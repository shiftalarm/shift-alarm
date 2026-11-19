
package com.shiftalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

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
