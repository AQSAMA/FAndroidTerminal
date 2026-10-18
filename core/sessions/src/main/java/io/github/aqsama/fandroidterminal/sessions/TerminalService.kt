// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground lifetime is independent of Activity instances, not of Android process death. */
class TerminalService : Service() {
    inner class LocalBinder : Binder() { val service: TerminalService get() = this@TerminalService }
    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var sessions: SessionManager
        private set
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        sessions = SessionManager(applicationContext)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Terminal sessions", NotificationManager.IMPORTANCE_LOW),
        )
        scope.launch {
            sessions.sessions.collect { active ->
                if (foreground && active.isEmpty()) {
                    ServiceCompat.stopForeground(this@TerminalService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    foreground = false
                    stopSelf()
                } else if (foreground) showNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sessions.close()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return START_NOT_STICKY
        }
        // Called before process creation so Android's foreground deadline is always met.
        foreground = true
        showNotification()
        if (intent?.action == ACTION_NEW || sessions.sessions.value.isEmpty()) {
            if (sessions.sessions.value.size < 12) sessions.create()
        }
        showNotification()
        return START_NOT_STICKY
    }

    private fun showNotification() {
        val open = packageManager.getLaunchIntentForPackage(packageName) ?: return
        val contentIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, TerminalService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("FAndroidTerminal")
            .setContentText("${sessions.sessions.value.size} open terminal sessions")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close all sessions", stopIntent)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        sessions.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "terminal_sessions"
        private const val NOTIFICATION = 100
        private const val ACTION_NEW = "terminal.NEW"
        private const val ACTION_STOP = "terminal.STOP"
        fun ensureStarted(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
        }
        fun newSession(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java).setAction(ACTION_NEW))
        }
    }
}
