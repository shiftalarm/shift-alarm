
package com.shiftalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED
        )
        if (intent.action !in supportedActions) return

        val store = ShiftScheduleStore(context)
        val scheduler = AlarmScheduler(context)

        if (!scheduler.canScheduleExactAlarms()) {
            return
        }

        val today = java.time.LocalDate.now()

        store.getAllShifts().forEach { (date, code) ->

            if (!date.isBefore(today)) {
                try {
                    scheduler.scheduleShift(date, code)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
