
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

    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_ALARM = "com.shiftalarm.app.ACTION_ALARM"
        const val ACTION_SNOOZE = "com.shiftalarm.app.ACTION_SNOOZE"

        const val EXTRA_SHIFT_CODE = "shift_code"
        const val EXTRA_DATE = "shift_date"
        const val EXTRA_REMINDER = "reminder_minutes"

        val REMINDER_MINUTES = listOf(60, 30, 15, 0)

        private const val SNOOZE_REQUEST_CODE = 99001
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    fun scheduleShift(date: LocalDate, shiftCode: String) {
        val shift = ShiftDefinitions.find(shiftCode)
            ?: throw IllegalArgumentException("Kode shift tidak dikenal")

        val start = LocalDateTime.of(date, shift.startTime)

        for (minutes in REMINDER_MINUTES) {
            val trigger = start.minusMinutes(minutes.toLong())

            if (trigger.isAfter(LocalDateTime.now())) {
                scheduleAlarm(trigger, date, shift.code, minutes)
            }
        }
    }

    private fun scheduleAlarm(
        dateTime: LocalDateTime,
        date: LocalDate,
        shiftCode: String,
        reminderMinutes: Int
    ) {
        if (!canScheduleExactAlarms()) {
            throw SecurityException("Izin alarm tepat belum diberikan")
        }

        val triggerMillis = dateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val requestCode = "$date-$shiftCode-$reminderMinutes".hashCode()

        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_SHIFT_CODE, shiftCode)
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_REMINDER, reminderMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent
        )
    }

    fun scheduleSnooze(shiftCode: String, date: LocalDate) {
        if (!canScheduleExactAlarms()) {
            throw SecurityException("Izin alarm tepat belum diberikan")
        }

        val triggerMillis = System.currentTimeMillis() + 5 * 60 * 1000L

        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_SHIFT_CODE, shiftCode)
            putExtra(EXTRA_DATE, date.toString())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            SNOOZE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMillis,
            pendingIntent
        )
    }

    fun cancelShift(date: LocalDate, shiftCode: String) {
        for (minutes in REMINDER_MINUTES) {
            val requestCode = "$date-$shiftCode-$minutes".hashCode()

            val intent = Intent(appContext, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM
            }

            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    fun cancelSnooze() {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            SNOOZE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}
