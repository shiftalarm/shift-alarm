package com.shiftalarm.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.LocalDate

/**
 * Recreate AlarmManager entries after Android clears them during shutdown.
 * BOOT_COMPLETED arrives after unlock, so credential-protected preferences
 * are available. Restore synchronously before the receiver returns.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ShiftAlarmBoot"
        const val DIAGNOSTICS_PREFS = "shift_alarm_recovery_diagnostics"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (action !in supportedActions) return

        val app = context.applicationContext
        val diagnostics = app.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
        val attemptedAt = System.currentTimeMillis()

        try {
            val scheduler = AlarmScheduler(app)
            val schedules = ShiftScheduleStore(app).getAllShifts().toSortedMap()

            if (!scheduler.canScheduleExactAlarms()) {
                diagnostics.edit()
                    .putString("last_event", action)
                    .putString("last_result", "Izin Alarm & reminders belum aktif")
                    .putInt("saved_shifts", schedules.size)
                    .putInt("scheduled_shifts", 0)
                    .putInt("failed_shifts", 0)
                    .putBoolean("exact_alarm_permission", false)
                    .putLong("last_attempt_at", attemptedAt)
                    .commit()
                Log.e(TAG, "Exact alarm access unavailable; saved schedules=${schedules.size}")
                return
            }

            val today = LocalDate.now()
            var scheduled = 0
            var skipped = 0
            val failures = mutableListOf<String>()

            // Include yesterday so overnight shifts can retain their end alarm.
            for ((date, code) in schedules) {
                if (date.isBefore(today.minusDays(1))) {
                    skipped++
                    continue
                }
                try {
                    scheduler.scheduleShift(date, code)
                    scheduled++
                } catch (error: Exception) {
                    failures += "$date/$code: ${error.javaClass.simpleName}: ${error.message}"
                    Log.e(TAG, "Could not restore shift $code on $date", error)
                }
            }

            val settings = AlarmPreferences(app)
            val active = settings.getActiveAlarm()
            var activeRestored = false
            if (active != null) {
                val activeDate = runCatching { LocalDate.parse(active.date) }.getOrNull()
                if (activeDate == null || activeDate.isBefore(today.minusDays(1))) {
                    settings.clearActiveAlarm()
                } else {
                    try {
                        scheduler.scheduleActiveAlarmRestore(active)
                        activeRestored = true
                    } catch (error: Exception) {
                        failures += "active alarm: ${error.javaClass.simpleName}: ${error.message}"
                        Log.e(TAG, "Could not restore active alarm", error)
                    }
                }
            }

            val result = if (failures.isEmpty()) "OK" else
                "Sebagian gagal: " + failures.joinToString(" | ").take(1200)
            diagnostics.edit()
                .putString("last_event", action)
                .putString("last_result", result)
                .putInt("saved_shifts", schedules.size)
                .putInt("scheduled_shifts", scheduled)
                .putInt("skipped_past_shifts", skipped)
                .putInt("failed_shifts", failures.size)
                .putBoolean("active_alarm_restored", activeRestored)
                .putBoolean("exact_alarm_permission", true)
                .putLong("last_attempt_at", attemptedAt)
                .commit()

            Log.i(TAG, "Restore finished: event=$action saved=${schedules.size}, " +
                "scheduled=$scheduled, skipped=$skipped, failed=${failures.size}")
        } catch (error: Exception) {
            Log.e(TAG, "Boot recovery failed for event=$action", error)
            runCatching {
                diagnostics.edit()
                    .putString("last_event", action)
                    .putString("last_result", "ERROR: ${error.javaClass.simpleName}: ${error.message}")
                    .putLong("last_attempt_at", attemptedAt)
                    .commit()
            }
        }
    }
}
