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
        const val EXTRA_ALARM_KIND = "alarm_kind"
        const val KIND_START = "start"
        const val KIND_END = "end"
        const val ATTENDANCE_REMINDER_MINUTES = 30
        private val LEGACY_REMINDER_MINUTES = listOf(60, 30, 15, 0)
        private const val SNOOZE_REQUEST_CODE = 99001
        private const val START_CODE_OFFSET = 0
        private const val END_CODE_OFFSET = 1
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    fun scheduleShift(date: LocalDate, shiftCode: String) {
        val shift = ShiftDefinitions.find(shiftCode) ?: throw IllegalArgumentException("Kode shift tidak dikenal")
        cancelShift(date, shift.code)
        val prefs = AlarmPreferences(appContext)
        val startTime = prefs.getAlarmTime(date, shift.startTime)
        val endTime = prefs.getEndAlarmTime(date, shift.endTime)
        val start = LocalDateTime.of(date, startTime)
        val endDate = date.plusDays(shift.endDayOffset.toLong())
        val end = LocalDateTime.of(endDate, endTime)
        val now = LocalDateTime.now()

        val reminderTime = start.minusMinutes(ATTENDANCE_REMINDER_MINUTES.toLong())
        if (prefs.isAttendanceReminderEnabled() && reminderTime.isAfter(now)) {
            scheduleAlarm(reminderTime, date, shift.code, ATTENDANCE_REMINDER_MINUTES, startTime.toString(), KIND_START)
        }
        if (start.isAfter(now)) {
            scheduleAlarm(start, date, shift.code, 0, startTime.toString(), KIND_START)
        }
        if (end.isAfter(now)) {
            scheduleAlarm(end, date, shift.code, 0, endTime.toString(), KIND_END)
        }
    }

    private fun scheduleAlarm(
        dateTime: LocalDateTime,
        date: LocalDate,
        shiftCode: String,
        reminderMinutes: Int,
        alarmTime: String,
        kind: String
    ) {
        if (!canScheduleExactAlarms()) throw SecurityException("Izin alarm tepat belum diberikan")
        val triggerMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val kindOffset = if (kind == KIND_END) END_CODE_OFFSET else START_CODE_OFFSET
        val requestCode = "$date-$shiftCode-$reminderMinutes-$kindOffset".hashCode()
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_SHIFT_CODE, shiftCode)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_REMINDER, reminderMinutes)
            putExtra(EXTRA_ALARM_TIME, alarmTime)
            putExtra(EXTRA_ALARM_KIND, kind)
        }
        val pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    fun scheduleSnooze(shiftCode: String, date: LocalDate, kind: String = KIND_START, alarmTime: String? = null) {
        if (!canScheduleExactAlarms()) throw SecurityException("Izin alarm tepat belum diberikan")
        val triggerMillis = System.currentTimeMillis() + 5 * 60 * 1000L
        val shift = ShiftDefinitions.find(shiftCode)
        val fallback = if (kind == KIND_END) shift?.endTime else shift?.startTime
        val configured = alarmTime ?: if (kind == KIND_END) {
            AlarmPreferences(appContext).getEndAlarmTime(date, fallback ?: java.time.LocalTime.now()).toString()
        } else {
            AlarmPreferences(appContext).getAlarmTime(date, fallback ?: java.time.LocalTime.now()).toString()
        }
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_SHIFT_CODE, shiftCode)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_REMINDER, 0)
            putExtra(EXTRA_ALARM_TIME, configured)
            putExtra(EXTRA_ALARM_KIND, kind)
        }
        val pendingIntent = PendingIntent.getBroadcast(appContext, SNOOZE_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    fun cancelShift(date: LocalDate, shiftCode: String) {
        for (minutes in LEGACY_REMINDER_MINUTES) {
            for (kindOffset in START_CODE_OFFSET..END_CODE_OFFSET) {
                val requestCode = "$date-$shiftCode-$minutes-$kindOffset".hashCode()
                val intent = Intent(appContext, AlarmReceiver::class.java).apply { action = ACTION_ALARM }
                val pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
            }
            // Cancel request codes used by v1.1.0, which did not distinguish start/end.
            if (minutes == 0 || minutes == 30 || minutes == 15 || minutes == 60) {
                val legacyCode = "$date-$shiftCode-$minutes".hashCode()
                val legacyIntent = Intent(appContext, AlarmReceiver::class.java).apply { action = ACTION_ALARM }
                val legacyPending = PendingIntent.getBroadcast(appContext, legacyCode, legacyIntent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                legacyPending?.let { alarmManager.cancel(it); it.cancel() }
            }
        }
    }

    fun cancelSnooze() {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply { action = ACTION_SNOOZE }
        val pendingIntent = PendingIntent.getBroadcast(appContext, SNOOZE_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        pendingIntent?.let { alarmManager.cancel(it); it.cancel() }
    }
}
