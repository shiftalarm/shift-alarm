package com.shiftalarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_ALARM = "com.shiftalarm.app.ACTION_ALARM"
        const val ACTION_SNOOZE = "com.shiftalarm.app.ACTION_SNOOZE"
        const val EXTRA_SHIFT_CODE = "shift_code"
        const val EXTRA_DATE = "shift_date"
        const val EXTRA_REMINDER = "reminder_minutes"
        const val EXTRA_ALARM_TIME = "alarm_time"
        const val EXTRA_ALARM_TYPE = "alarm_type"
        const val ALARM_TYPE_START = "START"
        const val ALARM_TYPE_END = "END"
        const val ATTENDANCE_REMINDER_MINUTES = 30
        val REMINDER_MINUTES = listOf(ATTENDANCE_REMINDER_MINUTES, 0)
        private val LEGACY_REMINDER_MINUTES = listOf(60, 30, 15, 0)
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    fun scheduleShift(date: LocalDate, shiftCode: String) {
        val shift = ShiftDefinitions.find(shiftCode) ?: throw IllegalArgumentException("Kode shift tidak dikenal")
        cancelShift(date, shift.code)

        val preferences = AlarmPreferences(appContext)
        val startTime = preferences.getStartAlarmTime(date, shift.startTime)
        val endTime = preferences.getEndAlarmTime(date, shift.endTime)
        val start = LocalDateTime.of(date, startTime)
        val now = LocalDateTime.now()
        val reminderTime = start.minusMinutes(ATTENDANCE_REMINDER_MINUTES.toLong())

        if (reminderTime.isAfter(now)) {
            scheduleAlarm(reminderTime, date, shift.code, ATTENDANCE_REMINDER_MINUTES, startTime.toString(), ALARM_TYPE_START)
        }
        if (start.isAfter(now)) {
            scheduleAlarm(start, date, shift.code, 0, startTime.toString(), ALARM_TYPE_START)
        }

        val endDate = date.plusDays(shift.endDayOffset.toLong())
        val end = LocalDateTime.of(endDate, endTime)
        if (end.isAfter(now)) {
            scheduleAlarm(end, date, shift.code, 0, endTime.toString(), ALARM_TYPE_END)
        }
    }

    private fun scheduleAlarm(
        dateTime: LocalDateTime,
        date: LocalDate,
        shiftCode: String,
        reminderMinutes: Int,
        alarmTime: String,
        alarmType: String
    ) {
        if (!canScheduleExactAlarms()) throw SecurityException("Izin alarm tepat belum diberikan")
        val triggerMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val requestCode = when {
            reminderMinutes > 0 -> "$date-$shiftCode-$reminderMinutes".hashCode()
            alarmType == ALARM_TYPE_END -> "$date-$shiftCode-END".hashCode()
            else -> "$date-$shiftCode-0".hashCode()
        }
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_SHIFT_CODE, shiftCode)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_REMINDER, reminderMinutes)
            putExtra(EXTRA_ALARM_TIME, alarmTime)
            putExtra(EXTRA_ALARM_TYPE, alarmType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    fun scheduleSnooze(shiftCode: String, date: LocalDate, alarmType: String, alarmTime: String) {
        if (!canScheduleExactAlarms()) throw SecurityException("Izin alarm tepat belum diberikan")
        val triggerMillis = System.currentTimeMillis() + 5 * 60 * 1000L
        val requestCode = snoozeRequestCode(shiftCode, date, alarmType)
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_SHIFT_CODE, shiftCode)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_REMINDER, 0)
            putExtra(EXTRA_ALARM_TIME, alarmTime)
            putExtra(EXTRA_ALARM_TYPE, alarmType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    fun cancelShift(date: LocalDate, shiftCode: String) {
        for (minutes in LEGACY_REMINDER_MINUTES) {
            val requestCode = "$date-$shiftCode-$minutes".hashCode()
            val intent = Intent(appContext, AlarmReceiver::class.java).apply { action = ACTION_ALARM }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
        }
        val endIntent = Intent(appContext, AlarmReceiver::class.java).apply { action = ACTION_ALARM }
        val endPending = PendingIntent.getBroadcast(
            appContext, "$date-$shiftCode-END".hashCode(), endIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        endPending?.let { alarmManager.cancel(it); it.cancel() }
        cancelSnooze(shiftCode, date, ALARM_TYPE_START)
        cancelSnooze(shiftCode, date, ALARM_TYPE_END)
    }

    fun cancelSnooze(shiftCode: String, date: LocalDate, alarmType: String) {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply { action = ACTION_SNOOZE }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext, snoozeRequestCode(shiftCode, date, alarmType), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
    }

    private fun snoozeRequestCode(shiftCode: String, date: LocalDate, alarmType: String): Int =
        "$date-$shiftCode-SNOOZE-$alarmType".hashCode()
}
