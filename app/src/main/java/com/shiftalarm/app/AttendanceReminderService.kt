package com.shiftalarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo

/**
 * Plays the attendance reminder sound directly instead of relying on
 * notification-channel sound, which Android/Realme users can mute separately.
 * The notification remains visible, but its channel is intentionally silent
 * to prevent duplicate sounds.
 */
class AttendanceReminderService : Service() {
    private var player: MediaPlayer? = null
    private var fallbackAttempted = false

    companion object {
        private const val CHANNEL_ID = "shift_attendance_audio_v1"
        private const val NOTIFICATION_ID = 2031
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shiftCode = intent?.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: "Shift"
        val date = intent?.getStringExtra(AlarmScheduler.EXTRA_DATE).orEmpty()
        startAsForeground(buildNotification(shiftCode))
        playReminderSound()
        return START_NOT_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Pengingat Absen", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pemberitahuan persiapan dinas 30 menit sebelum alarm utama"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(shiftCode: String): Notification {
        val text = AlarmPreferences(this).getAttendanceReminderText()
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
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
    }

    private fun playReminderSound() {
        val settings = AlarmPreferences(this)
        val selected = settings.getAttendanceRingtoneUri()
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val uri = if (!fallbackAttempted) selected ?: defaultUri else defaultUri
        if (uri == null) {
            stopSelf()
            return
        }

        val next = MediaPlayer()
        player = next
        try {
            next.setDataSource(this, uri)
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
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
                    handlePlaybackFailure(selected)
                }
            }
            next.setOnCompletionListener {
                stopSelf()
            }
            next.setOnErrorListener { _, _, _ ->
                handlePlaybackFailure(selected)
                true
            }
            next.prepareAsync()
        } catch (error: Exception) {
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
            stopSelf()
        }
    }

    private fun stopPlayer() {
        player?.let { media ->
            runCatching { if (media.isPlaying) media.stop() }
            runCatching { media.release() }
        }
        player = null
    }

    override fun onDestroy() {
        stopPlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
