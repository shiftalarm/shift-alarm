package com.shiftalarm.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.time.LocalDate

/**
 * AlarmManager clears registered alarms after reboot. Restore all saved schedules
 * after boot/unlock, app replacement, clock changes and exact-alarm permission grant.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShiftAlarmBoot"
        private const val RESTORE_WAKELOCK_TIMEOUT = 60_000L
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
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_USER_UNLOCKED
        )
        if (action !in supportedActions) return

        val app = context.applicationContext
        val pending = goAsync()
        val diagnostics = app.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShiftAlarm:BootRestore")
        try {
            wakeLock.acquire(RESTORE_WAKELOCK_TIMEOUT)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to acquire restore wakelock", error)
        }

        Thread({
            try {
                val scheduler = AlarmScheduler(app)
                val all = ShiftScheduleStore(app).getAllShifts().toSortedMap()
                if (!scheduler.canScheduleExactAlarms()) {
                    diagnostics.edit()
                        .putString("last_event", action)
                        .putString("last_result", "Izin alarm tepat belum aktif")
                        .putInt("saved_shifts", all.size)
                        .putInt("scheduled_shifts", 0)
                        .putBoolean("exact_alarm_permission", false)
                        .putLong("last_attempt_at", System.currentTimeMillis())
                        .commit()
                    Log.e(TAG, "Exact alarm permission unavailable; saved schedules=" + all.size)
                    return@Thread
                }

                val today = LocalDate.now()
                var restored = 0
                var skipped = 0
                val failures = mutableListOf<String>()
                all.forEach { (date, code) ->
                    // Include yesterday so an overnight shift can restore its end alarm after midnight.
                    if (date.isBefore(today.minusDays(1))) {
                        skipped++
                    } else {
                        runCatching {
                            scheduler.scheduleShift(date, code)
                            restored++
                        }.onFailure {
                            failures += date.toString() + "/" + code + ": " + it.javaClass.simpleName + ": " + it.message
                            Log.e(TAG, "Failed restoring " + code + " on " + date, it)
                        }
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
                        runCatching {
                            scheduler.scheduleActiveAlarmRestore(active)
                            activeRestored = true
                        }.onFailure {
                            failures += "active alarm: " + it.javaClass.simpleName + ": " + it.message
                            Log.e(TAG, "Failed restoring active ringing alarm", it)
                        }
                    }
                }

                val result = if (failures.isEmpty()) "OK" else "Sebagian gagal: " + failures.joinToString(" | ").take(1200)
                diagnostics.edit()
                    .putString("last_event", action)
                    .putString("last_result", result)
                    .putInt("saved_shifts", all.size)
                    .putInt("scheduled_shifts", restored)
                    .putInt("skipped_past_shifts", skipped)
                    .putInt("failed_shifts", failures.size)
                    .putBoolean("active_alarm_restored", activeRestored)
                    .putBoolean("exact_alarm_permission", true)
                    .putLong("last_attempt_at", System.currentTimeMillis())
                    .commit()

                Log.i(TAG, "Recovery complete: action=" + action + ", saved=" + all.size +
                    ", scheduled=" + restored + ", skipped=" + skipped + ", failed=" + failures.size +
                    ", activeAlarmRestored=" + activeRestored)
            } catch (error: Exception) {
                diagnostics.edit()
                    .putString("last_event", action)
                    .putString("last_result", "Exception: " + error.javaClass.simpleName + ": " + error.message)
                    .putLong("last_attempt_at", System.currentTimeMillis())
                    .commit()
                Log.e(TAG, "Boot restore failed for action=" + action, error)
            } finally {
                runCatching { if (wakeLock.isHeld) wakeLock.release() }
                pending.finish()
            }
        }, "ShiftAlarm-BootRestore").start()
    }
}
