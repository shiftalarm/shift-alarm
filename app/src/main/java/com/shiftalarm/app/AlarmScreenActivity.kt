package com.shiftalarm.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmScreenActivity : Activity() {

    private var shiftCode: String = "Shift"
    private var date: LocalDate = LocalDate.now()
    private var configuredAlarmTime: LocalTime? = null
    private var alarmType: String = AlarmScheduler.ALARM_TYPE_START

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shiftCode = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: "Shift"
        configuredAlarmTime = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: AlarmScheduler.ALARM_TYPE_START
        date = try {
            LocalDate.parse(intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: "")
        } catch (_: Exception) {
            LocalDate.now()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        window.statusBarColor = Color.rgb(8, 17, 36)
        window.navigationBarColor = Color.rgb(8, 17, 36)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        createAlarmInterface()
    }

    private fun createAlarmInterface() {
        val navy = Color.rgb(8, 17, 36)
        val cyan = Color.rgb(71, 201, 230)
        val white = Color.WHITE
        val muted = Color.rgb(174, 190, 211)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(30), dp(28), dp(30))
            setBackgroundColor(navy)
        }

        val brand = TextView(this).apply {
            text = "SHIFT ALARM"
            textSize = 16f
            letterSpacing = 0.18f
            setTextColor(cyan)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        root.addView(brand, matchWrap())

        val status = TextView(this).apply {
            text = if (alarmType == AlarmScheduler.ALARM_TYPE_END) "ALARM SELESAI AKTIF" else "ALARM MULAI DINAS AKTIF"
            textSize = 12f
            letterSpacing = 0.16f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(26), 0, dp(8))
        }
        root.addView(status, matchWrap())

        val shiftDefinition = ShiftDefinitions.find(shiftCode)
        val fallbackTime = if (alarmType == AlarmScheduler.ALARM_TYPE_END) {
            shiftDefinition?.endTime
        } else {
            shiftDefinition?.startTime
        }
        val time = configuredAlarmTime ?: fallbackTime ?: LocalTime.now()
        val clock = TextView(this).apply {
            text = time.format(DateTimeFormatter.ofPattern("HH:mm"))
            textSize = 64f
            setTextColor(white)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        root.addView(clock, matchWrap())

        val shiftTitle = TextView(this).apply {
            text = if (alarmType == AlarmScheduler.ALARM_TYPE_END) "Waktunya Selesai Dinas $shiftCode" else "Waktunya Mulai Dinas $shiftCode"
            textSize = 25f
            setTextColor(white)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(shiftTitle, matchWrap())

        val dateLabel = TextView(this).apply {
            text = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", java.util.Locale("id", "ID")))
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        root.addView(dateLabel, matchWrap())

        val ringing = TextView(this).apply {
            text = if (alarmType == AlarmScheduler.ALARM_TYPE_END) "Waktunya selesai bertugas" else "Alarm sedang berbunyi"
            textSize = 14f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(0, dp(30), 0, dp(24))
        }
        root.addView(ringing, matchWrap())

        val snooze = Button(this).apply {
            text = "SNOOZE 5 MENIT"
            textSize = 18f
            isAllCaps = false
            setTextColor(navy)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(cyan))
            setOnClickListener { performAlarmAction(AlarmActionReceiver.ACTION_SNOOZE) }
        }
        root.addView(snooze, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ).apply { bottomMargin = dp(14) })

        val stop = Button(this).apply {
            text = "STOP"
            textSize = 17f
            isAllCaps = false
            setTextColor(white)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(43, 57, 78)))
            setOnClickListener { performAlarmAction(AlarmActionReceiver.ACTION_STOP) }
        }
        root.addView(stop, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ))

        setContentView(root)
    }

    private fun performAlarmAction(action: String) {
        val actionIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date.toString())
            putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, configuredAlarmTime?.toString())
        }
        sendBroadcast(actionIntent)
        finish()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
