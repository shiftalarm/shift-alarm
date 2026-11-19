
package com.shiftalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val shiftCode = intent.getStringExtra(
            AlarmScheduler.EXTRA_SHIFT_CODE
        ) ?: return

        val date = intent.getStringExtra(
            AlarmScheduler.EXTRA_DATE
        ) ?: return

        val reminderMinutes = intent.getIntExtra(
            AlarmScheduler.EXTRA_REMINDER,
            0
        )

        val serviceIntent = Intent(
            context,
            AlarmService::class.java
        ).apply {
            action = intent.action
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
            putExtra(AlarmScheduler.EXTRA_REMINDER, reminderMinutes)
        }

        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
