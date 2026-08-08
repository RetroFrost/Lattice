package com.retrofrost.lattice.privacy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.retrofrost.lattice.model.LatticeSettings
import com.retrofrost.lattice.telegram.TelegramChatSummary
import com.retrofrost.lattice.telegram.TelegramMessageItem

class LatticeNotificationManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    fun showUnread(chat: TelegramChatSummary, settings: LatticeSettings) {
        if (!settings.notificationsEnabled || settings.dndEnabled) return

        val channelId = channelId(settings)
        ensureChannel(channelId, settings)

        val preview = ContentFilterEngine.evaluate(
            TelegramMessageItem(-1L, chat.id, chat.preview, false, 0, "preview"),
            settings
        ).displayText

        val showContent = settings.notificationContent && !(settings.dndEnabled && settings.dndHideNotificationContent)
        val title = if (showContent) chat.title else "Lattice"
        val body = if (showContent) preview.ifBlank { "New message" } else "New message"

        val notification = Notification.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        manager.notify(chat.id.hashCode(), notification)
    }

    private fun channelId(settings: LatticeSettings): String = buildString {
        append("lattice_messages_")
        append(if (settings.notificationSound) "sound" else "silent")
        append('_')
        append(if (settings.notificationVibration) "vibrate" else "steady")
    }

    private fun ensureChannel(id: String, settings: LatticeSettings) {
        if (manager.getNotificationChannel(id) != null) return
        val importance = if (settings.notificationSound || settings.notificationVibration) {
            NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_LOW
        }
        val channel = NotificationChannel(id, "Lattice messages", importance).apply {
            description = "Privacy-aware Telegram message notifications"
            enableVibration(settings.notificationVibration)
            if (!settings.notificationSound) setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }
}
