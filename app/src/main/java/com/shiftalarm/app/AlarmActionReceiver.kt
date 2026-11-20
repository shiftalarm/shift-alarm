package com.shiftalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class AlarmActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_STOP = "com.shiftalarm.app.ACTION_STOP"
        const val ACTION_SNOOZE = "com.shiftalarm.app.ACTION_SNOOZE_BUTTON"
        const val EXTRA_ACTION_ALARM_KIND = "action_alarm_kind"
        const val EXTRA_ACTION_ALARM_TIME = "action_alarm_time"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val shiftCode = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: return
        val dateString = intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: return
        val date = try { LocalDate.parse(dateString) } catch (_: Exception) { return }
        val scheduler = AlarmScheduler(context)
        when (intent.action) {
            ACTION_STOP -> {
                scheduler.cancelSnooze()
                context.stopService(Intent(context, AlarmService::class.java))
            }
            ACTION_SNOOZE -> {
                scheduler.cancelSnooze()
                context.stopService(Intent(context, AlarmService::class.java))
                try {
                    val kind = intent.getStringExtra(EXTRA_ACTION_ALARM_KIND) ?: AlarmScheduler.KIND_START
                    val time = intent.getStringExtra(EXTRA_ACTION_ALARM_TIME)
                    scheduler.scheduleSnooze(shiftCode, date, kind, time)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
}
