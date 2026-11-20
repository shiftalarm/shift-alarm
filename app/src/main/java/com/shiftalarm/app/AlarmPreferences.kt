package com.shiftalarm.app

import android.content.Context
import android.net.Uri
import java.time.LocalDate
import java.time.LocalTime

class AlarmPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("shift_alarm_settings", Context.MODE_PRIVATE)

    fun getAlarmTime(date: LocalDate, defaultTime: LocalTime): LocalTime {
        val migrated = prefs.getString("alarm_start_time_$date", null)
        if (migrated != null) return runCatching { LocalTime.parse(migrated) }.getOrDefault(defaultTime)
        // Preserve custom start times created by v1.1.0.
        val legacy = prefs.getString("alarm_time_$date", null)
        if (legacy != null) {
            val parsed = runCatching { LocalTime.parse(legacy) }.getOrDefault(defaultTime)
            prefs.edit().putString("alarm_start_time_$date", parsed.toString()).apply()
            return parsed
        }
        return defaultTime
    }

    fun setAlarmTime(date: LocalDate, time: LocalTime) =
        prefs.edit().putString("alarm_start_time_$date", time.toString()).apply()

    fun getEndAlarmTime(date: LocalDate, defaultTime: LocalTime): LocalTime =
        getTime("alarm_end_time_$date", defaultTime)

    fun setEndAlarmTime(date: LocalDate, time: LocalTime) =
        prefs.edit().putString("alarm_end_time_$date", time.toString()).apply()

    private fun getTime(key: String, fallback: LocalTime): LocalTime {
        val raw = prefs.getString(key, null) ?: return fallback
        return runCatching { LocalTime.parse(raw) }.getOrDefault(fallback)
    }

    fun clearAlarmTime(date: LocalDate) {
        prefs.edit().remove("alarm_start_time_$date").remove("alarm_end_time_$date").apply()
    }

    fun getRingtoneUri(): Uri? =
        prefs.getString(KEY_RINGTONE_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun setRingtoneUri(uri: Uri?) {
        prefs.edit().putString(KEY_RINGTONE_URI, uri?.toString()).apply()
    }

    fun isGradualVolume(): Boolean = prefs.getBoolean(KEY_GRADUAL_VOLUME, true)
    fun setGradualVolume(enabled: Boolean) { prefs.edit().putBoolean(KEY_GRADUAL_VOLUME, enabled).apply() }
    fun getRampDurationSeconds(): Int = prefs.getInt(KEY_RAMP_DURATION, 30).coerceIn(10, 120)
    fun setRampDurationSeconds(seconds: Int) { prefs.edit().putInt(KEY_RAMP_DURATION, seconds.coerceIn(10, 120)).apply() }
    fun isAttendanceReminderEnabled(): Boolean = prefs.getBoolean(KEY_ATTENDANCE_REMINDER_ENABLED, true)
    fun setAttendanceReminderEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_ATTENDANCE_REMINDER_ENABLED, enabled).apply() }

    fun getReminderText(language: String = "id"): String {
        val saved = prefs.getString(KEY_REMINDER_TEXT, null)
        if (saved != null) return saved
        return if (language == "en") DEFAULT_REMINDER_TEXT_EN else DEFAULT_REMINDER_TEXT
    }
    fun setReminderText(value: String) { prefs.edit().putString(KEY_REMINDER_TEXT, value.trim().ifBlank { DEFAULT_REMINDER_TEXT }).apply() }
    fun resetReminderText() { prefs.edit().remove(KEY_REMINDER_TEXT).apply() }

    companion object {
        const val KEY_RINGTONE_URI = "alarm_ringtone_uri"
        const val KEY_GRADUAL_VOLUME = "gradual_volume"
        const val KEY_RAMP_DURATION = "ramp_duration_seconds"
        const val KEY_ATTENDANCE_REMINDER_ENABLED = "attendance_reminder_enabled"
        const val KEY_REMINDER_TEXT = "attendance_reminder_text"
        const val DEFAULT_REMINDER_TEXT = "Persiapan Dinas {shift} — 30 menit lagi waktunya mulai dinas. Pastikan Anda sudah siap."
        const val DEFAULT_REMINDER_TEXT_EN = "Shift {shift} preparation — your work shift starts in 30 minutes. Please get ready."
    }
}
