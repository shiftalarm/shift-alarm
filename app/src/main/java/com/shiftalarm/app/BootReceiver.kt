
package com.shiftalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager

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

        val pending = goAsync()
        Thread {
            try {
                val store = ShiftScheduleStore(context)
                val scheduler = AlarmScheduler(context)
                if (scheduler.canScheduleExactAlarms()) {
                    val today = java.time.LocalDate.now()
                    store.getAllShifts().forEach { (date, code) ->
                        if (!date.isBefore(today)) {
                            runCatching { scheduler.scheduleShift(date, code) }
                                .onFailure { it.printStackTrace() }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
