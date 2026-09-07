package com.fedesack.raybanmetadat

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.CAMERA
import android.Manifest.permission.INTERNET
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            DatTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                DatRoot(
                    state = state,
                    onRegister = { viewModel.register(this) },
                    onMock = viewModel::useMock,
                    onOpenLive = viewModel::openLive,
                    onBack = viewModel::backToConnect,
                    onStart = { viewModel.onStartClicked { wearableCamera.launch(Permission.CAMERA) } },
                    onStop = viewModel::stopStream,
                    onSurface = viewModel::attachPreview,
                    onSurfaceGone = viewModel::detachPreview,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        androidPermissions.launch(arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, INTERNET, CAMERA))
    }
}
