package com.shiftalarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ATTENDANCE_NOTIFICATION_ID = 2030
    }

    override fun onReceive(context: Context, intent: Intent) {
        val shiftCode = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: return
        val date = intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: return
        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE)
            ?: AlarmScheduler.ALARM_TYPE_START
        val isSnooze = intent.action == AlarmScheduler.ACTION_SNOOZE
        val reminderMinutes = intent.getIntExtra(AlarmScheduler.EXTRA_REMINDER, 0)

        if (!isSnooze && alarmType == AlarmScheduler.ALARM_TYPE_START &&
            reminderMinutes == AlarmScheduler.ATTENDANCE_REMINDER_MINUTES) {
            startAttendanceReminder(context, shiftCode, date)
            return
        }

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = intent.action ?: AlarmScheduler.ACTION_ALARM
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
            putExtra(AlarmScheduler.EXTRA_REMINDER, 0)
            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME))
            putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAttendanceReminder(context: Context, shiftCode: String, date: String) {
        val serviceIntent = Intent(context, AttendanceReminderService::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
            else context.startService(serviceIntent)
        } catch (error: Exception) {
            error.printStackTrace()
            showAttendanceReminderFallback(context, shiftCode, date)
        }
    }

    private fun showAttendanceReminderFallback(context: Context, shiftCode: String, date: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prefs = AlarmPreferences(context)
        val reminderText = prefs.getAttendanceReminderText()
        val soundUri = prefs.getAttendanceRingtoneUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val channelId = "shift_attendance_fallback_v4_" + (soundUri?.toString()?.hashCode()?.toUInt()?.toString(16) ?: "default")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pengingat Absen", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pengingat persiapan dinas 30 menit sebelum alarm utama"
                setShowBadge(false)
                enableVibration(true)
                setSound(soundUri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
            manager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setPriority(Notification.PRIORITY_HIGH).setSound(soundUri)
        }.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Persiapan Dinas $shiftCode")
            .setContentText(reminderText)
            .setStyle(Notification.BigTextStyle().bigText(reminderText))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setOngoing(false)
            .build()

        try {
            manager.notify((date + "-" + shiftCode).hashCode(), notification)
        } catch (_: SecurityException) {
            // Izin notifikasi boleh dimatikan; alarm utama tetap dijadwalkan terpisah.
        }
    }
}
