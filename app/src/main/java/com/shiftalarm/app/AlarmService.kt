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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.pm.ServiceInfo

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val CHANNEL_ID = "shift_alarm_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shiftCode = intent?.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: "Shift"
        val date = intent?.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: ""

        val notification = buildNotification(shiftCode, date)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startAlarmSound()
        startVibration()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Utama Shift Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm utama jadwal dinas"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createActionPendingIntent(
        action: String,
        shiftCode: String,
        date: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(shiftCode: String, date: String): Notification {
        val screenIntent = Intent(this, AlarmScreenActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            10000,
            screenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = createActionPendingIntent(
            AlarmActionReceiver.ACTION_STOP, shiftCode, date, 10001
        )
        val snoozeIntent = createActionPendingIntent(
            AlarmActionReceiver.ACTION_SNOOZE, shiftCode, date, 10002
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SHIFT ALARM")
            .setContentText("Waktunya dinas $shiftCode")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopIntent)
            .addAction(android.R.drawable.ic_media_play, "SNOOZE 5 MENIT", snoozeIntent)
            .build()
    }

    private fun startAlarmSound() {
        stopAlarmSound()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmService, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 800, 500, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopAlarmSound()
        vibrator?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
