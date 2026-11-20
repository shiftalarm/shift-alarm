package com.shiftalarm.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in RECOVERY_ACTIONS) return

        val diagnostics = context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
        val scheduler = AlarmScheduler(context)
        val allShifts = try {
            ShiftScheduleStore(context).getAllShifts()
        } catch (error: Exception) {
            diagnostics.edit()
                .putString("last_event", action)
                .putString("last_result", "Gagal membaca jadwal: " + error.message)
                .putLong("last_attempt_at", System.currentTimeMillis())
                .commit()
            Log.e(TAG, "Unable to read saved shifts after " + action, error)
            return
        }

        val permissionGranted = scheduler.canScheduleExactAlarms()
        if (!permissionGranted) {
            diagnostics.edit()
                .putString("last_event", action)
                .putString("last_result", "Izin alarm tepat belum aktif")
                .putInt("saved_shifts", allShifts.size)
                .putInt("scheduled_shifts", 0)
                .putBoolean("exact_alarm_permission", false)
                .putLong("last_attempt_at", System.currentTimeMillis())
                .commit()
            Log.w(TAG, "Recovery skipped: exact alarm permission unavailable; saved=" + allShifts.size + ", action=" + action)
            return
        }

        val today = LocalDate.now()
        var scheduled = 0
        var skipped = 0
        val failures = mutableListOf<String>()

        allShifts.forEach { entry ->
            val date = entry.key
            val code = entry.value
            if (date.isBefore(today)) {
                skipped++
            } else {
                try {
                    scheduler.scheduleShift(date, code)
                    scheduled++
                } catch (error: Exception) {
                    failures += date.toString() + "/" + code + ": " + error.javaClass.simpleName + ": " + error.message
                    Log.e(TAG, "Failed to restore alarms for " + date + " / " + code, error)
                }
            }
        }

        val result = if (failures.isEmpty()) "OK" else "Sebagian gagal: " + failures.joinToString(" | ").take(1200)
        diagnostics.edit()
            .putString("last_event", action)
            .putString("last_result", result)
            .putInt("saved_shifts", allShifts.size)
            .putInt("scheduled_shifts", scheduled)
            .putInt("skipped_past_shifts", skipped)
            .putInt("failed_shifts", failures.size)
            .putBoolean("exact_alarm_permission", true)
            .putLong("last_attempt_at", System.currentTimeMillis())
            .commit()

        Log.i(TAG, "Recovery complete: action=" + action + ", saved=" + allShifts.size +
            ", scheduled=" + scheduled + ", skipped=" + skipped + ", failed=" + failures.size)
    }

    companion object {
        private const val TAG = "ShiftAlarmBoot"
        const val DIAGNOSTICS_PREFS = "shift_alarm_recovery_diagnostics"
        private val RECOVERY_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
