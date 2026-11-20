package com.shiftalarm.app

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.media.RingtoneManager
import android.media.Ringtone
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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var store: ShiftScheduleStore
    private lateinit var scheduler: AlarmScheduler
    private lateinit var alarmPreferences: AlarmPreferences
    private var previewRingtone: Ringtone? = null
    private lateinit var content: LinearLayout
    private lateinit var monthLabel: TextView
    private lateinit var calendarGrid: GridLayout
    private lateinit var selectedDateLabel: TextView
    private lateinit var shiftSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var upcomingText: TextView
    private lateinit var scheduleSummary: TextView

    private var selectedDate = LocalDate.now()
    private var displayedMonth = YearMonth.now()
    private var darkMode = true
    private var accentIndex = 0
    private var wallpaperIndex = 0
    private var currentTab = "home"
    private var language = "id"

    private val accents = listOf(
        Color.rgb(36, 190, 220),   // Ocean
        Color.rgb(94, 132, 255),  // Royal blue
        Color.rgb(55, 190, 145),  // Emerald
        Color.rgb(184, 125, 255), // Violet
        Color.rgb(244, 132, 95),  // Coral
        Color.rgb(232, 177, 74),  // Amber
        Color.rgb(225, 96, 145),  // Rose
        Color.rgb(117, 174, 112), // Sage
        Color.rgb(89, 156, 190),  // Sky
        Color.rgb(174, 146, 111)  // Sand
    )

    private val wallpaperColors = listOf(
        Color.rgb(9, 17, 31), Color.rgb(15, 28, 48), Color.rgb(25, 31, 53),
        Color.rgb(28, 38, 48), Color.rgb(37, 29, 45), Color.rgb(241, 245, 250),
        Color.rgb(233, 241, 248), Color.rgb(245, 240, 231), Color.rgb(238, 243, 237),
        Color.rgb(246, 237, 240)
    )
    private val wallpaperNames = listOf(
        "Midnight", "Deep Ocean", "Indigo Night", "Forest Night", "Plum Night",
        "Cloud", "Ice", "Warm Sand", "Sage Mist", "Soft Rose"
    )
    private val backgroundColor: Int
        get() = wallpaperColors[wallpaperIndex.coerceIn(0, wallpaperColors.lastIndex)]
    private val cardColor: Int
        get() = if (darkMode) Color.rgb(20, 32, 51) else Color.WHITE
    private val primaryText: Int
        get() = if (darkMode) Color.rgb(242, 247, 255) else Color.rgb(24, 38, 58)
    private val secondaryText: Int
        get() = if (darkMode) Color.rgb(157, 176, 201) else Color.rgb(103, 119, 141)
    private val accent: Int
        get() = accents[accentIndex]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ShiftScheduleStore(this)
        scheduler = AlarmScheduler(this)
        alarmPreferences = AlarmPreferences(this)
        val prefs = getSharedPreferences("shift_alarm_appearance", MODE_PRIVATE)
        darkMode = prefs.getBoolean("dark_mode", true)
        accentIndex = prefs.getInt("accent_index", 0).coerceIn(accents.indices)
        wallpaperIndex = prefs.getInt("wallpaper_index", if (darkMode) 0 else 5).coerceIn(wallpaperColors.indices)
        language = prefs.getString("language", "id") ?: "id"
        showPremiumSplash()
        Handler(Looper.getMainLooper()).postDelayed({
            buildDashboard()
            requestNotificationPermission()
        }, 900)
    }

    private fun showPremiumSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(backgroundColor)
        }
        val mark = TextView(this).apply {
            text = "SA"
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accent, 26)
            elevation = dp(8).toFloat()
        }
        splash.addView(mark, LinearLayout.LayoutParams(dp(88), dp(88)))
        splash.addView(label("SHIFT ALARM", 25f, primaryText, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22) })
        splash.addView(label("SMART SHIFT SCHEDULE & ALARM", 10f, secondaryText, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        setContentView(splash)
    }

    private fun tr(indonesian: String, english: String): String =
        if (language == "en") english else indonesian

    private fun currentLocale(): Locale =
        if (language == "en") Locale.ENGLISH else Locale("id", "ID")

    private fun toggleAppearanceMode() {
        darkMode = !darkMode
        val palette = wallpaperIndex % 5
        wallpaperIndex = if (darkMode) palette else palette + 5
        saveAppearance()
        renderDashboard()
    }

    private fun buildDashboard() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            isFillViewport = true
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(30))
        }
        scroll.addView(content)
        setContentView(scroll)
        renderDashboard()
    }

    private fun renderDashboard() {
        content.removeAllViews()
        content.setBackgroundColor(backgroundColor)
        if (currentTab == "summary") {
            renderSummaryScreen()
            addBottomNavigation()
            return
        }
        if (currentTab == "profile") {
            renderProfileScreen()
            addBottomNavigation()
            return
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("SHIFT ALARM", 23f, primaryText, true))
        brand.addView(label("SMART SHIFT SCHEDULE & ALARM", 10f, secondaryText, true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val appearance = actionButton(if (darkMode) "☼" else "☾", accent) {
            toggleAppearanceMode()
        }
        header.addView(appearance, LinearLayout.LayoutParams(dp(48), dp(44)))
        content.addView(header)
        addSpace(18)

        val hero = card()
        hero.addView(label("DASHBOARD", 11f, accent, true))
        addSpaceTo(hero, 8)
        hero.addView(label("Atur dinas,\nbiarkan alarm mengingatkan.", 23f, primaryText, true))
        addSpaceTo(hero, 8)
        hero.addView(label("Jadwal tersimpan otomatis menjadi pengingat 30 menit dan alarm utama.", 13f, secondaryText, false))
        addSpaceTo(hero, 14)
        val heroActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heroActions.addView(actionButton("PILIH WARNA TEMA", accent) {
            openThemeSettings()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        heroActions.addView(spaceView(10))
        heroActions.addView(actionButton("IZIN ALARM", if (scheduler.canScheduleExactAlarms()) Color.rgb(43, 166, 124) else Color.rgb(205, 139, 48)) {
            openExactAlarmSettings()
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        hero.addView(heroActions)
        addSpaceTo(hero, 10)
        hero.addView(actionButton("PILIH WALLPAPER", Color.rgb(91, 105, 132)) { openWallpaperSettings() })
        addSpaceTo(hero, 8)
        hero.addView(actionButton("PENGATURAN SUARA ALARM", Color.rgb(61, 91, 125)) { openSoundSettings() })
        addSpaceTo(hero, 8)
        hero.addView(actionButton("PENGATURAN PENGINGAT ABSEN", Color.rgb(61, 91, 125)) { openAttendanceReminderSettings() })
        content.addView(hero)
        addSpace(16)

        val nextCard = card()
        nextCard.addView(label("DINAS BERIKUTNYA", 11f, accent, true))
        addSpaceTo(nextCard, 8)
        upcomingText = label("", 18f, primaryText, true)
        nextCard.addView(upcomingText)
        addSpaceTo(nextCard, 5)
        nextCard.addView(label("Pengingat persiapan otomatis 30 menit sebelum alarm utama.", 12f, secondaryText, false))
        content.addView(nextCard)
        updateUpcoming()
        addSpace(18)

        val calendarCard = card()
        val calendarHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        calendarHeader.addView(label("KALENDER DINAS", 13f, primaryText, true), LinearLayout.LayoutParams(0, -2, 1f))
        calendarHeader.addView(actionButton("‹", accent) {
            displayedMonth = displayedMonth.minusMonths(1)
            renderDashboard()
        }, LinearLayout.LayoutParams(dp(42), dp(38)))
        calendarHeader.addView(spaceView(6))
        calendarHeader.addView(actionButton("›", accent) {
            displayedMonth = displayedMonth.plusMonths(1)
            renderDashboard()
        }, LinearLayout.LayoutParams(dp(42), dp(38)))
        calendarCard.addView(calendarHeader)
        addSpaceTo(calendarCard, 12)
        monthLabel = label("", 17f, primaryText, true)
        calendarCard.addView(monthLabel)
        addSpaceTo(calendarCard, 10)
        calendarGrid = GridLayout(this).apply { columnCount = 7; alignmentMode = GridLayout.ALIGN_BOUNDS }
        calendarCard.addView(calendarGrid)
        addSpaceTo(calendarCard, 10)
        calendarCard.addView(label("Warna menandai jenis shift. Ketuk tanggal untuk memilih atau mengubah jadwal.", 11f, secondaryText, false))
        content.addView(calendarCard)
        renderCalendar()
        addSpace(18)

        val editor = card()
        editor.addView(label("ATUR JADWAL DINAS", 13f, primaryText, true))
        addSpaceTo(editor, 12)
        selectedDateLabel = label("", 16f, accent, true)
        editor.addView(selectedDateLabel)
        addSpaceTo(editor, 10)
        editor.addView(actionButton("PILIH TANGGAL LAIN", accent) { openDatePicker() })
        addSpaceTo(editor, 12)
        editor.addView(label("Pilih kode shift", 12f, secondaryText, true))
        addSpaceTo(editor, 6)
        shiftSpinner = Spinner(this)
        val options = ShiftDefinitions.all.map { "${it.code}   •   ${it.startTime} – ${it.endTime}" }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = super.getView(position, convertView, parent) as TextView
                item.setTextColor(primaryText)
                item.textSize = 13f
                item.setPadding(dp(12), dp(8), dp(12), dp(8))
                return item
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = super.getDropDownView(position, convertView, parent) as TextView
                item.setTextColor(primaryText)
                item.setTextSize(14f)
                item.setPadding(dp(14), dp(12), dp(14), dp(12))
                item.setBackgroundColor(cardColor)
                return item
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        shiftSpinner.adapter = adapter
        val existing = store.getShift(selectedDate)
        val selectedIndex = ShiftDefinitions.all.indexOfFirst { it.code == existing }
        if (selectedIndex >= 0) shiftSpinner.setSelection(selectedIndex)
        editor.addView(shiftSpinner, LinearLayout.LayoutParams(-1, dp(48)))
        addSpaceTo(editor, 8)
        editor.addView(label("Pengaturan jam khusus tanggal ini", 11f, secondaryText, true))
        addSpaceTo(editor, 6)
        val alarmTimeActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        alarmTimeActions.addView(actionButton("ATUR JAM MULAI", accent) { openAlarmTimePicker(AlarmScheduler.ALARM_TYPE_START) },
            LinearLayout.LayoutParams(0, dp(44), 1f))
        alarmTimeActions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        alarmTimeActions.addView(actionButton("ATUR JAM PULANG", Color.rgb(61, 91, 125)) { openAlarmTimePicker(AlarmScheduler.ALARM_TYPE_END) },
            LinearLayout.LayoutParams(0, dp(44), 1f))
        editor.addView(alarmTimeActions)
        addSpaceTo(editor, 12)
        editor.addView(actionButton("SIMPAN JADWAL", accent) { saveSchedule() })
        addSpaceTo(editor, 8)
        editor.addView(actionButton("HAPUS JADWAL TANGGAL INI", Color.rgb(190, 75, 87)) { removeSchedule() })
        addSpaceTo(editor, 12)
        statusText = label("", 12f, secondaryText, false)
        editor.addView(statusText)
        content.addView(editor)
        refreshStatus()
        addSpace(18)

        val summary = card()
        summary.addView(label("RINGKASAN JADWAL", 13f, primaryText, true))
        addSpaceTo(summary, 8)
        scheduleSummary = label("", 13f, secondaryText, false)
        summary.addView(scheduleSummary)
        content.addView(summary)
        updateScheduleSummary()

        addSpace(20)
        val footer = label("SHIFT ALARM  •  © 2026 mahendradba", 10f, secondaryText, true)
        footer.gravity = Gravity.CENTER
        content.addView(footer)
        addBottomNavigation()
    }

    private fun renderSummaryScreen() {
        val header = card()
        header.addView(label("RINGKASAN JADWAL", 12f, accent, true))
        addSpaceTo(header, 8)
        header.addView(label("Rekap seluruh jadwal dinas yang tersimpan.", 22f, primaryText, true))
        content.addView(header)
        addSpace(14)

        val schedules = store.getAllShifts().toSortedMap()
        val summaryCard = card()
        summaryCard.addView(label(tr("TOTAL JADWAL", "TOTAL SCHEDULES"), 11f, accent, true))
        addSpaceTo(summaryCard, 6)
        summaryCard.addView(label(schedules.size.toString() + if (language == "en") " saved dates" else " tanggal tersimpan", 20f, primaryText, true))
        addSpaceTo(summaryCard, 12)
        if (schedules.isEmpty()) {
            summaryCard.addView(label(tr("Belum ada jadwal. Tambahkan jadwal melalui halaman Home.", "No schedules yet. Add one from the Home page."), 13f, secondaryText, false))
        } else {
            summaryCard.addView(label(tr("REKAP PER JENIS SHIFT", "SHIFTS BY TYPE"), 10f, secondaryText, true))
            addSpaceTo(summaryCard, 5)
            schedules.values.groupingBy { it }.eachCount().toSortedMap().forEach { (code, count) ->
                summaryCard.addView(label("$code   •   $count " + if (language == "en") "days" else "hari", 13f, primaryText, false))
            }
            addSpaceTo(summaryCard, 12)
            summaryCard.addView(label(tr("DAFTAR JADWAL", "SCHEDULE LIST"), 10f, secondaryText, true))
            schedules.forEach { (date, code) ->
                val shift = ShiftDefinitions.find(code)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(9), 0, dp(9))
                }
                val dateText = label(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", currentLocale())), 13f, primaryText, true)
                row.addView(dateText, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label(code, 13f, accent, true))
                row.addView(label("   " + (shift?.startTime ?: "--:--") + "–" + (shift?.endTime ?: "--:--"), 11f, secondaryText, false))
                row.setOnClickListener {
                    selectedDate = date
                    displayedMonth = YearMonth.from(date)
                    currentTab = "home"
                    renderDashboard()
                }
                summaryCard.addView(row)
                val line = View(this).apply { setBackgroundColor(if (darkMode) Color.rgb(43, 57, 76) else Color.rgb(229, 234, 241)) }
                summaryCard.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        content.addView(summaryCard)
        addSpace(14)
        content.addView(actionButton("KEMBALI KE HOME", accent) {
            currentTab = "home"
            renderDashboard()
        })
    }

    private fun renderProfileScreen() {
        val profile = card()
        profile.addView(label(tr("PROFILE & PENGATURAN", "PROFILE & SETTINGS"), 12f, accent, true))
        addSpaceTo(profile, 8)
        profile.addView(label("Shift Alarm", 24f, primaryText, true))
        addSpaceTo(profile, 4)
        profile.addView(label("Smart Shift Schedule & Alarm", 13f, secondaryText, false))
        addSpaceTo(profile, 16)
        profile.addView(label(tr("Tampilan", "Appearance"), 11f, accent, true))
        addSpaceTo(profile, 8)
        profile.addView(actionButton(if (darkMode) tr("GUNAKAN LIGHT MODE", "USE LIGHT MODE") else tr("GUNAKAN DARK MODE", "USE DARK MODE"), accent) {
            toggleAppearanceMode()
        })
        addSpaceTo(profile, 8)
        profile.addView(actionButton(tr("PILIH WARNA AKSEN", "CHOOSE ACCENT COLOR"), accent) { openThemeSettings() })
        addSpaceTo(profile, 8)
        profile.addView(actionButton(tr("PILIH WALLPAPER", "CHOOSE WALLPAPER"), Color.rgb(91, 105, 132)) { openWallpaperSettings() })
        addSpaceTo(profile, 16)
        profile.addView(label(tr("Bahasa", "Language"), 11f, accent, true))
        addSpaceTo(profile, 8)
        profile.addView(actionButton(if (language == "id") "BAHASA: INDONESIA" else "LANGUAGE: ENGLISH", accent) {
            val choices = arrayOf("Bahasa Indonesia", "English")
            android.app.AlertDialog.Builder(this)
                .setTitle(tr("Pilih Bahasa", "Choose Language"))
                .setSingleChoiceItems(choices, if (language == "id") 0 else 1) { dialog, index ->
                    language = if (index == 0) "id" else "en"
                    getSharedPreferences("shift_alarm_appearance", MODE_PRIVATE).edit().putString("language", language).apply()
                    dialog.dismiss()
                    renderDashboard()
                }
                .setNegativeButton(tr("Batal", "Cancel"), null)
                .show()
        })
        addSpaceTo(profile, 16)
        profile.addView(label(tr("Alarm & Notifikasi", "Alarm & Notifications"), 11f, accent, true))
        addSpaceTo(profile, 8)
        profile.addView(actionButton(tr("PENGATURAN SUARA ALARM", "ALARM SOUND SETTINGS"), Color.rgb(61, 91, 125)) { openSoundSettings() })
        addSpaceTo(profile, 8)
        profile.addView(actionButton(tr("PENGINGAT ABSEN", "ATTENDANCE REMINDER"), Color.rgb(61, 91, 125)) { openAttendanceReminderSettings() })
        addSpaceTo(profile, 8)
        profile.addView(actionButton(tr("IZIN ALARM TEPAT", "EXACT ALARM PERMISSION"), Color.rgb(61, 91, 125)) { openExactAlarmSettings() })
        content.addView(profile)
    }

    private fun addBottomNavigation() {
        addSpace(16)
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(cardColor, 18)
        }
        val tabs = listOf("home" to "HOME", "summary" to tr("RINGKASAN", "SUMMARY"), "profile" to tr("PROFIL", "PROFILE"))
        tabs.forEach { (key, title) ->
            val selected = currentTab == key
            val button = TextView(this).apply {
                text = title
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (selected) Color.WHITE else secondaryText)
                background = rounded(if (selected) accent else cardColor, 12)
                setPadding(dp(10), dp(13), dp(10), dp(13))
                setOnClickListener {
                    currentTab = key
                    renderDashboard()
                }
            }
            nav.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
        }
        content.addView(nav)
    }

    private fun renderCalendar() {
        monthLabel.text = displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", currentLocale()))
        calendarGrid.removeAllViews()
        val weekdays = if (language == "en") listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN") else listOf("SEN", "SEL", "RAB", "KAM", "JUM", "SAB", "MIN")
        weekdays.forEach { day ->
            val tv = label(day, 9f, secondaryText, true).apply { gravity = Gravity.CENTER }
            calendarGrid.addView(tv, GridLayout.LayoutParams().apply {
                width = 0; height = dp(32); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        val firstDayOffset = displayedMonth.atDay(1).dayOfWeek.value - 1
        repeat(firstDayOffset) {
            calendarGrid.addView(View(this), GridLayout.LayoutParams().apply {
                width = 0; height = dp(44); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        for (day in 1..displayedMonth.lengthOfMonth()) {
            val date = displayedMonth.atDay(day)
            val code = store.getShift(date)
            val isSelected = date == selectedDate
            val isToday = date == LocalDate.now()
            val cell = TextView(this).apply {
                text = if (code == null) day.toString() else "$day\n$code"
                textSize = if (code == null) 13f else 10f
                setTextColor(if (isSelected) Color.WHITE else if (code != null) Color.WHITE else primaryText)
                gravity = Gravity.CENTER
                typeface = if (isSelected || isToday || code != null) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = rounded(if (isSelected) accent else shiftColor(code), 10)
                setPadding(1, dp(3), 1, dp(3))
                setOnClickListener {
                    selectedDate = date
                    renderDashboard()
                }
            }
            calendarGrid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0; height = dp(44)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    private fun shiftColor(code: String?): Int {
        return when (code) {
            "SF", "SO" -> Color.rgb(48, 132, 190)
            "PF", "PO" -> Color.rgb(37, 153, 121)
            "MF", "MO" -> Color.rgb(132, 94, 190)
            "R" -> Color.rgb(190, 137, 55)
            else -> if (darkMode) Color.rgb(31, 46, 67) else Color.rgb(226, 233, 241)
        }
    }

    private fun updateUpcoming() {
        val upcoming = store.getAllShifts().entries
            .filter { (date, _) -> !date.isBefore(LocalDate.now()) }
            .sortedBy { it.key }
            .firstOrNull()
        upcomingText.text = if (upcoming == null) {
            "Belum ada jadwal tersimpan"
        } else {
            val shift = ShiftDefinitions.find(upcoming.value)
            "${upcoming.value}  •  ${upcoming.key.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("id", "ID")))}\nMulai ${shift?.startTime ?: "--:--"}"
        }
    }

    private fun updateScheduleSummary() {
        val schedules = store.getAllShifts().toSortedMap()
        scheduleSummary.text = if (schedules.isEmpty()) {
            "Belum ada jadwal. Pilih tanggal pada kalender lalu simpan shift."
        } else {
            schedules.entries.joinToString("\n") { (date, code) ->
                val shift = ShiftDefinitions.find(code)
                "${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID")))}   •   $code   •   ${shift?.startTime ?: ""}"
            }
        }
    }

    private fun saveSchedule() {
        val shift = ShiftDefinitions.all.getOrNull(shiftSpinner.selectedItemPosition) ?: return
        val oldCode = store.getShift(selectedDate)
        try {
            if (oldCode != null) scheduler.cancelShift(selectedDate, oldCode)
            store.setShift(selectedDate, shift.code)
            if (scheduler.canScheduleExactAlarms()) {
                scheduler.scheduleShift(selectedDate, shift.code)
                Toast.makeText(this, "Jadwal ${shift.code} tersimpan dan alarm dijadwalkan.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Jadwal tersimpan. Aktifkan izin alarm tepat.", Toast.LENGTH_LONG).show()
            }
            renderDashboard()
        } catch (e: Exception) {
            statusText.text = "Jadwal tersimpan, tetapi penjadwalan alarm gagal: ${e.message}"
        }
    }

    private fun removeSchedule() {
        val oldCode = store.getShift(selectedDate)
        if (oldCode == null) {
            Toast.makeText(this, "Tidak ada jadwal pada tanggal ini.", Toast.LENGTH_SHORT).show()
            return
        }
        scheduler.cancelShift(selectedDate, oldCode)
        store.removeShift(selectedDate)
        alarmPreferences.clearAlarmTime(selectedDate)
        Toast.makeText(this, "Jadwal berhasil dihapus.", Toast.LENGTH_SHORT).show()
        renderDashboard()
    }

    private fun refreshStatus() {
        statusText.text = if (scheduler.canScheduleExactAlarms()) {
            "● Izin alarm tepat aktif"
        } else {
            "● Izin alarm tepat belum aktif — tekan tombol IZIN ALARM di atas."
        }
    }

    private fun openAlarmTimePicker(alarmType: String) {
        val code = ShiftDefinitions.all.getOrNull(shiftSpinner.selectedItemPosition)?.code
            ?: store.getShift(selectedDate)
            ?: return
        val shift = ShiftDefinitions.find(code) ?: return
        val isStart = alarmType == AlarmScheduler.ALARM_TYPE_START
        val defaultTime = if (isStart) shift.startTime else shift.endTime
        val current = if (isStart) {
            alarmPreferences.getStartAlarmTime(selectedDate, defaultTime)
        } else {
            alarmPreferences.getEndAlarmTime(selectedDate, defaultTime)
        }
        val title = if (isStart) "Atur Jam Mulai" else "Atur Jam Pulang"
        TimePickerDialog(this, { _, hour, minute ->
            val selectedTime = java.time.LocalTime.of(hour, minute)
            if (isStart) alarmPreferences.setStartAlarmTime(selectedDate, selectedTime)
            else alarmPreferences.setEndAlarmTime(selectedDate, selectedTime)

            val savedCode = store.getShift(selectedDate)
            if (savedCode != null && scheduler.canScheduleExactAlarms()) {
                runCatching {
                    scheduler.cancelShift(selectedDate, savedCode)
                    scheduler.scheduleShift(selectedDate, savedCode)
                }.onFailure {
                    Toast.makeText(this, "Jam tersimpan, tetapi alarm perlu dijadwalkan ulang: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            Toast.makeText(this, "$title tanggal ini: ${selectedTime}", Toast.LENGTH_SHORT).show()
            renderDashboard()
        }, current.hour, current.minute, true).apply { setTitle(title) }.show()
    }

    private fun openThemeSettings() {
        val names = arrayOf(
            "Ocean Cyan", "Royal Blue", "Emerald", "Violet", "Coral",
            "Amber", "Rose", "Sage", "Sky", "Sand"
        )
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val choices = accents.indices.map { index ->
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(11), dp(10), dp(11))
                val swatch = View(this@MainActivity).apply {
                    background = rounded(accents[index], 24)
                }
                addView(swatch, LinearLayout.LayoutParams(dp(30), dp(30)))
                val title = label(names[index], 14f, primaryText, index == accentIndex)
                val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) }
                addView(title, lp)
                if (index == accentIndex) addView(label("AKTIF", 10f, accent, true))
                setOnClickListener {
                    accentIndex = index
                    saveAppearance()
                    renderDashboard()
                }
            }
        }
        choices.forEach { rows.addView(it) }
        android.app.AlertDialog.Builder(this)
            .setTitle("Warna Tema")
            .setView(ScrollView(this).apply { addView(rows) })
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun openWallpaperSettings() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(tr("Pilih Wallpaper", "Choose Wallpaper"))
            .setSingleChoiceItems(wallpaperNames.toTypedArray(), wallpaperIndex) { alert, index ->
                wallpaperIndex = index
                darkMode = index < 5
                saveAppearance()
                alert.dismiss()
                renderDashboard()
                Toast.makeText(this, "Wallpaper " + wallpaperNames[index] + " diterapkan", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .create()
        dialog.show()
    }

    private fun openSoundSettings() {
        val gradual = alarmPreferences.isGradualVolume()
        val duration = alarmPreferences.getRampDurationSeconds()
        val modeLabel = if (gradual) "Bertahap Mengeras" else "Langsung Kencang"
        val options = arrayOf(
            "Pilih nada alarm",
            "Pratinjau nada",
            "Mode suara: $modeLabel",
            "Durasi bertahap: $duration detik"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Pengaturan Suara Alarm")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pilih Nada Alarm")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                alarmPreferences.getRingtoneUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            )
                        }
                        @Suppress("DEPRECATION")
                        startActivityForResult(picker, 2401)
                    }
                    1 -> previewAlarmTone()
                    2 -> {
                        val choices = arrayOf("Langsung Kencang", "Bertahap Mengeras")
                        val selected = if (alarmPreferences.isGradualVolume()) 1 else 0
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Pilih Mode Volume")
                            .setSingleChoiceItems(choices, selected) { dialog, index ->
                                val useGradual = index == 1
                                alarmPreferences.setGradualVolume(useGradual)
                                dialog.dismiss()
                                Toast.makeText(
                                    this,
                                    if (useGradual) "Mode Bertahap Mengeras aktif" else "Mode Langsung Kencang aktif",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                    3 -> {
                        val labels = arrayOf("10 detik", "20 detik", "30 detik", "45 detik", "60 detik", "90 detik", "120 detik")
                        val values = intArrayOf(10, 20, 30, 45, 60, 90, 120)
                        val selected = values.indexOf(alarmPreferences.getRampDurationSeconds()).coerceAtLeast(0)
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Durasi hingga volume maksimal")
                            .setSingleChoiceItems(labels, selected) { dialog, index ->
                                alarmPreferences.setRampDurationSeconds(values[index])
                                dialog.dismiss()
                                Toast.makeText(this, "Durasi ${values[index]} detik disimpan", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Batal", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun openAttendanceReminderSettings() {
        val enabled = android.widget.CheckBox(this).apply {
            text = "Aktifkan pengingat 30 menit sebelum alarm mulai"
            setTextColor(primaryText)
            isChecked = alarmPreferences.isAttendanceReminderEnabled()
        }
        val messageInput = android.widget.EditText(this).apply {
            setText(alarmPreferences.getAttendanceReminderText())
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            hint = "Tulis pesan pengingat"
            minLines = 2
            maxLines = 4
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(if (darkMode) Color.rgb(31, 46, 67) else Color.rgb(235, 240, 247), 10)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(enabled)
            addView(label("Pesan notifikasi", 12f, secondaryText, true))
            addView(messageInput, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Pengingat Absen")
            .setView(layout)
            .setPositiveButton("Simpan", null)
            .setNeutralButton("Pulihkan Default", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                alarmPreferences.setAttendanceReminderEnabled(enabled.isChecked)
                alarmPreferences.setAttendanceReminderText(messageInput.text?.toString().orEmpty())
                if (scheduler.canScheduleExactAlarms()) {
                    store.getAllShifts().forEach { (date, code) ->
                        if (!date.isBefore(LocalDate.now())) runCatching { scheduler.scheduleShift(date, code) }
                    }
                }
                Toast.makeText(this, "Pengaturan pengingat disimpan", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                messageInput.setText(AlarmPreferences.DEFAULT_REMINDER_TEXT)
            }
        }
        dialog.show()
    }

    private fun previewAlarmTone() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Pratinjau Nada Alarm")
            .setMessage("Tekan Putar untuk mendengarkan nada yang dipilih. Gunakan Stop untuk menghentikannya.")
            .setPositiveButton("Putar", null)
            .setNeutralButton("Stop", null)
            .setNegativeButton("Tutup") { _, _ ->
                previewRingtone?.stop()
                previewRingtone = null
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    previewRingtone?.stop()
                    val uri = alarmPreferences.getRingtoneUri()
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    previewRingtone = uri?.let { RingtoneManager.getRingtone(this, it) }
                    if (previewRingtone != null) previewRingtone?.play()
                    else Toast.makeText(this, "Nada alarm tidak tersedia", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Nada tidak dapat diputar", Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                previewRingtone?.stop()
                previewRingtone = null
            }
        }
        dialog.setOnDismissListener {
            previewRingtone?.stop()
            previewRingtone = null
        }
        dialog.show()
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2401 && resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
            alarmPreferences.setRingtoneUri(uri)
            Toast.makeText(this, "Pilihan nada alarm disimpan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() { previewRingtone?.stop(); super.onPause() }
    private fun openDatePicker() {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day)
            displayedMonth = YearMonth.from(selectedDate)
            renderDashboard()
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                Toast.makeText(this, "Tidak dapat membuka pengaturan alarm.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Perangkat ini mendukung alarm tepat.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAppearance() {
        getSharedPreferences("shift_alarm_appearance", MODE_PRIVATE).edit()
            .putBoolean("dark_mode", darkMode)
            .putInt("accent_index", accentIndex)
            .putInt("wallpaper_index", wallpaperIndex)
            .putString("language", language)
            .apply()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized && ::store.isInitialized && ::scheduler.isInitialized) {
            store.getAllShifts().forEach { (date, code) ->
                if (!date.isBefore(LocalDate.now()) && scheduler.canScheduleExactAlarms()) {
                    runCatching { scheduler.scheduleShift(date, code) }
                }
            }
            renderDashboard()
        }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(cardColor, 18)
        elevation = dp(3).toFloat()
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(2).toFloat(), 1f)
        }

    private fun actionButton(textValue: String, color: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            text = textValue
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(color, 12)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun addSpace(height: Int) { content.addView(spaceView(height)) }
    private fun addSpaceTo(parent: LinearLayout, height: Int) { parent.addView(spaceView(height)) }
    private fun spaceView(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
