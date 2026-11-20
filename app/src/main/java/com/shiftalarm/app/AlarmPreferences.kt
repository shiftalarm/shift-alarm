package com.shiftalarm.app

import android.content.Context
import android.net.Uri
import java.time.LocalDate
import java.time.LocalTime

class AlarmPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("shift_alarm_settings", Context.MODE_PRIVATE)

    fun getAlarmTime(date: LocalDate, defaultTime: LocalTime): LocalTime {
        val raw = prefs.getString("alarm_time_$date", null) ?: return defaultTime
        return runCatching { LocalTime.parse(raw) }.getOrDefault(defaultTime)
    }

    fun setAlarmTime(date: LocalDate, time: LocalTime) {
        prefs.edit().putString("alarm_time_$date", time.toString()).apply()
    }

    fun clearAlarmTime(date: LocalDate) {
        prefs.edit().remove("alarm_time_$date").apply()
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
        const val KEY_RINGTONE_URI = "alarm_ringtone_uri"
        const val KEY_GRADUAL_VOLUME = "gradual_volume"
        const val KEY_RAMP_DURATION = "ramp_duration_seconds"
    }
}
