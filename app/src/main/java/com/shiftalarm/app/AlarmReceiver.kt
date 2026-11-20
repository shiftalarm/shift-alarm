package com.shiftalarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ATTENDANCE_CHANNEL_ID = "shift_attendance_reminder_v1"
        const val ATTENDANCE_NOTIFICATION_ID = 2030
    }

    override fun onReceive(context: Context, intent: Intent) {
        val shiftCode = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: return
        val date = intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: return
        val isSnooze = intent.action == AlarmScheduler.ACTION_SNOOZE
        val reminderMinutes = intent.getIntExtra(AlarmScheduler.EXTRA_REMINDER, 0)
        if (!isSnooze && reminderMinutes == AlarmScheduler.ATTENDANCE_REMINDER_MINUTES) {
            if (AlarmPreferences(context).isAttendanceReminderEnabled()) showAttendanceReminder(context, shiftCode, date)
            return
        }
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = intent.action ?: AlarmScheduler.ACTION_ALARM
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
            putExtra(AlarmScheduler.EXTRA_REMINDER, 0)
            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME))
            putExtra(AlarmScheduler.EXTRA_ALARM_KIND, intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND) ?: AlarmScheduler.KIND_START)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showAttendanceReminder(context: Context, shiftCode: String, date: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ATTENDANCE_CHANNEL_ID, "Pengingat Absen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pengingat persiapan dinas 30 menit sebelum alarm mulai kerja"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        val message = AlarmPreferences(context).getReminderText().replace("{shift}", shiftCode)
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, ATTENDANCE_CHANNEL_ID)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT)
        }.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SHIFT ALARM")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOngoing(false)
            .setTimeoutAfter(60_000L)
            .build()
        try { manager.notify("$date-$shiftCode".hashCode(), notification) }
        catch (_: SecurityException) { }
    }
}
