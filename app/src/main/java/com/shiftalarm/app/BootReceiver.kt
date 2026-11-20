package com.shiftalarm.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

/**
 * AlarmManager registrations are cleared by Android during reboot.
 * Restore after credential-protected app storage becomes available, and
 * after app update, clock changes, or exact-alarm permission is granted.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private val RESTORE_LOCK = Any()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // App schedules live in credential-protected storage. Wait until the
        // user unlocks; ACTION_USER_UNLOCKED will run the actual restoration.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return

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

        val pendingResult = goAsync()
        Thread({
            try {
                synchronized(RESTORE_LOCK) {
                    val app = context.applicationContext
                    val scheduler = AlarmScheduler(app)
                    if (!scheduler.canScheduleExactAlarms()) return@synchronized

                    val today = LocalDate.now()
                    ShiftScheduleStore(app).getAllShifts()
                        .toSortedMap()
                        .forEach { (date, code) ->
                            if (!date.isBefore(today)) {
                                runCatching { scheduler.scheduleShift(date, code) }
                                    .onFailure { it.printStackTrace() }
                            }
                        }

                    // If the device was restarted while the main alarm was
                    // already ringing, recreate a short exact trigger so the
                    // foreground alarm service starts again after unlock.
                    val settings = AlarmPreferences(app)
                    val active = settings.getActiveAlarm()
                    if (active != null) {
                        val activeDate = runCatching { LocalDate.parse(active.date) }.getOrNull()
                        if (activeDate == null || activeDate.isBefore(today.minusDays(1))) {
                            settings.clearActiveAlarm()
                        } else {
                            runCatching { scheduler.scheduleActiveAlarmRestore(active) }
                                .onFailure { it.printStackTrace() }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }, "ShiftAlarm-BootRestore").start()
    }
}
