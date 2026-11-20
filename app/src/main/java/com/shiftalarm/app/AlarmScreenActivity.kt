package com.shiftalarm.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmScreenActivity : Activity() {
    private var shiftCode = "Shift"
    private var date = LocalDate.now()
    private var configuredAlarmTime: LocalTime? = null
    private var alarmKind = AlarmScheduler.KIND_START
    private var language = "id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shiftCode = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: "Shift"
        configuredAlarmTime = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        alarmKind = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND) ?: AlarmScheduler.KIND_START
        language = getSharedPreferences("shift_alarm_appearance", MODE_PRIVATE).getString("language", "id") ?: "id"
        date = try { LocalDate.parse(intent.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: "") } catch (_: Exception) { LocalDate.now() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        window.statusBarColor = Color.rgb(8, 17, 36)
        window.navigationBarColor = Color.rgb(8, 17, 36)
        createAlarmInterface()
    }

    private fun createAlarmInterface() {
        val navy = Color.rgb(8, 17, 36)
        val cyan = Color.rgb(71, 201, 230)
        val white = Color.WHITE
        val muted = Color.rgb(174, 190, 211)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(28), dp(30), dp(28), dp(30)); setBackgroundColor(navy)
        }
        fun text(value: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                if (bold) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(top), 0, dp(6))
            }
        root.addView(text("SHIFT ALARM", 16f, cyan, true))
        val title = if (language == "en") {
            if (alarmKind == AlarmScheduler.KIND_END) "TIME TO FINISH WORK" else "TIME TO START WORK"
        } else if (alarmKind == AlarmScheduler.KIND_END) "WAKTUNYA PULANG" else "WAKTUNYA MULAI DINAS"
        root.addView(text(if (language == "en") "ALARM ACTIVE" else "ALARM AKTIF", 12f, muted, true, 26))
        root.addView(text((configuredAlarmTime ?: ShiftDefinitions.find(shiftCode)?.let { if (alarmKind == AlarmScheduler.KIND_END) it.endTime else it.startTime } ?: LocalTime.now()).format(DateTimeFormatter.ofPattern("HH:mm")), 64f, white))
        root.addView(text(title, 23f, white, true, 18))
        root.addView(text("Shift $shiftCode", 16f, muted))
        root.addView(text(date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", if (language == "en") java.util.Locale.ENGLISH else java.util.Locale("id", "ID"))), 14f, muted))
        root.addView(text(if (language == "en") "Alarm is ringing" else "Alarm sedang berbunyi", 14f, cyan, false, 30))
        val snooze = Button(this).apply {
            text = "Snooze"; textSize = 17f; isAllCaps = false
            setTextColor(navy); setBackgroundTintList(android.content.res.ColorStateList.valueOf(cyan))
            setOnClickListener { performAlarmAction(AlarmActionReceiver.ACTION_SNOOZE) }
        }
        root.addView(snooze, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(14) })
        val stop = Button(this).apply {
            text = "Stop"; textSize = 17f; isAllCaps = false
            setTextColor(white); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(43, 57, 78)))
            setOnClickListener { performAlarmAction(AlarmActionReceiver.ACTION_STOP) }
        }
        root.addView(stop, LinearLayout.LayoutParams(-1, dp(76)))
        setContentView(root)
    }

    private fun performAlarmAction(action: String) {
        val actionIntent = Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date.toString())
            putExtra(AlarmActionReceiver.EXTRA_ACTION_ALARM_KIND, alarmKind)
            putExtra(AlarmActionReceiver.EXTRA_ACTION_ALARM_TIME, configuredAlarmTime?.toString())
        }
        sendBroadcast(actionIntent)
        finish()
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
