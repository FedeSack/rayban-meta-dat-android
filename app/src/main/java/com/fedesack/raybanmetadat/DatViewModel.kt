package com.fedesack.raybanmetadat

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoFrame
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DatViewModel(application: Application) : AndroidViewModel(application) {
    private val flagsStore = FeatureFlagsStore(application)
    private val captureStore = BoardCaptureStore(application)
    private val captureWriter = BoardCaptureWriter(application)
    private val analytics = StreamSessionAnalytics()
    private val intentQueue = IntentQueue()
    private val initialFlags = flagsStore.load()
    private val _state =
        MutableStateFlow(
            AppState(
                flags = initialFlags,
                captures = captureStore.load(),
                intentWebhookUrl = flagsStore.intentWebhookUrl(),
                gazeEndpoint = if (initialFlags.gazeBridge) GazeLan.endpoint(GazeLan.wifiIpv4()) else null,
            ),
        )
    val state: StateFlow<AppState> = _state.asStateFlow()
    private val loggedCompressedGazeSkip = AtomicBoolean(false)
    private val intentEgress =
        IntentEgress(
            queue = intentQueue,
            client = HttpUrlIntentWebhookClient(),
            isVoiceDevMode = { _state.value.flags.voiceDevMode },
            webhookUrl = { _state.value.intentWebhookUrl },
        )
    private val gaze =
        GazeBridge(
            server = GazeWsServer(),
            pipeline =
                GazeJpegPipeline(
                    encodeYuv = { yuv ->
                        runCatching { YuvJpeg.encodeYuv(yuv, GazeWs.JPEG_QUALITY) }.getOrNull()
                    },
                    onCompressedSkip = {
                        if (loggedCompressedGazeSkip.compareAndSet(false, true)) {
                            Log.i(
                                "RaybanDat/Gaze",
                                "skip compressed/HEVC frame for JPEG relay " +
                                    "(YUV path only; HEVC side-decode removed)",
                            )
                        }
                    },
                ),
        )

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
    private val lastFrameLock = Any()
    private var lastRawFrame: RawStreamFrame? = null

    private val sink =
        FrameSink(
            onPresented = { presented ->
                val now = SystemClock.elapsedRealtime()
                val reading = Latency.reading(presented.presentationTimeUs, now, presented.receivedElapsedMs)
                val count = analytics.onPresented(now, reading)
                _state.update {
                    it.copy(
                        latencyMs = reading.millis,
                        latencyMode = reading.mode,
                        hasFrame = true,
                        awaitingFirstFrame = false,
                        analytics = analytics.snapshot(),
                    )
                }
                if (count == 1L) {
                    logSnapshot("first_frame")
                } else if (_state.value.flags.verboseLogcat && count % 24L == 0L) {
                    logSnapshot("stats")
                }
            },
            onFirstFrame = { _state.update { it.copy(hasFrame = true, awaitingFirstFrame = false) } },
            onQueueDrop = {
                val now = SystemClock.elapsedRealtime()
                analytics.onQueueDrop(now)
                if (_state.value.flags.verboseLogcat) {
                    logAnalytics("drop", mapOf("atMs" to now))
                }
            },
            onDecodeError = { message ->
                analytics.onDecodeError(SystemClock.elapsedRealtime(), message)
                logAnalytics("decode_error", mapOf("msg" to message))
            },
            onDecoderFormat = { width, height ->
                analytics.onDecoderOutput(width, height, SystemClock.elapsedRealtime())
                if (_state.value.flags.verboseLogcat) {
                    logAnalytics("decoder_out", mapOf("w" to width, "h" to height))
                }
            },
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
        _state.update {
            it.copy(
                phase = Phase.LIVE,
                message = null,
                wizard = wizardForLive(it),
            )
        }
        ensureSession()
    }

    fun backToConnect() {
        val currentCamera = camera
        currentCamera?.stop()
        clearStream()
        session?.stop()
        clearSession()
        sink.detach()
        analytics.stop(SystemClock.elapsedRealtime())
        _state.update {
            it.copy(
                phase = Phase.CONNECT,
                session = DeviceSessionState.IDLE,
                stream = StreamState.STOPPED,
                latencyMs = null,
                latencyMode = null,
                hasFrame = false,
                awaitingFirstFrame = false,
                analytics = AnalyticsSnapshot(),
                wizard = null,
            )
        }
    }

    fun attachPreview(surface: Surface) {
        sink.attach(surface)
    }

    fun detachPreview() {
        sink.detach()
    }

    fun setFlag(
        flag: FeatureFlag,
        enabled: Boolean,
    ) {
        val next = flagsStore.set(flag, enabled)
        _state.update { it.copy(flags = next) }
        logAnalytics(
            "flag",
            mapOf(
                "name" to flag.key,
                "on" to enabled,
                "stub" to flag.stub,
            ),
        )
        if (flag == FeatureFlag.GAZE_BRIDGE) {
            syncGazeBridge()
            restartLiveStreamIfNeeded()
        }
        if (flag == FeatureFlag.MURDOKU_HQ_CAPTURE) {
            _state.update { current ->
                current.copy(
                    wizard =
                        if (enabled) {
                            wizardForLive(current.copy(flags = next))
                        } else {
                            null
                        },
                )
            }
            restartLiveStreamIfNeeded()
        }
    }

    fun enableMurdokuAndConnect(activity: Activity) {
        if (!_state.value.flags.murdokuHqCapture) {
            setFlag(FeatureFlag.MURDOKU_HQ_CAPTURE, true)
        }
        startMurdokuWizard()
        register(activity)
    }

    fun startMurdokuWizard() {
        _state.update { it.copy(wizard = it.wizard ?: MurdokuWizardMath.newSession()) }
    }

    fun captureBoard() {
        if (_state.value.capturing) return
        if (_state.value.wizard?.step == MurdokuWizardStep.GUIDE) return
        val active = stream
        if (active == null || _state.value.stream != StreamState.STREAMING) {
            _state.update { it.copy(message = "Start el stream para capturar") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(capturing = true, message = null) }
            val takenAtMs = System.currentTimeMillis()
            var saved: BoardCapture? = null
            var captureError: String? = null
            active.capturePhoto()
                .onSuccess { photo ->
                    saved =
                        runCatching { captureWriter.savePhoto(photo, takenAtMs) }
                            .onFailure { error -> captureError = error.message }
                            .getOrNull()
                }
                .onFailure { error, _ ->
                    captureError = error.description
                }
            if (saved == null) {
                saved = saveLastYuvFrame(takenAtMs)
            }
            val capture = saved
            if (capture != null) {
                val wizard = _state.value.wizard
                val tagged =
                    wizard?.captureKind?.let { kind -> capture.copy(kind = kind) } ?: capture
                val next = BoardCaptureMath.prepend(_state.value.captures, tagged)
                captureStore.save(next)
                val advanced =
                    if (wizard != null && wizard.captureKind != null) {
                        MurdokuWizardMath.acceptCapture(wizard, tagged, takenAtMs)
                    } else {
                        wizard
                    }
                _state.update {
                    it.copy(
                        captures = next,
                        capturing = false,
                        message = null,
                        wizard = advanced,
                    )
                }
                logAnalytics(
                    "murdoku_capture",
                    mapOf(
                        "source" to tagged.source.name,
                        "mime" to tagged.mime,
                        "kind" to tagged.kind?.json,
                        "session" to advanced?.sessionId,
                        "w" to tagged.width,
                        "h" to tagged.height,
                    ),
                )
                if (advanced != null && advanced.readyToHandoff && advanced.step == MurdokuWizardStep.GUIDE) {
                    enqueueMurdokuAnalysis(advanced)
                }
            } else {
                _state.update {
                    it.copy(
                        capturing = false,
                        message = captureError ?: "No se pudo capturar el tablero",
                    )
                }
            }
        }
    }

    fun setVideoQuality(quality: VideoQualityFlag) {
        if (quality == _state.value.flags.videoQuality) return
        val next = flagsStore.setVideoQuality(quality)
        _state.update { it.copy(flags = next) }
        logAnalytics("flag", mapOf("name" to FeatureFlagsCatalog.VIDEO_QUALITY_KEY, "value" to quality.key))
        restartLiveStreamIfNeeded()
    }

    fun setFrameRate(frameRate: FrameRateFlag) {
        if (frameRate == _state.value.flags.frameRate) return
        val next = flagsStore.setFrameRate(frameRate)
        _state.update { it.copy(flags = next) }
        logAnalytics("flag", mapOf("name" to FeatureFlagsCatalog.FRAME_RATE_KEY, "value" to frameRate.fps))
        restartLiveStreamIfNeeded()
    }

    fun setIntentWebhookUrl(url: String) {
        val stored = flagsStore.setIntentWebhookUrl(url)
        _state.update { it.copy(intentWebhookUrl = stored) }
    }

    fun enqueueMurdokuAnalysis(wizard: MurdokuWizardState? = _state.value.wizard) {
        val session = wizard ?: return
        val utterance = MurdokuHandoffJson.payload(session) ?: return
        val intent =
            VoiceIntent.create(
                utterance = utterance,
                source = IntentSource.DAT,
                deviceId = deviceId(),
                appVersion = appVersion(),
                voiceDevMode = _state.value.flags.voiceDevMode,
            )
        viewModelScope.launch(Dispatchers.IO) {
            val result = intentEgress.submit(intent)
            val parsed =
                result.responseBody
                    ?.let { MurdokuHandoffJson.parseAnalysis(it) }
                    ?.takeIf { it.sessionId == session.sessionId }
            _state.update { current ->
                val currentWizard = current.wizard
                val nextWizard =
                    when {
                        currentWizard == null || currentWizard.sessionId != session.sessionId -> currentWizard
                        parsed != null ->
                            MurdokuWizardMath.applyAnalysis(currentWizard, parsed)
                                .copy(enqueueStatus = result.statusLine)
                        else -> currentWizard.copy(enqueueStatus = result.statusLine)
                    }
                current.copy(
                    queuedIntentCount = intentQueue.size,
                    lastIntentStatus = result.statusLine,
                    wizard = nextWizard,
                )
            }
            logAnalytics(
                "murdoku_handoff",
                mapOf(
                    "session" to session.sessionId,
                    "intent" to intent.id,
                    "posted" to result.posted,
                    "reason" to result.reason,
                    "queued" to intentQueue.size,
                    "moves" to (parsed?.moves?.size ?: 0),
                ),
            )
        }
    }

    fun enqueueChatStub(utterance: String) {
        val text = utterance.trim().ifEmpty { DEFAULT_CHAT_STUB }
        val intent =
            VoiceIntent.create(
                utterance = text,
                source = IntentSource.CHAT,
                deviceId = deviceId(),
                appVersion = appVersion(),
                voiceDevMode = _state.value.flags.voiceDevMode,
            )
        viewModelScope.launch(Dispatchers.IO) {
            val result = intentEgress.submit(intent)
            _state.update {
                it.copy(
                    queuedIntentCount = intentQueue.size,
                    lastIntentStatus = result.statusLine,
                )
            }
            logAnalytics(
                "intent",
                mapOf(
                    "id" to intent.id,
                    "source" to intent.source.json,
                    "posted" to result.posted,
                    "reason" to result.reason,
                    "queued" to intentQueue.size,
                ),
            )
        }
    }

    fun onWearableCameraPermission(status: PermissionStatus) {
        if (status == PermissionStatus.Granted || _state.value.source == DeviceSource.MOCK) {
            startStream()
        } else {
            _state.update {
                it.copy(
                    message = "Cámara de lentes denegada",
                    awaitingFirstFrame = false,
                )
            }
        }
    }

    fun onStartClicked(requestWearableCamera: () -> Unit) {
        viewModelScope.launch {
            if (_state.value.session != DeviceSessionState.STARTED) {
                _state.update {
                    it.copy(
                        message = "La sesión todavía no está STARTED",
                        awaitingFirstFrame = false,
                    )
                }
                return@launch
            }
            beginAwaitingFirstFrame()
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
                        _state.update {
                            it.copy(
                                message = error.description,
                                awaitingFirstFrame = false,
                            )
                        }
                    },
                )
        }
    }

    fun stopStream() {
        val current = camera ?: return
        _state.update { it.copy(stream = StreamState.STOPPING, awaitingFirstFrame = false) }
        current.stop()
    }

    private fun beginAwaitingFirstFrame() {
        val now = SystemClock.elapsedRealtime()
        val config = _state.value.flags.streamConfig()
        analytics.start(
            atElapsedMs = now,
            configuredQuality = config.qualityName,
            configuredFps = config.fps,
            compressVideo = config.compressVideo,
        )
        analytics.onSessionState(_state.value.session.name, now)
        analytics.onStreamState(_state.value.stream.name, now)
        _state.update {
            it.copy(
                awaitingFirstFrame = true,
                message = null,
                hasFrame = false,
                latencyMs = null,
                latencyMode = null,
                analytics = analytics.snapshot(),
            )
        }
        logAnalytics(
            "start",
            mapOf(
                "session" to _state.value.session.name,
                "stream" to _state.value.stream.name,
                "cfgQuality" to config.qualityName,
                "cfgFps" to config.fps,
                "compress" to config.compressVideo,
                "preferSharpness" to config.preferSharpness,
                "murdokuHq" to config.murdokuHq,
            ),
        )
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
                            val now = SystemClock.elapsedRealtime()
                            analytics.onSessionState(value.name, now)
                            _state.update {
                                it.copy(
                                    session = value,
                                    analytics = analytics.snapshot(),
                                )
                            }
                            logAnalytics("session", mapOf("state" to value.name))
                            if (value == DeviceSessionState.STOPPED) {
                                clearSession()
                            }
                        }
                    }
                sessionErrorJob =
                    viewModelScope.launch {
                        created.errors.collect { error ->
                            _state.update {
                                it.copy(
                                    message = error.description,
                                    awaitingFirstFrame = false,
                                )
                            }
                        }
                    }
                created.start()
            }
            .onFailure { error, _ ->
                _state.update { it.copy(message = error.description) }
            }
    }

    private fun restartLiveStreamIfNeeded() {
        if (!isLiveStreamActive()) return
        releaseStream(reason = "reconfigure")
        if (_state.value.session != DeviceSessionState.STARTED) {
            _state.update {
                it.copy(
                    stream = StreamState.STOPPED,
                    message = "Stop and Start to apply",
                    awaitingFirstFrame = false,
                    hasFrame = false,
                    latencyMs = null,
                    latencyMode = null,
                    analytics = analytics.snapshot(),
                )
            }
            return
        }
        beginAwaitingFirstFrame()
        startStream()
    }

    private fun isLiveStreamActive(): Boolean {
        val streamState = _state.value.stream
        return camera != null ||
            stream != null ||
            streamState == StreamState.STREAMING ||
            streamState == StreamState.STARTING ||
            streamState == StreamState.STOPPING
    }

    private fun configuredVideoQuality(): VideoQuality =
        when (_state.value.flags.streamConfig().quality) {
            VideoQualityFlag.HIGH -> VideoQuality.HIGH
            VideoQualityFlag.MEDIUM -> VideoQuality.MEDIUM
            VideoQualityFlag.LOW -> VideoQuality.LOW
        }

    private fun wizardForLive(state: AppState): MurdokuWizardState? {
        if (!state.flags.murdokuHqCapture) return null
        return state.wizard ?: MurdokuWizardMath.newSession()
    }

    private fun startStream() {
        val current = session ?: return
        if (stream != null) return
        val config = _state.value.flags.streamConfig()
        current
            .addCamera(
                StreamConfiguration(
                    videoQuality = configuredVideoQuality(),
                    frameRate = config.fps,
                    compressVideo = config.compressVideo,
                ),
            )
            .onSuccess { added ->
                camera = added
                val addedStream = added.stream
                stream = addedStream
                listen(addedStream)
                addedStream.start().onFailure { error, _ ->
                    _state.update {
                        it.copy(
                            message = error.description,
                            awaitingFirstFrame = false,
                        )
                    }
                    clearStream()
                }
            }
            .onFailure { error, _ ->
                _state.update {
                    it.copy(
                        message = error.description,
                        awaitingFirstFrame = false,
                    )
                }
            }
    }

    private fun listen(active: Stream) {
        var seenActive = false
        streamJob =
            viewModelScope.launch {
                active.state.collect { value ->
                    val now = SystemClock.elapsedRealtime()
                    analytics.onStreamState(value.name, now)
                    _state.update {
                        it.copy(
                            stream = value,
                            analytics = analytics.snapshot(),
                        )
                    }
                    logAnalytics("stream", mapOf("state" to value.name))
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
                    _state.update {
                        it.copy(
                            message = error.description,
                            awaitingFirstFrame = false,
                        )
                    }
                }
            }
        syncGazeBridge()
        framesJob =
            viewModelScope.launch(frames) {
                active.videoStream.collect { frame ->
                    val received = SystemClock.elapsedRealtime()
                    val path = if (frame.isCompressed) DecodePath.HEVC else DecodePath.YUV
                    val arrival = analytics.onFrameArrived(received, frame.width, frame.height, path)
                    if (arrival.first) {
                        logAnalytics(
                            "first_arrival",
                            mapOf(
                                "ttaMs" to analytics.snapshot().timeToFirstArrivalMs,
                                "w" to frame.width,
                                "h" to frame.height,
                                "path" to path.name,
                            ),
                        )
                    }
                    if (arrival.resolutionChanged) {
                        logAnalytics(
                            "resolution",
                            mapOf(
                                "w" to frame.width,
                                "h" to frame.height,
                                "path" to path.name,
                            ),
                        )
                    }
                    if (!_state.value.hasFrame) {
                        _state.update { it.copy(analytics = analytics.snapshot()) }
                    }
                    rememberFrame(frame)
                    offerGaze(frame)
                    sink.render(frame, received)
                }
            }
    }

    private fun clearStream() {
        releaseStream(reason = "stop")
        _state.update {
            it.copy(
                stream = StreamState.STOPPED,
                latencyMs = null,
                latencyMode = null,
                hasFrame = false,
                awaitingFirstFrame = false,
                analytics = analytics.snapshot(),
            )
        }
    }

    private fun rememberFrame(frame: VideoFrame) {
        if (!_state.value.flags.murdokuHqCapture) return
        if (frame.isCodecConfig) return
        synchronized(lastFrameLock) {
            lastRawFrame =
                RawStreamFrame(
                    bytes = YuvJpeg.copyBuffer(frame.buffer),
                    width = frame.width,
                    height = frame.height,
                    compressed = frame.isCompressed,
                )
        }
    }

    private fun saveLastYuvFrame(takenAtMs: Long): BoardCapture? {
        val raw =
            synchronized(lastFrameLock) { lastRawFrame }
                ?: return null
        if (raw.compressed) return null
        return runCatching {
            captureWriter.saveYuvFrame(raw.bytes, raw.width, raw.height, takenAtMs)
        }.getOrNull()
    }

    private fun releaseStream(reason: String) {
        framesJob?.cancel()
        streamJob?.cancel()
        streamErrorJob?.cancel()
        framesJob = null
        streamJob = null
        streamErrorJob = null
        synchronized(lastFrameLock) { lastRawFrame = null }
        loggedCompressedGazeSkip.set(false)
        gaze.sync(flagOn = false, streamLive = false)
        publishGazeState()
        runCatching { camera?.stop() }
        camera?.close()
        camera = null
        stream = null
        val summary = analytics.stop(SystemClock.elapsedRealtime())
        if (summary != null) {
            logAnalytics("stop", summary.toFields() + mapOf("reason" to reason))
        }
    }

    private fun clearSession() {
        sessionJob?.cancel()
        sessionErrorJob?.cancel()
        sessionJob = null
        sessionErrorJob = null
        session = null
    }

    private fun logSnapshot(event: String) {
        val snap = analytics.snapshot()
        logAnalytics(
            event,
            mapOf(
                "latencyMs" to snap.latencyMs,
                "mode" to snap.latencyMode?.name,
                "fps" to snap.estimatedFps,
                "gapMs" to snap.interArrivalMs,
                "ttffMs" to snap.timeToFirstFrameMs,
                "arrived" to snap.framesArrived,
                "presented" to snap.framesPresented,
                "w" to snap.width,
                "h" to snap.height,
                "path" to snap.decodePath?.name,
                "drops" to snap.queueDrops,
            ),
        )
    }

    private fun logAnalytics(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
    ) {
        Log.i(AnalyticsLog.TAG, AnalyticsLog.line(event, fields))
    }

    private fun deviceId(): String =
        runCatching {
            Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                Settings.Secure.ANDROID_ID,
            )
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "android"

    private fun appVersion(): String =
        runCatching {
            val app = getApplication<Application>()
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.1.0"

    override fun onCleared() {
        camera?.stop()
        clearStream()
        session?.stop()
        clearSession()
        sink.detach()
        gaze.stop()
        mockKit?.disable()
        super.onCleared()
    }

    private fun offerGaze(frame: VideoFrame) {
        if (!_state.value.flags.gazeBridge) return
        try {
            gaze.submit(
                width = frame.width,
                height = frame.height,
                compressed = frame.isCompressed,
                codecConfig = frame.isCodecConfig,
                presentationTimeUs = frame.presentationTimeUs,
                bytes = { YuvJpeg.copyBuffer(frame.buffer) },
            )
        } catch (t: Throwable) {
            Log.w("RaybanDat/Gaze", "submit skipped: ${t.message}")
        }
    }

    private fun syncGazeBridge() {
        val flags = _state.value.flags
        gaze.sync(flagOn = flags.gazeBridge, streamLive = isLiveStreamActive() && stream != null)
        publishGazeState()
        if (flags.gazeBridge) {
            logAnalytics(
                "gaze",
                mapOf(
                    "on" to true,
                    "live" to (stream != null),
                    "listening" to gaze.listening,
                    "url" to gaze.displayUrl(true),
                ),
            )
        }
    }

    private fun publishGazeState() {
        val flagOn = _state.value.flags.gazeBridge
        _state.update {
            it.copy(
                gazeEndpoint = gaze.displayUrl(flagOn),
                gazeListening = gaze.listening,
            )
        }
    }

    companion object {
        const val DEFAULT_CHAT_STUB = "chat stub"
    }
}

private data class RawStreamFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val compressed: Boolean,
)
