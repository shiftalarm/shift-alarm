package com.shiftalarm.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.time.LocalDate

/**
 * AlarmManager drops all scheduled alarms at reboot.
 * This receiver is direct-boot aware so it can receive USER_UNLOCKED.
 * Schedule data lives in credential-protected preferences, therefore it is
 * deliberately read only after the user has unlocked the device.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShiftAlarmBoot"
        private const val RESTORE_WAKELOCK_TIMEOUT = 60_000L
        const val DIAGNOSTICS_PREFS = "shift_alarm_recovery_diagnostics"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Do not access app preferences during Direct Boot. They are protected
        // by the user's credential and become available after ACTION_USER_UNLOCKED.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i(TAG, "Locked boot received; waiting for user unlock before restoring schedules")
            return
        }

        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        if (action !in supportedActions) return

        val app = context.applicationContext
        val pending = goAsync()
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShiftAlarm:BootRestore")
        try {
            wakeLock.acquire(RESTORE_WAKELOCK_TIMEOUT)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to acquire restore wakelock", error)
        }

        Thread({
            try {
                val diagnostics = app.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
                val all = ShiftScheduleStore(app).getAllShifts().toSortedMap()
                val attemptedAt = System.currentTimeMillis()
                val scheduler = AlarmScheduler(app)

                if (!scheduler.canScheduleExactAlarms()) {
                    diagnostics.edit()
                        .putString("last_event", action)
                        .putString("last_result", "Izin alarm tepat belum aktif")
                        .putInt("saved_shifts", all.size)
                        .putInt("scheduled_shifts", 0)
                        .putInt("failed_shifts", 0)
                        .putBoolean("exact_alarm_permission", false)
                        .putLong("last_attempt_at", attemptedAt)
                        .commit()
                    Log.e(TAG, "Exact alarm permission unavailable; saved schedules=${all.size}")
                    return@Thread
                }

                val today = LocalDate.now()
                var restored = 0
                var skipped = 0
                val failures = mutableListOf<String>()

                all.forEach { (date, code) ->
                    if (date.isBefore(today.minusDays(1))) {
                        skipped++
                    } else {
                        try {
                            scheduler.scheduleShift(date, code)
                            restored++
                        } catch (error: Exception) {
                            failures += "$date/$code: ${error.javaClass.simpleName}: ${error.message}"
                            Log.e(TAG, "Failed restoring $code on $date", error)
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
                        try {
                            scheduler.scheduleActiveAlarmRestore(active)
                            activeRestored = true
                        } catch (error: Exception) {
                            failures += "active alarm: ${error.javaClass.simpleName}: ${error.message}"
                            Log.e(TAG, "Failed restoring active alarm", error)
                        }
                    }
                }

                val result = if (failures.isEmpty()) "OK" else
                    "Sebagian gagal: " + failures.joinToString(" | ").take(1200)
                diagnostics.edit()
                    .putString("last_event", action)
                    .putString("last_result", result)
                    .putInt("saved_shifts", all.size)
                    .putInt("scheduled_shifts", restored)
                    .putInt("skipped_past_shifts", skipped)
                    .putInt("failed_shifts", failures.size)
                    .putBoolean("active_alarm_restored", activeRestored)
                    .putBoolean("exact_alarm_permission", true)
                    .putLong("last_attempt_at", attemptedAt)
                    .commit()

                Log.i(TAG, "Recovery finished: action=$action saved=${all.size}, " +
                    "scheduled=$restored, skipped=$skipped, failed=${failures.size}")
            } catch (error: Exception) {
                Log.e(TAG, "Boot restore failed for action=$action", error)
                runCatching {
                    app.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE).edit()
                        .putString("last_event", action)
                        .putString("last_result", "ERROR: ${error.javaClass.simpleName}: ${error.message}")
                        .putLong("last_attempt_at", System.currentTimeMillis())
                        .commit()
                }
            } finally {
                runCatching { if (wakeLock.isHeld) wakeLock.release() }
                pending.finish()
            }
        }, "ShiftAlarm-BootRestore").start()
    }
}
