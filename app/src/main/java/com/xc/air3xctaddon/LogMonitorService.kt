package com.xc.air3xctaddon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LogMonitorService : Service() {
    private val CHANNEL_ID = "LogMonitorServiceChannel"
    private lateinit var fileObserver: FileObserver
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var dao: EventConfigDao

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "air3xctaddon-db"
        ).build()
        dao = db.eventConfigDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AIR3 XCT Addon")
            .setContentText("Monitoring XCTrack log")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        // Get today's log file
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val logDir = File(
            Environment.getExternalStorageDirectory().path,
            "Android/data/org.xcontest.XCTrack/files/Log"
        )
        val logFile = File(logDir, "$date.log")

        // Start monitoring
        monitorLogFile(logFile)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Log Monitor Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun monitorLogFile(logFile: File) {
        fileObserver = object : FileObserver(logFile.path, MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (event == MODIFY) {
                    scope.launch {
                        readLogFile(logFile)
                    }
                }
            }
        }
        fileObserver.startWatching()
    }

    private suspend fun readLogFile(logFile: File) {
        if (!logFile.exists()) return
        val configs = dao.getAllConfigs().firstOrNull() ?: emptyList()
        logFile.bufferedReader().useLines { lines ->
            lines.forEach { line: String ->
                configs.forEach { config ->
                    if (line.contains("[EventMapping] Event: ${config.event.name}")) {
                        playSound(config)
                    }
                }
            }
        }
    }

    private fun playSound(config: EventConfig) {
        val soundFile = File(filesDir, "Sounds/${config.soundFile}")
        if (!soundFile.exists()) return

        val mediaPlayer = MediaPlayer().apply {
            setDataSource(soundFile.path)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            prepare()
        }

        // Set volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        when (config.volumeType) {
            VolumeType.MAXIMUM -> mediaPlayer.setVolume(1f, 1f)
            VolumeType.SYSTEM -> mediaPlayer.setVolume(currentVolume.toFloat() / maxVolume, currentVolume.toFloat() / maxVolume)
            VolumeType.PERCENTAGE -> mediaPlayer.setVolume(config.volumePercentage / 100f, config.volumePercentage / 100f)
        }

        // Play sound multiple times
        repeat(config.playCount) {
            mediaPlayer.start()
            // Wait for sound to finish
            Thread.sleep(mediaPlayer.duration.toLong())
            mediaPlayer.seekTo(0)
        }
        mediaPlayer.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver.stopWatching()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}