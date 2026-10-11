package com.amanaque.trabajo

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

internal object ReminderScheduler {
    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1001

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarmIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DailyReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val canScheduleExactly = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canScheduleExactly) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, alarmIntent)
                return
            } catch (_: SecurityException) {
                // Exact-alarm access can be revoked between checking permission and scheduling.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, alarmIntent)
    }

    fun showNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorio diario",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT)
        }.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Mi almanaque")
            .setContentText("No olvides registrar tu día y revisar tus objetivos.")
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.showNotification(context)
        ReminderScheduler.scheduleNext(context)
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.scheduleNext(context)
    }
}
