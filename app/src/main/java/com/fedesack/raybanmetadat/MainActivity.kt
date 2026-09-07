package com.fedesack.raybanmetadat

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fedesack.raybanmetadat.ui.DatRoot
import com.fedesack.raybanmetadat.ui.DatTheme
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

class MainActivity : ComponentActivity() {
    private val viewModel: DatViewModel by viewModels()

    private val androidPermissions =
        registerForActivityResult(RequestMultiplePermissions()) { result ->
            viewModel.onAndroidPermissions(result.values.all { it })
        }

    private val wearableCamera =
        registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
            viewModel.onWearableCameraPermission(result.getOrDefault(PermissionStatus.Denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            DatTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                DatRoot(
                    state = state,
                    onRegister = { viewModel.register(this) },
                    onMock = viewModel::useMock,
                    onMurdoku = { viewModel.enableMurdokuAndConnect(this) },
                    onBack = viewModel::backToConnect,
                    onStart = { viewModel.onStartClicked { wearableCamera.launch(Permission.CAMERA) } },
                    onStop = viewModel::stopStream,
                    onCaptureBoard = viewModel::captureBoard,
                    onShareCapture = ::shareCapture,
                    onEnqueueMurdoku = { viewModel.enqueueMurdokuAnalysis() },
                    onSurface = viewModel::attachPreview,
                    onSurfaceGone = viewModel::detachPreview,
                    onFlagChange = viewModel::setFlag,
                    onVideoQualityChange = viewModel::setVideoQuality,
                    onFrameRateChange = viewModel::setFrameRate,
                    onIntentWebhookUrlChange = viewModel::setIntentWebhookUrl,
                    onEnqueueChat = viewModel::enqueueChatStub,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        androidPermissions.launch(arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, INTERNET, CAMERA))
    }

    private fun shareCapture(capture: BoardCapture) {
        val uri = Uri.parse(capture.uri)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = capture.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri(capture.fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startActivity(Intent.createChooser(intent, "Exportar tablero"))
    }
}
