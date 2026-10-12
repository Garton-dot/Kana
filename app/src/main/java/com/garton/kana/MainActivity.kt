package com.garton.kana

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.garton.kana.ui.App
import com.garton.kana.ui.KanaTheme
import com.garton.kana.ui.KanaViewModel
import com.garton.kana.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: KanaViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KanaTheme {
                // Ask for notifications right after the first finished session, when a streak exists to protect.
                val firstResults = vm.screen is Screen.Results && vm.progress.sessions == 1
                LaunchedEffect(firstResults) {
                    if (firstResults && Build.VERSION.SDK_INT >= 33 && vm.progress.remindersOn) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                App(vm)
            }
        }
    }
}
