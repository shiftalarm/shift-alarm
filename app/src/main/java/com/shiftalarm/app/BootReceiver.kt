package com.shiftalarm.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import java.time.LocalDate

/**
 * AlarmManager clears every registered alarm when Android reboots.
 * Re-register all saved future alarms after user unlock, package update,
 * date/time changes, and exact-alarm permission changes.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ShiftAlarmBoot"
        private const val RESTORE_WAKELOCK_TIMEOUT = 60_000L
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
                if (!scheduler.canScheduleExactAlarms()) {
                    Log.e(TAG, "Exact alarm permission unavailable; saved alarms cannot be restored")
                    return@Thread
                }

                val today = LocalDate.now()
                val all = ShiftScheduleStore(app).getAllShifts().toSortedMap()
                var restored = 0
                all.forEach { (date, code) ->
                    // Include yesterday so an overnight shift can still restore
                    // its end-of-duty alarm after a midnight reboot.
                    if (!date.isBefore(today.minusDays(1))) {
                        runCatching {
                            scheduler.scheduleShift(date, code)
                            restored++
                        }.onFailure { Log.e(TAG, "Failed restoring $code on $date", it) }
                    }
                }

                val settings = AlarmPreferences(app)
                val active = settings.getActiveAlarm()
                if (active != null) {
                    val activeDate = runCatching { LocalDate.parse(active.date) }.getOrNull()
                    if (activeDate == null || activeDate.isBefore(today.minusDays(1))) {
                        settings.clearActiveAlarm()
                    } else {
                        runCatching { scheduler.scheduleActiveAlarmRestore(active) }
                            .onFailure { Log.e(TAG, "Failed restoring active ringing alarm", it) }
                    }
                }
                Log.i(TAG, "Boot restore completed for action=$action; schedules=$restored")
            } catch (error: Exception) {
                Log.e(TAG, "Boot restore failed for action=$action", error)
            } finally {
                runCatching { if (wakeLock.isHeld) wakeLock.release() }
                pending.finish()
            }
        }, "ShiftAlarm-BootRestore").start()
    }
}
