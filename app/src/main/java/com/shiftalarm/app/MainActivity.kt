package com.shiftalarm.app

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.media.Ringtone
import android.media.RingtoneManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: ShiftScheduleStore
    private lateinit var scheduler: AlarmScheduler
    private lateinit var prefs: AlarmPreferences
    private lateinit var root: FrameLayout
    private lateinit var pageHost: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private lateinit var selectedDateLabel: TextView
    private lateinit var startTimeLabel: TextView
    private lateinit var endTimeLabel: TextView
    private lateinit var shiftSpinner: Spinner
    private lateinit var statusText: TextView\n    private lateinit var startTimeValue: TextView\n    private lateinit var endTimeValue: TextView
    private var previewRingtone: Ringtone? = null
    private val previewHandler = Handler(Looper.getMainLooper())
    private val stopPreview = Runnable { stopTonePreview() }
    private var selectedDate = LocalDate.now()
    private var displayedMonth = YearMonth.now()
    private var currentTab = 0
    private var darkMode = true
    private var accentIndex = 0
    private var customAccent = Color.rgb(36, 190, 220)
    private var language = "id"
    private var customBackground: Uri? = null
    private var reminderDraft = ""

    private val accents = listOf(
        Color.rgb(36, 190, 220),
        Color.rgb(94, 132, 255),
        Color.rgb(55, 190, 145),
        Color.rgb(184, 125, 255),
        Color.rgb(255, 158, 84),
        Color.rgb(238, 105, 139),
        Color.rgb(167, 190, 82),
        Color.rgb(103, 190, 190),
        Color.rgb(244, 193, 76),
        Color.rgb(142, 151, 255)
    )

    private val backgroundColor get() = if (darkMode) Color.rgb(9, 17, 31) else Color.rgb(241, 245, 250)
    private val cardColor get() = if (darkMode) Color.rgb(20, 32, 51) else Color.WHITE
    private val primaryText get() = if (darkMode) Color.rgb(242, 247, 255) else Color.rgb(24, 38, 58)
    private val secondaryText get() = if (darkMode) Color.rgb(157, 176, 201) else Color.rgb(103, 119, 141)
    private val accent get() = if (accentIndex == accents.size) customAccent else accents[accentIndex]

    private fun tr(id: String, en: String): String = if (language == "en") en else id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShiftScheduleStore(this)
        scheduler = AlarmScheduler(this)
        prefs = AlarmPreferences(this)
        val appearance = getSharedPreferences("shift_alarm_appearance", MODE_PRIVATE)
        darkMode = appearance.getBoolean("dark_mode", true)
        accentIndex = appearance.getInt("accent_index", 0).coerceIn(0, accents.size)
        customAccent = appearance.getInt("custom_accent", accents[0])
        language = appearance.getString("language", "id") ?: "id"
        customBackground = appearance.getString("custom_background", null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        buildShell()
        requestNotificationPermission()
        showSplash()
    }

    private fun buildShell() {
        root = FrameLayout(this).apply { setBackgroundColor(backgroundColor) }
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pageHost = FrameLayout(this)
        shell.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))
        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(8))
            setBackgroundColor(cardColor)
            elevation = dp(12).toFloat()
        }
        shell.addView(bottomNav, LinearLayout.LayoutParams(-1, dp(68)))
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        renderNavigation()
        renderTab(0, false)
        applyCustomBackground()
    }

    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(8, 17, 36))
            alpha = 0f
            scaleX = 0.97f
            scaleY = 0.97f
        }
        val mark = TextView(this).apply {
            text = "◈"
            textSize = 58f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(71, 201, 230))
            typeface = Typeface.DEFAULT_BOLD
        }
        val name = TextView(this).apply {
            text = "SHIFT ALARM"
            textSize = 25f
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val tagline = TextView(this).apply {
            text = "SMART SHIFT SCHEDULE & ALARM"
            textSize = 10f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(157, 176, 201))
        }
        splash.addView(mark)
        splash.addView(name)
        splash.addView(tagline)
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        splash.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(DecelerateInterpolator()).start()
        Handler(Looper.getMainLooper()).postDelayed({
            splash.animate().alpha(0f).scaleX(1.025f).scaleY(1.025f).setDuration(420).withEndAction {
                root.removeView(splash)
                animatePageIn(pageHost)
            }.start()
        }, 1050)
    }

    private fun renderNavigation() {
        bottomNav.removeAllViews()
        val labels = arrayOf(tr("Home", "Home"), tr("Ringkasan", "Summary"), tr("Profil", "Profile"))
        val icons = arrayOf("⌂", "▦", "◎")
        for (i in labels.indices) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(4), dp(5), dp(4))
                background = rounded(if (currentTab == i) if (darkMode) Color.rgb(30, 57, 77) else Color.rgb(224, 241, 247) else Color.TRANSPARENT, 14)
                setOnClickListener { if (currentTab != i) { currentTab = i; renderNavigation(); renderTab(i, true) } }
            }
            item.addView(TextView(this).apply {
                text = icons[i]
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(if (currentTab == i) accent else secondaryText)
            })
            item.addView(TextView(this).apply {
                text = labels[i]
                textSize = 10f
                gravity = Gravity.CENTER
                typeface = if (currentTab == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (currentTab == i) accent else secondaryText)
            })
            bottomNav.addView(item, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(4); rightMargin = dp(4) })
        }
    }

    private fun renderTab(tab: Int, animate: Boolean) {
        val page = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(backgroundColor) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        page.addView(column)
        when (tab) {
            0 -> buildHome(column)
            1 -> buildSummary(column)
            else -> buildProfile(column)
        }
        pageHost.removeAllViews()
        pageHost.addView(page, FrameLayout.LayoutParams(-1, -1))
        if (animate) animatePageIn(page)
    }

    private fun animatePageIn(view: View) {
        view.alpha = 0f
        view.translationY = dp(12).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun buildHome(parent: LinearLayout) {
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("SHIFT ALARM", 23f, primaryText, true))
        brand.addView(label("SMART SHIFT SCHEDULE & ALARM", 10f, secondaryText, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionButton("⚙", accent) { currentTab = 2; renderNavigation(); renderTab(2, true) }, LinearLayout.LayoutParams(dp(48), dp(44)))
        parent.addView(header)
        space(parent, 16)

        val hero = card()
        hero.addView(label(tr("ATUR DINAS, BIARKAN ALARM MENGINGATKAN.", "SET YOUR SHIFT, LET THE ALARM REMIND YOU."), 21f, primaryText, true))
        space(hero, 8)
        hero.addView(label(tr("Dua alarm otomatis: mulai kerja dan pulang kerja, plus pengingat persiapan 30 menit sebelumnya.", "Two automatic alarms: work start and finish, plus a 30-minute preparation reminder."), 13f, secondaryText, false))
        space(hero, 14)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton(tr("IZIN ALARM", "ALARM PERMISSION"), if (scheduler.canScheduleExactAlarms()) Color.rgb(43,166,124) else Color.rgb(205,139,48)) { openExactAlarmSettings() }, LinearLayout.LayoutParams(0, dp(42), 1f))
        actions.addView(spaceView(8))
        actions.addView(actionButton(tr("PENGATURAN SUARA", "SOUND SETTINGS"), Color.rgb(61,91,125)) { currentTab = 2; renderNavigation(); renderTab(2, true) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        hero.addView(actions)
        space(hero, 12)
        val reminder = Switch(this).apply {
            text = tr("Pengingat persiapan 30 menit sebelum mulai", "Preparation reminder 30 minutes before start")
            textSize = 12f; setTextColor(primaryText); isChecked = prefs.isAttendanceReminderEnabled()
            setOnCheckedChangeListener { _, enabled ->
                prefs.setAttendanceReminderEnabled(enabled)
                store.getAllShifts().forEach { (date, code) -> if (!date.isBefore(LocalDate.now()) && scheduler.canScheduleExactAlarms()) runCatching { scheduler.scheduleShift(date, code) } }
            }
        }
        hero.addView(reminder)
        parent.addView(hero)
        space(parent, 15)

        val upcoming = store.getAllShifts().entries.filter { !it.key.isBefore(LocalDate.now()) }.minByOrNull { it.key }
        val next = card()
        next.addView(label(tr("DINAS BERIKUTNYA", "NEXT SHIFT"), 11f, accent, true))
        space(next, 8)
        next.addView(label(if (upcoming == null) tr("Belum ada jadwal tersimpan", "No saved schedule yet") else {
            val s = ShiftDefinitions.find(upcoming.value)
            "${upcoming.value}  •  ${upcoming.key.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale(if (language == "en") "en" else "id")))}\n${tr("Mulai", "Start")} ${prefs.getAlarmTime(upcoming.key, s!!.startTime)}   |   ${tr("Pulang", "Finish")} ${prefs.getEndAlarmTime(upcoming.key, s.endTime)}"
        }, 17f, primaryText, true))
        parent.addView(next)
        space(parent, 16)

        val calendar = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(tr("KALENDER DINAS", "SHIFT CALENDAR"), 13f, primaryText, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("‹", accent) { displayedMonth = displayedMonth.minusMonths(1); renderTab(0, true) }, LinearLayout.LayoutParams(dp(40), dp(36)))
        top.addView(spaceView(5))
        top.addView(actionButton("›", accent) { displayedMonth = displayedMonth.plusMonths(1); renderTab(0, true) }, LinearLayout.LayoutParams(dp(40), dp(36)))
        calendar.addView(top)
        space(calendar, 10)
        calendar.addView(label(displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale(if (language == "en") "en" else "id"))), 17f, primaryText, true))
        space(calendar, 8)
        val grid = GridLayout(this).apply { columnCount = 7; alignmentMode = GridLayout.ALIGN_BOUNDS }
        calendar.addView(grid)
        val weekdays = if (language == "en") listOf("MON","TUE","WED","THU","FRI","SAT","SUN") else listOf("SEN","SEL","RAB","KAM","JUM","SAB","MIN")
        weekdays.forEach { d -> grid.addView(label(d, 9f, secondaryText, true).apply { gravity = Gravity.CENTER }, GridLayout.LayoutParams().apply { width=0; height=dp(30); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) }) }
        repeat(displayedMonth.atDay(1).dayOfWeek.value - 1) {
            grid.addView(View(this), GridLayout.LayoutParams().apply { width=0; height=dp(48); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) })
        }
        for (day in 1..displayedMonth.lengthOfMonth()) {
            val date = displayedMonth.atDay(day)
            val code = store.getShift(date)
            val selected = date == selectedDate
            val cell = TextView(this).apply {
                text = if (code == null) "$day" else "$day\n$code"
                textSize = if (code == null) 13f else 10f
                gravity = Gravity.CENTER
                setTextColor(if (selected || code != null) Color.WHITE else primaryText)
                typeface = if (selected || date == LocalDate.now() || code != null) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = rounded(if (selected) accent else shiftColor(code), 10)
                setOnClickListener { selectedDate = date; renderTab(0, true) }
            }
            grid.addView(cell, GridLayout.LayoutParams().apply { width=0; height=dp(46); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(2),dp(2),dp(2),dp(2)) })
        }
        space(calendar, 8)
        calendar.addView(label(tr("Ketuk tanggal untuk mengatur jadwal dan alarm.", "Tap a date to configure its schedule and alarms."), 11f, secondaryText, false))
        parent.addView(calendar)
        space(parent, 16)

        val editor = card()
        editor.addView(label(tr("ATUR JADWAL DINAS", "SHIFT SCHEDULE"), 13f, primaryText, true))
        space(editor, 10)
        selectedDateLabel = label(selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale(if (language == "en") "en" else "id"))), 15f, accent, true)
        editor.addView(selectedDateLabel)
        space(editor, 9)
        editor.addView(actionButton(tr("PILIH TANGGAL LAIN", "CHOOSE ANOTHER DATE"), accent) { openDatePicker() })
        space(editor, 10)
        editor.addView(label(tr("Kode shift", "Shift code"), 12f, secondaryText, true))
        val options = ShiftDefinitions.all.map { "${it.code}   •   ${it.startTime} – ${it.endTime}" }
        shiftSpinner = Spinner(this)
        shiftSpinner.adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply { setTextColor(primaryText); textSize=14f; setPadding(dp(10),dp(8),dp(10),dp(8)) }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(primaryText); textSize=14f; setPadding(dp(12),dp(12),dp(12),dp(12)); setBackgroundColor(cardColor)
                }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val currentCode = store.getShift(selectedDate)
        val idx = ShiftDefinitions.all.indexOfFirst { it.code == currentCode }
        if (idx >= 0) shiftSpinner.setSelection(idx)
        editor.addView(shiftSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        space(editor, 12)

        val currentShift = ShiftDefinitions.find(currentCode ?: ShiftDefinitions.all.first().code) ?: ShiftDefinitions.all.first()
        val start = prefs.getAlarmTime(selectedDate, currentShift.startTime)
        val end = prefs.getEndAlarmTime(selectedDate, currentShift.endTime)
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val startBox = timeBox(tr("JAM MULAI", "START TIME"), start.toString()) { openTimePicker(false) }
        val endBox = timeBox(tr("JAM PULANG", "FINISH TIME"), end.toString()) { openTimePicker(true) }
        times.addView(startBox, LinearLayout.LayoutParams(0, dp(92), 1f))
        times.addView(spaceView(8))
        times.addView(endBox, LinearLayout.LayoutParams(0, dp(92), 1f))
        editor.addView(times)
        space(editor, 12)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(actionButton(tr("SIMPAN JADWAL", "SAVE SCHEDULE"), accent) { saveSchedule() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        buttons.addView(spaceView(8))
        buttons.addView(actionButton(tr("HAPUS", "DELETE"), Color.rgb(190,75,87)) { confirmRemoveSchedule() }, LinearLayout.LayoutParams(dp(94), dp(46)))
        editor.addView(buttons)
        space(editor, 9)
        statusText = label(if (scheduler.canScheduleExactAlarms()) tr("● Izin alarm tepat aktif", "● Exact alarm permission active") else tr("● Izin alarm tepat belum aktif", "● Exact alarm permission is not enabled"), 11f, secondaryText, false)
        editor.addView(statusText)
        parent.addView(editor)
        space(parent, 12)
        parent.addView(label(tr("Alarm mulai dan pulang dijadwalkan otomatis. Untuk shift malam, alarm pulang mengikuti tanggal berikutnya.", "Start and finish alarms are scheduled automatically. For night shifts, the finish alarm follows the next calendar day."), 11f, secondaryText, false))
    }

    private fun timeBox(title: String, time: String, action: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(8),dp(8),dp(8),dp(8)); background = rounded(if (darkMode) Color.rgb(27,45,66) else Color.rgb(232,240,247), 14)
            isClickable = true; isFocusable = true; setOnClickListener { action() }
        }
        box.addView(label(title, 10f, secondaryText, true))
        val valueLabel = label(time, 23f, accent, true)
        if (title == tr("JAM MULAI", "START TIME")) startTimeValue = valueLabel else endTimeValue = valueLabel
        box.addView(valueLabel)
        box.addView(label(tr("Ketuk untuk ubah", "Tap to edit"), 9f, secondaryText, false))
        return box
    }

    private fun buildSummary(parent: LinearLayout) {
        parent.addView(label(tr("RINGKASAN JADWAL", "SCHEDULE SUMMARY"), 22f, primaryText, true))
        parent.addView(label(tr("Pantau seluruh jadwal dinas dalam satu tempat.", "Review all your shifts in one place."), 12f, secondaryText, false))
        space(parent, 16)
        val monthCard = card()
        val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        row.addView(actionButton("‹", accent) { displayedMonth=displayedMonth.minusMonths(1); renderTab(1,true) }, LinearLayout.LayoutParams(dp(42),dp(38)))
        row.addView(label(displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale(if(language=="en")"en" else "id"))), 17f, primaryText, true).apply { gravity=Gravity.CENTER }, LinearLayout.LayoutParams(0,-2,1f))
        row.addView(actionButton("›", accent) { displayedMonth=displayedMonth.plusMonths(1); renderTab(1,true) }, LinearLayout.LayoutParams(dp(42),dp(38)))
        monthCard.addView(row)
        val entries = store.getAllShifts().filterKeys { YearMonth.from(it)==displayedMonth }.toSortedMap()
        val counts = entries.values.groupingBy { it }.eachCount()
        space(monthCard,12)
        monthCard.addView(label("${entries.size} ${tr("jadwal tersimpan bulan ini","saved shifts this month")}", 16f, accent, true))
        space(monthCard,6)
        monthCard.addView(label(if(entries.isEmpty()) tr("Belum ada jadwal pada bulan ini.","No shifts saved for this month.") else counts.entries.joinToString("   •   ") { "${it.key}: ${it.value}" }, 12f, secondaryText, false))
        space(monthCard,14)
        if (entries.isEmpty()) {
            monthCard.addView(label(tr("Jadwal yang kamu simpan akan muncul di sini.", "Your saved shifts will appear here."), 13f, secondaryText, false))
        } else entries.forEach { (date,code) ->
            val shift = ShiftDefinitions.find(code) ?: return@forEach
            val line = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(8),0,dp(8)) }
            val info = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            info.addView(label(date.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy",Locale(if(language=="en")"en" else "id"))),13f,primaryText,true))
            info.addView(label("${prefs.getAlarmTime(date,shift.startTime)}  →  ${prefs.getEndAlarmTime(date,shift.endTime)}",11f,secondaryText,false))
            line.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            line.addView(label(code,13f,accent,true))
            line.setOnClickListener { selectedDate=date; displayedMonth=YearMonth.from(date); currentTab=0; renderNavigation(); renderTab(0,true) }
            monthCard.addView(line)
            monthCard.addView(View(this).apply { setBackgroundColor(if(darkMode) Color.rgb(39,54,73) else Color.rgb(230,235,241)) }, LinearLayout.LayoutParams(-1,dp(1)))
        }
        parent.addView(monthCard)
        space(parent,12)
        parent.addView(label(tr("Ketuk salah satu jadwal untuk mengeditnya di Home.", "Tap a shift to edit it on Home."),11f,secondaryText,false))
    }

    private fun buildProfile(parent: LinearLayout) {
        parent.addView(label(tr("PROFILE", "PROFILE"), 22f, primaryText, true))
        parent.addView(label(tr("Atur pengalaman Shift Alarm sesuai preferensimu.", "Personalize your Shift Alarm experience."), 12f, secondaryText, false))
        space(parent,16)

        val appearance = card()
        appearance.addView(label(tr("TAMPILAN", "APPEARANCE"),13f,primaryText,true))
        space(appearance,10)
        appearance.addView(label(tr("Mode tampilan", "Appearance mode"),11f,secondaryText,true))
        val modes = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        modes.addView(actionButton("LIGHT",if(!darkMode)accent else Color.rgb(80,96,116)){darkMode=false;saveAppearance();renderTab(2,true);renderNavigation()},LinearLayout.LayoutParams(0,dp(42),1f))
        modes.addView(spaceView(6))
        modes.addView(actionButton("DARK",if(darkMode)accent else Color.rgb(80,96,116)){darkMode=true;saveAppearance();renderTab(2,true);renderNavigation()},LinearLayout.LayoutParams(0,dp(42),1f))
        appearance.addView(modes)
        space(appearance,12)
        appearance.addView(label(tr("Warna aksen", "Accent color"),11f,secondaryText,true))
        val palette = GridLayout(this).apply { columnCount=5 }
        for(i in accents.indices) {
            val swatch=View(this).apply { background=rounded(accents[i],12); isClickable=true; setOnClickListener { accentIndex=i;saveAppearance();renderTab(2,true);renderNavigation() } }
            palette.addView(swatch,GridLayout.LayoutParams().apply { width=0;height=dp(36);columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(dp(4),dp(5),dp(4),dp(5)) })
        }
        appearance.addView(palette)
        space(appearance,8)
        appearance.addView(actionButton(tr("WARNA KUSTOM (HEX)", "CUSTOM COLOR (HEX)"),accent){openCustomColorPicker()})
        space(appearance,8)
        appearance.addView(actionButton(tr("PILIH GAMBAR LATAR", "CHOOSE BACKGROUND IMAGE"),Color.rgb(61,91,125)){pickBackgroundImage()})
        appearance.addView(actionButton(tr("HAPUS GAMBAR LATAR", "REMOVE BACKGROUND IMAGE"),Color.rgb(100,112,130)){customBackground=null;getSharedPreferences("shift_alarm_appearance",MODE_PRIVATE).edit().remove("custom_background").apply();applyCustomBackground();renderTab(2,true)})
        parent.addView(appearance)
        space(parent,12)

        val languageCard=card()
        languageCard.addView(label(tr("BAHASA / LANGUAGE","LANGUAGE"),13f,primaryText,true))
        space(languageCard,8)
        val languageOptions= arrayOf("🇮🇩  Bahasa Indonesia","🇬🇧  English")
        languageCard.addView(Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,languageOptions)
            setSelection(if(language=="en")1 else 0)
            onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent:AdapterView<*>?){}
                override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){
                    val next=if(position==1)"en" else "id"
                    if(next!=language){language=next;saveAppearance();renderNavigation();renderTab(currentTab,true)}
                }
            }
        },LinearLayout.LayoutParams(-1,dp(48)))
        parent.addView(languageCard)
        space(parent,12)

        val sound=card()
        sound.addView(label(tr("SUARA ALARM","ALARM SOUND"),13f,primaryText,true))
        space(sound,8)
        val toneUri=prefs.getRingtoneUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val toneName=runCatching { toneUri?.let { RingtoneManager.getRingtone(this,it)?.getTitle(this) } }.getOrNull() ?: tr("Nada alarm bawaan","Default alarm tone")
        sound.addView(label("${tr("Nada dipilih","Selected tone")}: $toneName",12f,secondaryText,false))
        space(sound,8)
        val soundActions=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        soundActions.addView(actionButton(tr("PILIH NADA","CHOOSE TONE"),accent){pickAlarmTone()},LinearLayout.LayoutParams(0,dp(42),1f))
        soundActions.addView(spaceView(8))
        soundActions.addView(actionButton(if(previewRingtone?.isPlaying==true)tr("STOP PREVIEW","STOP PREVIEW") else tr("PRATINJAU ▶","PREVIEW ▶"),Color.rgb(61,91,125)){if(previewRingtone?.isPlaying==true)stopTonePreview() else previewAlarmTone()},LinearLayout.LayoutParams(0,dp(42),1f))
        sound.addView(soundActions)
        space(sound,10)
        val gradual=Switch(this).apply { text=tr("Volume alarm mengeras bertahap","Gradually increase alarm volume");setTextColor(primaryText);textSize=12f;isChecked=prefs.isGradualVolume();setOnCheckedChangeListener{_,v->prefs.setGradualVolume(v)} }
        sound.addView(gradual)
        sound.addView(actionButton(tr("DURASI VOLUME","VOLUME RAMP DURATION"),Color.rgb(61,91,125)){chooseRampDuration()})
        parent.addView(sound)
        space(parent,12)

        val reminder=card()
        reminder.addView(label(tr("TEKS PENGINGAT","REMINDER MESSAGE"),13f,primaryText,true))
        space(reminder,8)
        reminder.addView(label(prefs.getReminderText(),12f,secondaryText,false))
        space(reminder,8)
        reminder.addView(actionButton(tr("UBAH TEKS PENGINGAT","EDIT REMINDER TEXT"),accent){editReminderText()})
        reminder.addView(actionButton(tr("RESET KE TEKS DEFAULT","RESET DEFAULT MESSAGE"),Color.rgb(100,112,130)){
            prefs.resetReminderText();Toast.makeText(this,tr("Teks pengingat dikembalikan ke default","Reminder reset to default"),Toast.LENGTH_SHORT).show();renderTab(2,true)
        })
        parent.addView(reminder)
        space(parent,12)

        val about=card()
        about.addView(label(tr("TENTANG APLIKASI","ABOUT"),13f,primaryText,true))
        space(about,8)
        about.addView(label("Shift Alarm  •  Version 1.2.0\n${tr("Pembaruan: dua alarm mulai/pulang, pilihan bahasa, tampilan kustom, dan penyempurnaan pengalaman pengguna.","Updates: start/finish alarms, language options, custom appearance, and user experience improvements.")}",12f,secondaryText,false))
        parent.addView(about)
    }

    private fun openTimePicker(isEnd: Boolean) {
        val code=store.getShift(selectedDate) ?: ShiftDefinitions.all.getOrNull(shiftSpinner.selectedItemPosition)?.code ?: return
        val shift=ShiftDefinitions.find(code) ?: return
        val current=if(isEnd)prefs.getEndAlarmTime(selectedDate,shift.endTime) else prefs.getAlarmTime(selectedDate,shift.startTime)
        TimePickerDialog(this,{_,hour,minute->
            val time=LocalTime.of(hour,minute)
            if(isEnd)prefs.setEndAlarmTime(selectedDate,time) else prefs.setAlarmTime(selectedDate,time)
            if(store.getShift(selectedDate)!=null && scheduler.canScheduleExactAlarms()) runCatching{scheduler.scheduleShift(selectedDate,code)}
            Toast.makeText(this,if(isEnd)tr("Jam pulang disimpan","Finish time saved") else tr("Jam mulai disimpan","Start time saved"),Toast.LENGTH_SHORT).show()
            renderTab(0,true)
        },current.hour,current.minute,true).show()
    }

    private fun saveSchedule() {
        val shift=ShiftDefinitions.all.getOrNull(shiftSpinner.selectedItemPosition) ?: return
        val old=store.getShift(selectedDate)
        if(old!=null) scheduler.cancelShift(selectedDate,old)
        store.setShift(selectedDate,shift.code)
        try {
            if(scheduler.canScheduleExactAlarms()) {
                scheduler.scheduleShift(selectedDate,shift.code)
                Toast.makeText(this,tr("Jadwal dan dua alarm berhasil disimpan.","Schedule and both alarms saved."),Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this,tr("Jadwal tersimpan. Aktifkan izin alarm tepat.","Schedule saved. Enable exact alarm permission."),Toast.LENGTH_LONG).show()
            renderTab(0,true)
        } catch(e:Exception) { statusText.text="${tr("Penjadwalan gagal","Scheduling failed")}: ${e.message}" }
    }

    private fun confirmRemoveSchedule() {
        android.app.AlertDialog.Builder(this).setTitle(tr("Hapus jadwal?","Delete schedule?"))
            .setMessage(tr("Jadwal dan alarm mulai/pulang pada tanggal ini akan dihapus.","The schedule and its start/finish alarms for this date will be deleted."))
            .setNegativeButton(tr("Batal","Cancel"),null).setPositiveButton(tr("Hapus","Delete")){_,_->removeSchedule()}.show()
    }

    private fun removeSchedule() {
        val old=store.getShift(selectedDate)
        if(old==null){Toast.makeText(this,tr("Tidak ada jadwal pada tanggal ini.","No schedule on this date."),Toast.LENGTH_SHORT).show();return}
        scheduler.cancelShift(selectedDate,old)
        store.removeShift(selectedDate)
        prefs.clearAlarmTime(selectedDate)
        Toast.makeText(this,tr("Jadwal berhasil dihapus.","Schedule deleted."),Toast.LENGTH_SHORT).show()
        renderTab(0,true)
    }

    private fun openDatePicker() {
        DatePickerDialog(this,{_,y,m,d->selectedDate=LocalDate.of(y,m+1,d);displayedMonth=YearMonth.from(selectedDate);renderTab(0,true)},selectedDate.year,selectedDate.monthValue-1,selectedDate.dayOfMonth).show()
    }

    private fun pickAlarmTone() {
        val picker=Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,tr("Pilih Nada Alarm","Choose Alarm Tone"))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,prefs.getRingtoneUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(picker,2401)
    }

    private fun previewAlarmTone() {
        try {
            stopTonePreview()
            val uri=prefs.getRingtoneUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            previewRingtone=uri?.let{RingtoneManager.getRingtone(this,it)}
            if(previewRingtone==null){Toast.makeText(this,tr("Nada tidak tersedia","Tone unavailable"),Toast.LENGTH_SHORT).show();return}
            previewRingtone?.play()
            previewHandler.removeCallbacks(stopPreview)
            previewHandler.postDelayed(stopPreview,18000L)
            Toast.makeText(this,tr("Pratinjau diputar maksimal 18 detik","Preview plays for up to 18 seconds"),Toast.LENGTH_SHORT).show()
            renderTab(2,true)
        } catch(_:Exception){Toast.makeText(this,tr("Nada tidak dapat diputar","Unable to play tone"),Toast.LENGTH_LONG).show()}
    }

    private fun stopTonePreview() {
        previewHandler.removeCallbacks(stopPreview)
        runCatching{previewRingtone?.stop()}
        previewRingtone=null
    }

    private fun chooseRampDuration() {
        val values=intArrayOf(10,20,30,45,60,90,120)
        val labels=values.map{"$it seconds"}.toTypedArray()
        val selected=values.indexOf(prefs.getRampDurationSeconds()).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this).setTitle(tr("Durasi volume bertahap","Volume ramp duration"))
            .setSingleChoiceItems(labels,selected){dialog,which->prefs.setRampDurationSeconds(values[which]);dialog.dismiss()}.setNegativeButton(tr("Batal","Cancel"),null).show()
    }

    private fun editReminderText() {
        val input=EditText(this).apply { setText(prefs.getReminderText());minLines=3;maxLines=5;hint=tr("Gunakan {shift} untuk kode shift","Use {shift} for the shift code") }
        android.app.AlertDialog.Builder(this).setTitle(tr("Teks pengingat 30 menit","30-minute reminder text"))
            .setMessage(tr("Kamu bisa memakai {shift} agar kode shift otomatis ditampilkan.","You can use {shift} to insert the shift code automatically."))
            .setView(input).setNegativeButton(tr("Batal","Cancel"),null)
            .setNeutralButton(tr("Default","Default")){_,_->prefs.resetReminderText();renderTab(2,true)}
            .setPositiveButton(tr("Simpan","Save")){_,_->prefs.setReminderText(input.text.toString());renderTab(2,true)}.show()
    }

    private fun openCustomColorPicker() {
        val input=EditText(this).apply { setText(String.format("#%06X",0xFFFFFF and accent));hint="#24BEDC" }
        android.app.AlertDialog.Builder(this).setTitle(tr("Warna aksen kustom","Custom accent color"))
            .setMessage(tr("Masukkan kode warna HEX, contoh #24BEDC.","Enter a HEX color, e.g. #24BEDC."))
            .setView(input).setNegativeButton(tr("Batal","Cancel"),null).setPositiveButton(tr("Terapkan","Apply")){_,_->
                val parsed=runCatching{Color.parseColor(input.text.toString().trim())}.getOrNull()
                if(parsed==null)Toast.makeText(this,tr("Kode warna tidak valid","Invalid color code"),Toast.LENGTH_SHORT).show()
                else{customAccent=parsed;accentIndex=accents.size;saveAppearance();renderTab(2,true);renderNavigation()}
            }.show()
    }

    private fun pickBackgroundImage() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="image/*";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)}
        startActivityForResult(intent,2402)
    }

    private fun applyCustomBackground() {
        val uri=customBackground ?: return
        try {
            val image=android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(contentResolver,uri))
            image.alpha=34
            root.background=image
        }catch(_:Exception){customBackground=null;root.setBackgroundColor(backgroundColor)}
    }

    private fun saveAppearance() {
        getSharedPreferences("shift_alarm_appearance",MODE_PRIVATE).edit()
            .putBoolean("dark_mode",darkMode).putInt("accent_index",accentIndex)
            .putInt("custom_accent",customAccent).putString("language",language)
            .putString("custom_background",customBackground?.toString()).apply()
        root.setBackgroundColor(backgroundColor)
        applyCustomBackground()
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK)return
        if(requestCode==2401){
            @Suppress("DEPRECATION")
            val uri=data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
            prefs.setRingtoneUri(uri);renderTab(2,true)
        } else if(requestCode==2402){
            val uri=data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
                customBackground=uri
                saveAppearance()
                renderTab(currentTab,true)
            }catch(_:Exception){Toast.makeText(this,tr("Gambar tidak dapat digunakan","Unable to use this image"),Toast.LENGTH_LONG).show()}
        }
    }

    private fun requestNotificationPermission() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
    }

    private fun openExactAlarmSettings() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)try{startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")))}
        catch(_:Exception){Toast.makeText(this,tr("Tidak dapat membuka pengaturan alarm.","Unable to open alarm settings."),Toast.LENGTH_LONG).show()}
        else Toast.makeText(this,tr("Alarm tepat didukung perangkat ini.","Exact alarms are supported on this device."),Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if(::store.isInitialized && ::scheduler.isInitialized){
            store.getAllShifts().forEach{(date,code)->if(!date.isBefore(LocalDate.now())&&scheduler.canScheduleExactAlarms())runCatching{scheduler.scheduleShift(date,code)}}
            if(::root.isInitialized){renderNavigation();renderTab(currentTab,false)}
        }
    }

    override fun onPause(){stopTonePreview();super.onPause()}

    private fun shiftColor(code:String?):Int=when(code){
        "SF","SO"->Color.rgb(48,132,190);"PF","PO"->Color.rgb(37,153,121);"MF","MO"->Color.rgb(132,94,190);"R"->Color.rgb(190,137,55)
        else->if(darkMode)Color.rgb(31,46,67) else Color.rgb(226,233,241)
    }
    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(15),dp(15),dp(15),dp(15));background=rounded(cardColor,18);elevation=dp(2).toFloat()}
    private fun label(value:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{text=value;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD;setLineSpacing(dp(2).toFloat(),1f)}
    private fun actionButton(value:String,color:Int,action:()->Unit)=TextView(this).apply{
        text=value;textSize=10f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
        background=rounded(color,12);setPadding(dp(8),dp(8),dp(8),dp(8));isClickable=true;isFocusable=true
        setOnClickListener{animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction{animate().scaleX(1f).scaleY(1f).setDuration(90).start();action()}.start()}
    }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun space(parent:LinearLayout,height:Int){parent.addView(spaceView(height))}
    private fun spaceView(height:Int)=View(this).apply{layoutParams=LinearLayout.LayoutParams(1,dp(height))}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
