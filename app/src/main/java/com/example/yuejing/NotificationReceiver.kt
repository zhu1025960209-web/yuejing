package com.example.yuejing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationReceiver : BroadcastReceiver() {
    companion object {
        private const val CHANNEL_ID = "yuejing_reminders"
        private const val CHANNEL_NAME = "月经提醒"
        private const val CHANNEL_DESCRIPTION = "经期、排卵期和易孕期提醒"
        
        const val EXTRA_REMINDER_TYPE = "reminder_type"
        const val REMINDER_PERIOD = "period"
        const val REMINDER_OVULATION = "ovulation"
        const val REMINDER_FERTILE = "fertile"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val reminderType = intent.getStringExtra(EXTRA_REMINDER_TYPE)
        
        createNotificationChannel(context)
        
        when (reminderType) {
            REMINDER_PERIOD -> showPeriodNotification(context)
            REMINDER_OVULATION -> showOvulationNotification(context)
            REMINDER_FERTILE -> showFertileNotification(context)
        }
    }
    
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showPeriodNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.heart_icon)
            .setContentTitle("经期提醒")
            .setContentText("您的经期预计明天开始，请做好准备~")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(1, notification)
        }
    }
    
    private fun showOvulationNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.heart_icon)
            .setContentTitle("排卵期提醒")
            .setContentText("今天是您的排卵期，请注意避孕或备孕~")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(2, notification)
        }
    }
    
    private fun showFertileNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.heart_icon)
            .setContentTitle("易孕期提醒")
            .setContentText("您的易孕期即将开始，请做好避孕或备孕准备~")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        with(NotificationManagerCompat.from(context)) {
            notify(3, notification)
        }
    }
}