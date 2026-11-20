package com.shiftalarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo

/**
 * Plays the chosen attendance tone and leaves a visible, dismissible
 * notification after playback ends. The previous implementation stopped
 * the service on completion, which removed its foreground notification.
 */
class AttendanceReminderService : Service() {
    private var player: MediaPlayer? = null
    private var fallbackAttempted = false
    private var notificationDetached = false
    private var activeShiftCode = "Shift"
    private var currentNotificationId = 2031

    companion object {
        private const val TAG = "ShiftAttendanceReminder"
        private const val CHANNEL_ID = "shift_attendance_audio_v2"
        private const val NOTIFICATION_ID = 2031
        private const val EXTRA_NOTIFICATION_ID = "attendance_notification_id"
        const val ACTION_DISMISS = "com.shiftalarm.app.ACTION_DISMISS_ATTENDANCE_REMINDER"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentNotificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID) ?: NOTIFICATION_ID
        if (intent?.action == ACTION_DISMISS) {
            stopPlayer()
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(currentNotificationId)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        stopPlayer()
        fallbackAttempted = false
        notificationDetached = false
        activeShiftCode = intent?.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: "Shift"
        val reminderDate = intent?.getStringExtra(AlarmScheduler.EXTRA_DATE).orEmpty()
        currentNotificationId = (reminderDate + "-" + activeShiftCode).hashCode()
            .and(0x7fffffff).coerceAtLeast(NOTIFICATION_ID)
        startAsForeground(buildNotification(activeShiftCode, ongoing = true))
        playReminderSound()
        return START_NOT_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(currentNotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(currentNotificationId, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pengingat Absen", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pengingat persiapan dinas 30 menit sebelum alarm utama"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(shiftCode: String, ongoing: Boolean): Notification {
        val text = AlarmPreferences(this).getAttendanceReminderText()
        val dismissIntent = Intent(this, AttendanceReminderService::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIFICATION_ID, currentNotificationId)
        }
        val dismiss = PendingIntent.getService(
            this, currentNotificationId, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_HIGH)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Persiapan Dinas $shiftCode")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setOngoing(ongoing)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "TUTUP", dismiss)
            .build()
    }

    private fun playReminderSound() {
        val settings = AlarmPreferences(this)
        val selected = settings.getAttendanceRingtoneUri()
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val uri = if (!fallbackAttempted) selected ?: defaultUri else defaultUri
        if (uri == null) {
            finishPlayback()
            return
        }

        val next = MediaPlayer()
        player = next
        try {
            next.setDataSource(this, uri)
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            next.isLooping = false
            next.setVolume(1f, 1f)
            next.setOnPreparedListener { prepared ->
                if (player !== prepared) {
                    runCatching { prepared.release() }
                    return@setOnPreparedListener
                }
                runCatching { prepared.start() }.onFailure {
                    Log.e(TAG, "Unable to start selected reminder tone", it)
                    handlePlaybackFailure(selected)
                }
            }
            next.setOnCompletionListener { finishPlayback() }
            next.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Reminder playback error: $what/$extra")
                handlePlaybackFailure(selected)
                true
            }
            next.prepareAsync()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to prepare reminder tone", error)
            runCatching { next.release() }
            if (player === next) player = null
            handlePlaybackFailure(selected)
        }
    }

    private fun handlePlaybackFailure(selected: android.net.Uri?) {
        stopPlayer()
        if (!fallbackAttempted && selected != null) {
            fallbackAttempted = true
            playReminderSound()
        } else {
            finishPlayback()
        }
    }

    private fun finishPlayback() {
        stopPlayer()
        // Detach, rather than remove, the foreground notification. Update it
        // to a normal dismissible notification that remains in the shade.
        runCatching {
            stopForeground(STOP_FOREGROUND_DETACH)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(currentNotificationId, buildNotification(activeShiftCode, ongoing = false))
            notificationDetached = true
        }.onFailure { Log.e(TAG, "Unable to retain reminder notification", it) }
        stopSelf()
    }

    private fun stopPlayer() {
        player?.let { media ->
            runCatching { media.setOnCompletionListener(null) }
            runCatching { media.setOnErrorListener(null) }
            runCatching { if (media.isPlaying) media.stop() }
            runCatching { media.release() }
        }
        player = null
    }

    override fun onDestroy() {
        stopPlayer()
        if (!notificationDetached) stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

}
