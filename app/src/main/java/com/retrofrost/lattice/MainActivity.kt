package com.retrofrost.lattice

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.retrofrost.lattice.ui.LatticeApp

class MainActivity : ComponentActivity() {
    private var deepLinkState: MutableState<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Maximum Privacy default: keep chat content out of screenshots and Recents previews.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

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
}
