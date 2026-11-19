
package com.shiftalarm.app

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.time.LocalDate

class MainActivity : Activity() {

    private lateinit var store: ShiftScheduleStore
    private lateinit var scheduler: AlarmScheduler

    private lateinit var dateText: TextView
    private lateinit var statusText: TextView
    private lateinit var scheduleList: TextView
    private lateinit var shiftSpinner: Spinner

    private var selectedDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = ShiftScheduleStore(this)
        scheduler = AlarmScheduler(this)

        createInterface()
        requestNotificationPermission()
        refreshSchedule()
    }

    private fun createInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "SHIFT ALARM"
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        }
        root.addView(title)

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        root.addView(statusText)

        val dateLabel = TextView(this).apply {
            text = "Tanggal Dinas"
            textSize = 17f
        }
        root.addView(dateLabel)

        dateText = TextView(this).apply {
            textSize = 18f
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(dateText)

        val dateButton = Button(this).apply {
            text = "PILIH TANGGAL"
            setOnClickListener {
                DatePickerDialog(
                    this@MainActivity,
                    { _, year, month, day ->
                        selectedDate = LocalDate.of(
                            year, month + 1, day
                        )
                        updateDateText()
                    },
                    selectedDate.year,
                    selectedDate.monthValue - 1,
                    selectedDate.dayOfMonth
                ).show()
            }
        }
        root.addView(dateButton)

        val shiftLabel = TextView(this).apply {
            text = "Pilih Shift"
            textSize = 17f
            setPadding(0, dp(20), 0, dp(8))
        }
        root.addView(shiftLabel)

        val shiftOptions = ShiftDefinitions.all.map {
            "${it.code}  |  ${it.startTime} - ${it.endTime}"
        }

        shiftSpinner = Spinner(this)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            shiftOptions
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        shiftSpinner.adapter = adapter
        root.addView(shiftSpinner)

        val saveButton = Button(this).apply {
            text = "SIMPAN JADWAL"
            setOnClickListener {
                saveSchedule()
            }
        }
        root.addView(saveButton)

        val removeButton = Button(this).apply {
            text = "HAPUS JADWAL TANGGAL INI"
            setOnClickListener {
                removeSchedule()
            }
        }
        root.addView(removeButton)

        val permissionButton = Button(this).apply {
            text = "AKTIFKAN IZIN ALARM"
            setOnClickListener {
                openExactAlarmSettings()
            }
        }
        root.addView(permissionButton)

        val listTitle = TextView(this).apply {
            text = "JADWAL TERSIMPAN"
            textSize = 20f
            setPadding(0, dp(24), 0, dp(12))
        }
        root.addView(listTitle)

        scheduleList = TextView(this).apply {
            textSize = 15f
            setPadding(dp(8), dp(8), dp(8), dp(20))
        }
        root.addView(scheduleList)

        updateDateText()

        setContentView(scroll)
    }

    private fun updateDateText() {
        dateText.text = selectedDate.toString()
    }

    private fun saveSchedule() {

        val shift = ShiftDefinitions.all[
            shiftSpinner.selectedItemPosition
        ]

        val oldCode = store.getShift(selectedDate)

        try {
            if (oldCode != null) {
                scheduler.cancelShift(selectedDate, oldCode)
            }

            store.setShift(selectedDate, shift.code)

            if (scheduler.canScheduleExactAlarms()) {
                scheduler.scheduleShift(
                    selectedDate,
                    shift.code
                )

                statusText.text =
                    "Jadwal ${shift.code} berhasil disimpan dan alarm dijadwalkan."
            } else {
                statusText.text =
                    "Jadwal tersimpan. Aktifkan izin alarm tepat untuk mengaktifkan pengingat."
            }

            refreshSchedule()

        } catch (e: Exception) {
            statusText.text =
                "Jadwal tersimpan, tetapi penjadwalan alarm gagal: ${e.message}"
        }
    }

    private fun removeSchedule() {

        val oldCode = store.getShift(selectedDate)

        if (oldCode == null) {
            Toast.makeText(
                this,
                "Tidak ada jadwal pada tanggal ini",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        scheduler.cancelShift(selectedDate, oldCode)
        store.removeShift(selectedDate)

        statusText.text = "Jadwal berhasil dihapus."

        refreshSchedule()
    }

    private fun refreshSchedule() {

        val schedules = store.getAllShifts().toSortedMap()

        scheduleList.text = if (schedules.isEmpty()) {
            "Belum ada jadwal tersimpan."
        } else {
            schedules.entries.joinToString("\n\n") { entry ->
                "${entry.key}  |  ${entry.value}"
            }
        }

        if (scheduler.canScheduleExactAlarms()) {
            statusText.text = "Izin alarm tepat: AKTIF"
        } else {
            statusText.text = "Izin alarm tepat: BELUM AKTIF"
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    private fun openExactAlarmSettings() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Tidak dapat membuka pengaturan alarm",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::statusText.isInitialized) {
            refreshSchedule()

            if (scheduler.canScheduleExactAlarms()) {
                store.getAllShifts().forEach { (date, code) ->
                    try {
                        if (!date.isBefore(LocalDate.now())) {
                            scheduler.scheduleShift(date, code)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
