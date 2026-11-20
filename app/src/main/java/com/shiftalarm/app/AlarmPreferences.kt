package com.shiftalarm.app

import android.content.Context
import android.net.Uri
import java.time.LocalDate
import java.time.LocalTime

class AlarmPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("shift_alarm_settings", Context.MODE_PRIVATE)

    // Keep the original key as the start-time key so existing per-date settings survive updates.
    fun getStartAlarmTime(date: LocalDate, defaultTime: LocalTime): LocalTime =
        readTime(startTimeKey(date), defaultTime)

    fun setStartAlarmTime(date: LocalDate, time: LocalTime) {
        prefs.edit().putString(startTimeKey(date), time.toString()).apply()
    }

    fun getEndAlarmTime(date: LocalDate, defaultTime: LocalTime): LocalTime =
        readTime(endTimeKey(date), defaultTime)

    fun setEndAlarmTime(date: LocalDate, time: LocalTime) {
        prefs.edit().putString(endTimeKey(date), time.toString()).apply()
    }

    // Backward-compatible aliases for code paths from version 1.0.1.
    fun getAlarmTime(date: LocalDate, defaultTime: LocalTime): LocalTime =
        getStartAlarmTime(date, defaultTime)

    fun setAlarmTime(date: LocalDate, time: LocalTime) = setStartAlarmTime(date, time)

    fun clearAlarmTime(date: LocalDate) {
        prefs.edit()
            .remove(startTimeKey(date))
            .remove(endTimeKey(date))
            .apply()
    }

    private fun readTime(key: String, defaultTime: LocalTime): LocalTime {
        val raw = prefs.getString(key, null) ?: return defaultTime
        return runCatching { LocalTime.parse(raw) }.getOrDefault(defaultTime)
    }

    private fun startTimeKey(date: LocalDate) = "alarm_time_$date"
    private fun endTimeKey(date: LocalDate) = "alarm_end_time_$date"

    fun isAttendanceReminderEnabled(): Boolean = prefs.getBoolean(KEY_REMINDER_ENABLED, true)

    fun setAttendanceReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getAttendanceReminderText(): String =
        prefs.getString(KEY_REMINDER_TEXT, DEFAULT_REMINDER_TEXT)?.takeIf { it.isNotBlank() } ?: DEFAULT_REMINDER_TEXT

    fun setAttendanceReminderText(message: String) {
        prefs.edit().putString(KEY_REMINDER_TEXT, message.trim().ifBlank { DEFAULT_REMINDER_TEXT }).apply()
    }

    fun resetAttendanceReminderText() {
        prefs.edit().remove(KEY_REMINDER_TEXT).apply()
    }

    fun getRingtoneUri(): Uri? =
        prefs.getString(KEY_RINGTONE_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setRingtoneUri(uri: Uri?) {
        prefs.edit().putString(KEY_RINGTONE_URI, uri?.toString()).apply()
    }

    fun isGradualVolume(): Boolean = prefs.getBoolean(KEY_GRADUAL_VOLUME, true)

    fun setGradualVolume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GRADUAL_VOLUME, enabled).apply()
    }

    fun getRampDurationSeconds(): Int = prefs.getInt(KEY_RAMP_DURATION, 30).coerceIn(10, 120)

    fun setRampDurationSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_RAMP_DURATION, seconds.coerceIn(10, 120)).apply()
    }

    companion object {
        const val KEY_REMINDER_ENABLED = "attendance_reminder_enabled"
        const val KEY_REMINDER_TEXT = "attendance_reminder_text"
        const val DEFAULT_REMINDER_TEXT = "30 menit lagi waktunya mulai dinas. Pastikan Anda sudah siap."
        const val KEY_RINGTONE_URI = "alarm_ringtone_uri"
        const val KEY_GRADUAL_VOLUME = "gradual_volume"
        const val KEY_RAMP_DURATION = "ramp_duration_seconds"
    }
}
