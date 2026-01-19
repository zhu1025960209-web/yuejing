package com.example.yuejing

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    companion object {
        private const val REQUEST_CODE_PERIOD = 1
        private const val REQUEST_CODE_OVULATION = 2
        private const val REQUEST_CODE_FERTILE = 3
        private const val REMINDER_HOUR = 9 // 上午9点提醒
        private const val REMINDER_MINUTE = 0
        private const val TAG = "ReminderScheduler"
    }
    
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    
    // 检查是否有设置精确闹钟的权限
    private fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // 低于Android 12不需要权限
        }
    }
    
    fun scheduleReminders(
        periodStartDate: LocalDate?, 
        ovulationDate: LocalDate?, 
        fertileStartDate: LocalDate?
    ) {
        val (periodReminder, ovulationReminder, fertileReminder) = loadReminderSettings(context)
        
        if (periodReminder && periodStartDate != null) {
            schedulePeriodReminder(periodStartDate)
        }
        
        if (ovulationReminder && ovulationDate != null) {
            scheduleOvulationReminder(ovulationDate)
        }
        
        if (fertileReminder && fertileStartDate != null) {
            scheduleFertileReminder(fertileStartDate)
        }
    }
    
    private fun schedulePeriodReminder(periodStartDate: LocalDate) {
        // 检查权限
        if (!hasExactAlarmPermission()) {
            Log.w(TAG, "No permission to schedule exact alarms, skipping period reminder")
            return
        }
        
        // 经期提醒：在经期开始前1天
        val reminderDate = periodStartDate.minusDays(1)
        val reminderTime = LocalDateTime.of(reminderDate, LocalTime.of(REMINDER_HOUR, REMINDER_MINUTE))
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_REMINDER_TYPE, NotificationReceiver.REMINDER_PERIOD)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PERIOD,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val reminderMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val currentMillis = System.currentTimeMillis()
        
        if (reminderMillis > currentMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderMillis,
                    pendingIntent
                )
            }
        }
    }
    
    private fun scheduleOvulationReminder(ovulationDate: LocalDate) {
        // 检查权限
        if (!hasExactAlarmPermission()) {
            Log.w(TAG, "No permission to schedule exact alarms, skipping ovulation reminder")
            return
        }
        
        // 排卵期提醒：在排卵日当天
        val reminderTime = LocalDateTime.of(ovulationDate, LocalTime.of(REMINDER_HOUR, REMINDER_MINUTE))
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_REMINDER_TYPE, NotificationReceiver.REMINDER_OVULATION)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_OVULATION,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val reminderMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val currentMillis = System.currentTimeMillis()
        
        if (reminderMillis > currentMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderMillis,
                    pendingIntent
                )
            }
        }
    }
    
    private fun scheduleFertileReminder(fertileStartDate: LocalDate) {
        // 检查权限
        if (!hasExactAlarmPermission()) {
            Log.w(TAG, "No permission to schedule exact alarms, skipping fertile reminder")
            return
        }
        
        // 易孕期提醒：在易孕期开始前1天
        val reminderDate = fertileStartDate.minusDays(1)
        val reminderTime = LocalDateTime.of(reminderDate, LocalTime.of(REMINDER_HOUR, REMINDER_MINUTE))
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra(NotificationReceiver.EXTRA_REMINDER_TYPE, NotificationReceiver.REMINDER_FERTILE)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_FERTILE,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        val reminderMillis = reminderTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val currentMillis = System.currentTimeMillis()
        
        if (reminderMillis > currentMillis) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderMillis,
                    pendingIntent
                )
            }
        }
    }
    
    fun cancelAllReminders() {
        cancelReminder(REQUEST_CODE_PERIOD)
        cancelReminder(REQUEST_CODE_OVULATION)
        cancelReminder(REQUEST_CODE_FERTILE)
    }
    
    private fun cancelReminder(requestCode: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}