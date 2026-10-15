package com.example.saktonato

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class Screen { PROFILE, CUTOUT, VERDICT, CAMERA }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileStore.init(this)

        setContent {
            MaterialTheme {
                Surface {
                    var screen by remember {
                        mutableStateOf(if (ProfileStore.current.isComplete) Screen.CUTOUT else Screen.PROFILE)
                    }
                    var profileReturn by remember { mutableStateOf(Screen.CUTOUT) }

                    when (screen) {
                        Screen.PROFILE -> {
                            val canCancel = ProfileStore.current.isComplete
                            if (canCancel) BackHandler { screen = profileReturn }
                            ProfileScreen(
                                onSaved = { screen = profileReturn },
                                onCancel = if (canCancel) ({ screen = profileReturn }) else null
                            )
                        }
                        Screen.CUTOUT -> CutoutScreen(
                            onNext = { screen = Screen.VERDICT },
                            onEditProfile = { profileReturn = Screen.CUTOUT; screen = Screen.PROFILE }
                        )
                        Screen.VERDICT -> VerdictScreen(
                            onTryOn = { screen = Screen.CAMERA },
                            onBack = { screen = Screen.CUTOUT },
                            onEditProfile = { profileReturn = Screen.VERDICT; screen = Screen.PROFILE }
                        )
                        Screen.CAMERA -> CameraGate(onBack = { screen = Screen.VERDICT })
                    }
                }
            }
        }
    }
}

@Composable
fun CameraGate(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (granted) CameraScreen(onBack) else Text("Camera permission is needed to try on clothes.")
}