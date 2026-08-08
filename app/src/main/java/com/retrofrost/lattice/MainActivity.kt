package com.retrofrost.lattice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.retrofrost.lattice.model.LatticeSettingsStore
import com.retrofrost.lattice.ui.LatticeApp

class MainActivity : ComponentActivity() {
    private var deepLinkState: MutableState<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = LatticeSettingsStore(applicationContext).load()
        if (settings.screenshotProtection) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            settings.notificationsEnabled &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }

        deepLinkState = mutableStateOf(intent?.dataString)
        setContent {
            LatticeApp(incomingLink = deepLinkState?.value)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkState?.value = intent.dataString
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 41
    }
}
