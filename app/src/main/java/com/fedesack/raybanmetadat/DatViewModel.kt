package com.fedesack.raybanmetadat

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DatViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var stream: Stream? = null
    private var mockKit: MockDeviceKitInterface? = null
    private var sessionJob: Job? = null
    private var sessionErrorJob: Job? = null
    private var streamJob: Job? = null
    private var streamErrorJob: Job? = null
    private var framesJob: Job? = null
    private var monitoring = false
    private val frames = Dispatchers.Default.limitedParallelism(1)

    private val sink =
        FrameSink(
            onPresented = { ptsUs, receivedMs ->
                val now = SystemClock.elapsedRealtime()
                val reading = Latency.reading(ptsUs, now, receivedMs)
                _state.update {
                    it.copy(
                        latencyMs = reading.millis,
                        latencyMode = reading.mode,
                        hasFrame = true,
                    )
                }
            },
            onFirstFrame = { _state.update { it.copy(hasFrame = true) } },
        )

    fun onAndroidPermissions(granted: Boolean) {
        _state.update {
            it.copy(
                androidReady = granted,
                message = if (granted) it.message else "Faltan permisos de Bluetooth",
            )
        }
        if (!granted) return
        Wearables.initialize(getApplication()).onFailure { error, _ ->
            _state.update { it.copy(message = error.description) }
        }
        startMonitoring()
    }

    fun register(activity: Activity) {
        _state.update { it.copy(source = DeviceSource.META_AI, message = null) }
        if (_state.value.canOpenLive) {
            openLive()
            return
        }
        Wearables.startRegistration(activity)
    }

    fun useMock() {
        val kit = MockDeviceKit.getInstance(getApplication())
        mockKit = kit
        kit.enable()
        viewModelScope.launch {
            kit.pairGlasses(GlassesModel.RAYBAN_META)
                .fold(
                    onSuccess = { glasses ->
                        glasses.powerOn()
                        glasses.unfold()
                        glasses.don()
                        runCatching { glasses.services.camera.setCameraFeed(CameraFacing.BACK) }
                        _state.update {
                            it.copy(
                                source = DeviceSource.MOCK,
                                registered = true,
                                registrationLabel = "mock",
                                message = null,
                            )
                        }
                        openLive()
                    },
                    onFailure = { error, _ ->
                        _state.update { it.copy(message = error.description) }
                    },
                )
        }
    }

    fun openLive() {
        _state.update { it.copy(phase = Phase.LIVE, message = null) }
        ensureSession()
    }

    fun backToConnect() {
        val currentCamera = camera
        currentCamera?.stop()
        clearStream()
        session?.stop()
        clearSession()
        sink.detach()
        _state.update {
            it.copy(
                phase = Phase.CONNECT,
                session = DeviceSessionState.IDLE,
                stream = StreamState.STOPPED,
                latencyMs = null,
                latencyMode = null,
                hasFrame = false,
            )
        }
    }

    fun attachPreview(surface: Surface) {
        sink.attach(surface)
    }

    fun detachPreview() {
        sink.detach()
    }

    fun onWearableCameraPermission(status: PermissionStatus) {
        if (status == PermissionStatus.Granted || _state.value.source == DeviceSource.MOCK) {
            startStream()
        } else {
            _state.update { it.copy(message = "Cámara de lentes denegada") }
        }
    }

    fun onStartClicked(requestWearableCamera: () -> Unit) {
        viewModelScope.launch {
            if (_state.value.session != DeviceSessionState.STARTED) {
                _state.update { it.copy(message = "La sesión todavía no está STARTED") }
                return@launch
            }
            if (_state.value.source == DeviceSource.MOCK) {
                startStream()
                return@launch
            }
            Wearables.checkPermissionStatus(Permission.CAMERA)
                .fold(
                    onSuccess = { status ->
                        if (status == PermissionStatus.Granted) {
                            startStream()
                        } else {
                            requestWearableCamera()
                        }
                    },
                    onFailure = { error, _ ->
                        _state.update { it.copy(message = error.description) }
                    },
                )
        }
    }

    fun stopStream() {
        val current = camera ?: return
        _state.update { it.copy(stream = StreamState.STOPPING) }
        current.stop()
    }

    private fun startMonitoring() {
        if (monitoring) return
        monitoring = true
        viewModelScope.launch {
            Wearables.registrationState.collect { value ->
                _state.update {
                    it.copy(
                        registered =
                            value == RegistrationState.REGISTERED ||
                                value == RegistrationState.UNREGISTERING,
                        registrationLabel =
                            when (value) {
                                RegistrationState.REGISTERED -> "registrado"
                                RegistrationState.REGISTERING -> "registrando"
                                RegistrationState.UNREGISTERING -> "desregistrando"
                                RegistrationState.AVAILABLE -> "disponible"
                                RegistrationState.UNAVAILABLE -> "no disponible"
                            },
                    )
                }
            }
        }
    }

    private fun ensureSession() {
        if (session != null) return
        Wearables.createSession(AutoDeviceSelector())
            .onSuccess { created ->
                session = created
                sessionJob =
                    viewModelScope.launch {
                        created.state.collect { value ->
                            _state.update { it.copy(session = value) }
                            if (value == DeviceSessionState.STOPPED) {
                                clearSession()
                            }
                        }
                    }
                sessionErrorJob =
                    viewModelScope.launch {
                        created.errors.collect { error ->
                            _state.update { it.copy(message = error.description) }
                        }
                    }
                created.start()
            }
            .onFailure { error, _ ->
                _state.update { it.copy(message = error.description) }
            }
    }

    private fun startStream() {
        val current = session ?: return
        if (stream != null) return
        current
            .addCamera(
                StreamConfiguration(
                    videoQuality = VideoQuality.HIGH,
                    frameRate = 24,
                    compressVideo = true,
                ),
            )
            .onSuccess { added ->
                camera = added
                val addedStream = added.stream
                stream = addedStream
                listen(addedStream)
                addedStream.start().onFailure { error, _ ->
                    _state.update { it.copy(message = error.description) }
                    clearStream()
                }
            }
            .onFailure { error, _ ->
                _state.update { it.copy(message = error.description) }
            }
    }

    private fun listen(active: Stream) {
        var seenActive = false
        streamJob =
            viewModelScope.launch {
                active.state.collect { value ->
                    _state.update { it.copy(stream = value) }
                    val terminal = value == StreamState.STOPPED || value == StreamState.CLOSED
                    if (!terminal) {
                        seenActive = true
                    } else if (seenActive) {
                        clearStream()
                    }
                }
            }
        streamErrorJob =
            viewModelScope.launch {
                active.errorStream.collect { error ->
                    _state.update { it.copy(message = error.description) }
                }
            }
        framesJob =
            viewModelScope.launch(frames) {
                active.videoStream.collect { frame ->
                    sink.render(frame, SystemClock.elapsedRealtime())
                }
            }
    }

    private fun clearStream() {
        framesJob?.cancel()
        streamJob?.cancel()
        streamErrorJob?.cancel()
        framesJob = null
        streamJob = null
        streamErrorJob = null
        camera?.close()
        camera = null
        stream = null
        _state.update {
            it.copy(
                stream = StreamState.STOPPED,
                latencyMs = null,
                latencyMode = null,
                hasFrame = false,
            )
        }
    }

    private fun clearSession() {
        sessionJob?.cancel()
        sessionErrorJob?.cancel()
        sessionJob = null
        sessionErrorJob = null
        session = null
    }

    override fun onCleared() {
        camera?.stop()
        clearStream()
        session?.stop()
        clearSession()
        sink.detach()
        mockKit?.disable()
        super.onCleared()
    }
}
