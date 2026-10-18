// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aqsama.fandroidterminal.designsystem.TerminalTheme
import io.github.aqsama.fandroidterminal.sessions.TerminalService

class MainActivity : ComponentActivity() {
    private var service by mutableStateOf<TerminalService?>(null)
    private var bound = false
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as TerminalService.LocalBinder).service
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
        override fun onBindingDied(name: ComponentName) { service = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TerminalTheme { TerminalApp(service) } }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        TerminalService.ensureStarted(this)
        bound = bindService(Intent(this, TerminalService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        service = null
        if (bound) { unbindService(connection); bound = false }
        super.onStop()
    }
}
