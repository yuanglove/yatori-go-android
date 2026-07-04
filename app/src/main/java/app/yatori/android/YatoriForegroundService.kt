package app.yatori.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject

class YatoriForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> EngineRegistry.engine?.stopAllTasks()
        }
        val running = runningCount()
        if (running <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(running))
        return START_STICKY
    }

    private fun runningCount(): Int {
        val raw = EngineRegistry.engine?.getTaskStatuses() ?: return 0
        val arr = JSONObject(raw).optJSONArray("data") ?: return 0
        var count = 0
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optString("state") == "running") count++
        }
        return count
    }

    private fun notification(running: Int): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Yatori tasks", NotificationManager.IMPORTANCE_LOW))
        }
        val stopIntent = Intent(this, YatoriForegroundService::class.java).setAction(ACTION_STOP_ALL)
        val stopPending = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openPending = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Yatori 正在运行")
            .setContentText("运行中任务：$running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止全部", stopPending)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_REFRESH = "app.yatori.android.REFRESH"
        const val ACTION_STOP_ALL = "app.yatori.android.STOP_ALL"
        private const val CHANNEL_ID = "yatori_tasks"
        private const val NOTIFICATION_ID = 42
    }
}
