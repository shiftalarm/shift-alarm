
package com.shiftalarm.app

import android.content.Context
import java.time.LocalDate
import org.json.JSONObject

class ShiftScheduleStore(context: Context) {

    private val preferences = context.getSharedPreferences(
        "shift_schedule",
        Context.MODE_PRIVATE
    )

    fun setShift(date: LocalDate, code: String) {
        require(ShiftDefinitions.find(code) != null) {
            "Kode shift tidak dikenal: $code"
        }

        preferences.edit()
            .putString(date.toString(), code.uppercase())
            .apply()
    }

    fun getShift(date: LocalDate): String? {
        return preferences.getString(date.toString(), null)
    }

    fun removeShift(date: LocalDate) {
        preferences.edit()
            .remove(date.toString())
            .apply()
    }

    fun getAllShifts(): Map<LocalDate, String> {
        return preferences.all.mapNotNull { (key, value) ->
            val date = runCatching {
                LocalDate.parse(key)
            }.getOrNull()

            val code = value as? String

            if (date != null && code != null) {
                date to code
            } else {
                null
            }
        }.toMap()
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }
}
