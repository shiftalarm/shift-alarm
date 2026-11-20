package com.shiftalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "com.shiftalarm.app.ACTION_STOP"
        const val ACTION_SNOOZE = "com.shiftalarm.app.ACTION_SNOOZE_BUTTON"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val shiftCode = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: return
        val dateString = intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: return
        val date = try {
            LocalDate.parse(dateString)
        } catch (_: Exception) {
            return
        }

        val scheduler = AlarmScheduler(context)
        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE)
            ?: AlarmScheduler.ALARM_TYPE_START
        val alarmTime = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME)
            ?: ShiftDefinitions.find(shiftCode)?.let {
                if (alarmType == AlarmScheduler.ALARM_TYPE_END) it.endTime.toString() else it.startTime.toString()
            } ?: "00:00"
        when (intent.action) {
            ACTION_STOP -> {
                AlarmPreferences(context).clearActiveAlarm()
                scheduler.cancelSnooze(shiftCode, date, AlarmScheduler.ALARM_TYPE_START)
                scheduler.cancelSnooze(shiftCode, date, AlarmScheduler.ALARM_TYPE_END)
                context.stopService(Intent(context, AlarmService::class.java))
            }
            ACTION_SNOOZE -> {
                AlarmPreferences(context).clearActiveAlarm()
                scheduler.cancelSnooze(shiftCode, date, alarmType)
                context.stopService(Intent(context, AlarmService::class.java))
                try {
                    scheduler.scheduleSnooze(shiftCode, date, alarmType, alarmTime)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
