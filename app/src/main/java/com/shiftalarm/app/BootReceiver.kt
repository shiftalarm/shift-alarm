package com.shiftalarm.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/**
 * Android clears all AlarmManager registrations when the device restarts.
 * Recreate every future shift alarm after boot/unlock, app replacement,
 * date/time changes, or exact-alarm permission becoming available.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_USER_UNLOCKED
        )
        if (intent.action !in supportedActions) return

        val pendingResult = goAsync()
        Thread({
            try {
                val scheduler = AlarmScheduler(context.applicationContext)
                if (!scheduler.canScheduleExactAlarms()) return@Thread

                val today = LocalDate.now()
                ShiftScheduleStore(context.applicationContext)
                    .getAllShifts()
                    .forEach { (date, code) ->
                        if (!date.isBefore(today)) {
                            runCatching { scheduler.scheduleShift(date, code) }
                                .onFailure { it.printStackTrace() }
                        }
                    }
            } finally {
                pendingResult.finish()
            }
        }, "ShiftAlarm-BootRestore").start()
    }
}
