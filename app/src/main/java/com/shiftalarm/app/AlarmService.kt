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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.pm.ServiceInfo

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val volumeHandler = Handler(Looper.getMainLooper())
    private var volumeStartedAt = 0L
    private var rampDurationMs = 30_000L
    private var gradualVolume = true
    private var activeKind = AlarmScheduler.KIND_START
    private var activeAlarmTime: String? = null
    private val volumeRamp = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            if (!player.isPlaying) return
            val fraction = ((SystemClock.elapsedRealtime() - volumeStartedAt).toFloat() / rampDurationMs).coerceIn(0f, 1f)
            val volume = if (gradualVolume) (0.08f + 0.92f * fraction) else 1f
            runCatching { player.setVolume(volume, volume) }
            if (gradualVolume && fraction < 1f) volumeHandler.postDelayed(this, 500L)
        }
    }

    companion object {
        const val CHANNEL_ID = "shift_alarm_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() { super.onCreate(); createNotificationChannel() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shiftCode = intent?.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CODE) ?: "Shift"
        val date = intent?.getStringExtra(AlarmScheduler.EXTRA_DATE) ?: ""
        activeAlarmTime = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME)
        activeKind = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_KIND) ?: AlarmScheduler.KIND_START
        val notification = buildNotification(shiftCode, date, activeAlarmTime, activeKind)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, notification)
        startAlarmSound()
        startVibration()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Alarm Utama Shift Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarm mulai dan pulang kerja"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createActionPendingIntent(action: String, shiftCode: String, date: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
            putExtra(AlarmActionReceiver.EXTRA_ACTION_ALARM_KIND, activeKind)
            putExtra(AlarmActionReceiver.EXTRA_ACTION_ALARM_TIME, activeAlarmTime)
        }
        return PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildNotification(shiftCode: String, date: String, alarmTime: String?, kind: String): Notification {
        val screenIntent = Intent(this, AlarmScreenActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_SHIFT_CODE, shiftCode)
            putExtra(AlarmScheduler.EXTRA_DATE, date)
            putExtra(AlarmScheduler.EXTRA_ALARM_TIME, alarmTime)
            putExtra(AlarmScheduler.EXTRA_ALARM_KIND, kind)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreen = PendingIntent.getActivity(this, 10000, screenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = createActionPendingIntent(AlarmActionReceiver.ACTION_STOP, shiftCode, date, 10001)
        val snooze = createActionPendingIntent(AlarmActionReceiver.ACTION_SNOOZE, shiftCode, date, 10002)
        val language = getSharedPreferences("shift_alarm_appearance", Context.MODE_PRIVATE).getString("language", "id") ?: "id"
        val label = if (language == "en") {
            if (kind == AlarmScheduler.KIND_END) "Time to finish work $shiftCode" else "Time to start work $shiftCode"
        } else if (kind == AlarmScheduler.KIND_END) "Waktunya pulang kerja $shiftCode" else "Waktunya mulai dinas $shiftCode"
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SHIFT ALARM")
            .setContentText(label)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true).setAutoCancel(false).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true).setContentIntent(fullScreen)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .addAction(android.R.drawable.ic_media_play, "Snooze 5 min", snooze).build()
    }

    private fun startAlarmSound() {
        stopAlarmSound()
        val prefs = AlarmPreferences(this)
        gradualVolume = prefs.isGradualVolume()
        rampDurationMs = prefs.getRampDurationSeconds() * 1000L
        val selected = prefs.getRingtoneUri()
        val uri = selected ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setDataSource(this, uri)
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            player.isLooping = true
            player.setVolume(if (gradualVolume) 0.08f else 1f, if (gradualVolume) 0.08f else 1f)
            player.setOnPreparedListener { prepared ->
                if (mediaPlayer !== prepared) { runCatching { prepared.release() }; return@setOnPreparedListener }
                runCatching {
                    prepared.start()
                    volumeStartedAt = SystemClock.elapsedRealtime()
                    if (gradualVolume) volumeHandler.post(volumeRamp)
                }.onFailure { if (mediaPlayer === prepared) mediaPlayer = null; runCatching { prepared.release() } }
            }
            player.setOnErrorListener { failed, _, _ ->
                if (mediaPlayer === failed) {
                    mediaPlayer = null; runCatching { failed.release() }
                    if (selected != null) { prefs.setRingtoneUri(null); startAlarmSound() }
                }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            if (mediaPlayer === player) mediaPlayer = null
            runCatching { player.release() }
            if (selected != null) { prefs.setRingtoneUri(null); startAlarmSound() } else e.printStackTrace()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else { @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator) }
        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 800, 500, 800, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else { @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0) }
        }
    }

    private fun stopAlarmSound() {
        volumeHandler.removeCallbacks(volumeRamp)
        mediaPlayer?.let { player -> try { if (player.isPlaying) player.stop(); player.release() } catch (_: Exception) {} }
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
