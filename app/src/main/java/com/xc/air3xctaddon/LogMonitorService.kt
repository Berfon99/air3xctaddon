package com.xc.air3xctaddon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LogMonitorService : Service() {
    companion object {
        private const val TAG = "LogMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "XCTrackAddonChannel"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private lateinit var eventReceiver: BroadcastReceiver

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "Service already running, skipping")
            return START_STICKY
        }
        startEventMonitoring()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "XCTrackAddonService"
            val descriptionText = "Service monitoring XCTrack events"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AIR3 XCTrack Addon")
            .setContentText("Monitoring XCTrack events")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startEventMonitoring() {
        Log.d(TAG, "Starting event monitoring")
        eventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action.startsWith(EventConstants.ACTION_PREFIX)) {
                    val event = action.removePrefix(EventConstants.ACTION_PREFIX)
                    val formatArgs = intent.getSerializableExtra(EventConstants.EXTRA_FORMAT_ARGS)
                    Log.d(TAG, "Received event: $event, formatArgs: $formatArgs")
                    handleEvent(event, formatArgs)
                }
            }
        }
        val filter = IntentFilter().apply {
            // Register all XCTrack event actions
            addAction("${EventConstants.ACTION_PREFIX}TAKEOFF")
            addAction("${EventConstants.ACTION_PREFIX}LANDING")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY50")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY40")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY30")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY20")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY10")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY5")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY_CHARGING")
            addAction("${EventConstants.ACTION_PREFIX}BATTERY_DISCHARGING")
            addAction("${EventConstants.ACTION_PREFIX}START_THERMALING")
            addAction("${EventConstants.ACTION_PREFIX}STOP_THERMALING")
            addAction("${EventConstants.ACTION_PREFIX}COMP_SSS_CROSSED")
            addAction("${EventConstants.ACTION_PREFIX}COMP_TURNPOINT_CROSSED")
            addAction("${EventConstants.ACTION_PREFIX}COMP_ESS_CROSSED")
            addAction("${EventConstants.ACTION_PREFIX}COMP_GOAL_CROSSED")
            addAction("${EventConstants.ACTION_PREFIX}SYSTEM_GPS_OK")
            addAction("${EventConstants.ACTION_PREFIX}AIRSPACE_CROSSED")
            addAction("${EventConstants.ACTION_PREFIX}AIRSPACE_RED_WARN")
            addAction("${EventConstants.ACTION_PREFIX}AIRSPACE_ORANGE_WARN")
            addAction("${EventConstants.ACTION_PREFIX}BT_OK")
            addAction("${EventConstants.ACTION_PREFIX}BT_KO")
            addAction("${EventConstants.ACTION_PREFIX}LIVETRACK_MESSAGE")
            addAction("${EventConstants.ACTION_PREFIX}AIRSPACE_CROSSED_SOON")
            addAction("${EventConstants.ACTION_PREFIX}AIRSPACE_OBSTACLE")
            addAction("${EventConstants.ACTION_PREFIX}CALL_REJECTED")
            addAction("${EventConstants.ACTION_PREFIX}COMP_TURNPOINT_PREV")
            addAction("${EventConstants.ACTION_PREFIX}LIVETRACK_ENABLED")
            addAction("${EventConstants.ACTION_PREFIX}TEST")
            addAction("${EventConstants.ACTION_PREFIX}_LANDING_CONFIRMATION_NEEDED")
            addAction("${EventConstants.ACTION_PREFIX}BUTTON_CLICK")
        }
        registerReceiver(eventReceiver, filter, "org.xcontest.XCTrack.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION", null)
        Log.d(TAG, "Event receiver registered")
    }

    private fun handleEvent(event: String, formatArgs: Any?) {
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(applicationContext).eventConfigDao()
                val configs = dao.getAllConfigsSync()
                Log.d(TAG, "Looking for event config: $event among ${configs.map { it.event }}")
                val config = configs.find { it.event == event }
                if (config != null) {
                    Log.d(TAG, "Found config for event: $event, soundFile: ${config.soundFile}")
                    playSound(config)
                } else {
                    Log.d(TAG, "No sound configured for event: $event")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling event: $event", e)
            }
        }
    }

    private fun playSound(config: EventConfig) {
        // Create the correct file path - use the actual sound file name from config
        val soundsDir = File(applicationContext.filesDir, "Sounds")
        val soundFile = File(soundsDir, config.soundFile)

        if (!soundFile.exists()) {
            // Check in external storage as well
            val extSoundsDir = File(applicationContext.getExternalFilesDir(null), "Sounds")
            val extSoundFile = File(extSoundsDir, config.soundFile)

            if (!extSoundFile.exists()) {
                Log.e(TAG, "Sound file does not exist: ${soundFile.absolutePath} or ${extSoundFile.absolutePath}")
                // Debug info about directories
                logDirectoryContents(soundsDir)
                logDirectoryContents(extSoundsDir)
                return
            } else {
                // Use the external file if it exists
                playMediaFile(extSoundFile, config)
                return
            }
        }

        playMediaFile(soundFile, config)
    }

    private fun logDirectoryContents(directory: File) {
        if (!directory.exists()) {
            Log.d(TAG, "Directory does not exist: ${directory.absolutePath}")
            return
        }

        val files = directory.listFiles()
        if (files == null || files.isEmpty()) {
            Log.d(TAG, "Directory is empty: ${directory.absolutePath}")
            return
        }

        Log.d(TAG, "Contents of ${directory.absolutePath}:")
        files.forEach { file ->
            Log.d(TAG, "  - ${file.name} (${file.length()} bytes, readable: ${file.canRead()})")
        }
    }

    private fun playMediaFile(soundFile: File, config: EventConfig) {
        Log.d(TAG, "Playing sound: ${soundFile.absolutePath}")
        Log.d(TAG, "Event: ${config.event}, volumeType: ${config.volumeType}, volumePercentage: ${config.volumePercentage}, playCount: ${config.playCount}")

        try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(soundFile.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                prepare()
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            when (config.volumeType) {
                VolumeType.MAXIMUM -> mediaPlayer.setVolume(1f, 1f)
                VolumeType.SYSTEM -> mediaPlayer.setVolume(currentVolume.toFloat() / maxVolume, currentVolume.toFloat() / maxVolume)
                VolumeType.PERCENTAGE -> mediaPlayer.setVolume(config.volumePercentage / 100f, config.volumePercentage / 100f)
            }

            var playCount = 0
            mediaPlayer.setOnCompletionListener {
                playCount++
                if (playCount < config.playCount) {
                    mediaPlayer.seekTo(0)
                    mediaPlayer.start()
                } else {
                    mediaPlayer.release()
                    Log.d(TAG, "Finished playing sound: ${soundFile.name}")
                }
            }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound: ${soundFile.absolutePath}", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        isRunning.set(false)
        try {
            unregisterReceiver(eventReceiver)
            Log.d(TAG, "Event receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        scope.cancel()
        super.onDestroy()
    }
}