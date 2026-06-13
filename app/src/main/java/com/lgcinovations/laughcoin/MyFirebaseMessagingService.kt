package com.lgcinovations.laughcoin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            val title = decodeUnicode(it.title ?: "LaughCoin")
            val body  = decodeUnicode(it.body  ?: "")
            sendNotification(title, body)
        }
        // Also handle data payloads
        if (remoteMessage.data.isNotEmpty()) {
            val title = decodeUnicode(remoteMessage.data["title"] ?: return)
            val body  = decodeUnicode(remoteMessage.data["body"]  ?: "")
            if (remoteMessage.notification == null) sendNotification(title, body)
        }
    }

    /**
     * Converts raw Unicode escape sequences like U0001F525 or \uD83D\uDD25 to
     * actual emoji characters so they display correctly in notifications.
     */
    private fun decodeUnicode(text: String): String {
        // Replace U0001Fxxx style (LaughCoin Cloud Function bug)
        val pattern = Regex("U([0-9A-Fa-f]{4,8})")
        return pattern.replace(text) { match ->
            try {
                val codePoint = match.groupValues[1].toInt(16)
                String(Character.toChars(codePoint))
            } catch (e: Exception) {
                match.value // leave unchanged if invalid
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE)

        val channelId = "laughcoin_notifications"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.laugh_logo)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                "LaughCoin Rewards & News",
                NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        // Save token to Firestore to target specific user later
    }
}
