package com.example.medihelp.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.medihelp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.medihelp.MainActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            val channelName = remoteMessage.data["channelName"]
            sendNotification(applicationContext, it.title ?: "MediHelp", it.body ?: "", channelName)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .update("fcmToken", token)
        }
    }
}

fun sendNotification(context: Context, title: String, message: String, channelName: String? = null) {
    val channelId = "medihelp_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "MediHelp Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    val intent = Intent(context, MainActivity::class.java)
    channelName?.let { intent.putExtra("channelName", it) }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
    with(NotificationManagerCompat.from(context)) {
        notify(System.currentTimeMillis().toInt(), builder.build())
    }
} 