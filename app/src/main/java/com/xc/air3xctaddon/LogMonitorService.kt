package com.xc.air3xctaddon

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

class LogMonitorService : Service() {
    companion object {
        private const val TAG = "LogMonitorService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private lateinit var eventReceiver: BroadcastReceiver

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "Service already running, skipping")
            return START_STICKY
        }
        startEventMonitoring()
        return START_STICKY
    }

    private fun startEventMonitoring() {
        Log.d(TAG, "Starting event monitoring")
        eventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                if (action.startsWith("org.xcontest.XCTrack.Event.")) {
                    val event = action.removePrefix("org.xcontest.XCTrack.Event.")
                    val formatArgs = intent.getSerializableExtra("formatArgs")
                    handleEvent(event, formatArgs)
                }
            }
        }
        val filter = IntentFilter().apply {
            // Register all 32 XCTrack event actions
            addAction("org.xcontest.XCTrack.Event.TAKEOFF")
            addAction("org.xcontest.XCTrack.Event.LANDING")
            addAction("org.xcontest.XCTrack.Event.THERMAL_ENTER")
            addAction("org.xcontest.XCTrack.Event.THERMAL_EXIT")
            addAction("org.xcontest.XCTrack.Event.GLIDE_START")
            addAction("org.xcontest.XCTrack.Event.GLIDE_END")
            addAction("org.xcontest.XCTrack.Event.ALTITUDE_RECORD")
            addAction("org.xcontest.XCTrack.Event.SPEED_RECORD")
            addAction("org.xcontest.XCTrack.Event.TASK_START")
            addAction("org.xcontest.XCTrack.Event.TASK_FINISH")
            addAction("org.xcontest.XCTrack.Event.WAYPOINT_REACHED")
            addAction("org.xcontest.XCTrack.Event.TURNPOINT_REACHED")
            addAction("org.xcontest.XCTrack.Event.BATTERY75")
            addAction("org.xcontest.XCTrack.Event.BATTERY50")
            addAction("org.xcontest.XCTrack.Event.BATTERY25")
            addAction("org.xcontest.XCTrack.Event.BATTERY10")
            addAction("org.xcontest.XCTrack.Event.GPS_FIX")
            addAction("org.xcontest.XCTrack.Event.GPS_LOST")
            addAction("org.xcontest.XCTrack.Event.STARTUP")
            addAction("org.xcontest.XCTrack.Event.SHUTDOWN")
            addAction("org.xcontest.XCTrack.Event.BLUETOOTH_ON")
            addAction("org.xcontest.XCTrack.Event.BLUETOOTH_OFF")
            addAction("org.xcontest.XCTrack.Event.NETWORK_ON")
            addAction("org.xcontest.XCTrack.Event.NETWORK_OFF")
            addAction("org.xcontest.XCTrack.Event.FLIGHT_MODE_ON")
            addAction("org.xcontest.XCTrack.Event.FLIGHT_MODE_OFF")
            addAction("org.xcontest.XCTrack.Event.SENSOR_CONNECTED")
            addAction("org.xcontest.XCTrack.Event.SENSOR_DISCONNECTED")
            addAction("org.xcontest.XCTrack.Event.WARNING")
            addAction("org.xcontest.XCTrack.Event.ERROR")
            addAction("org.xcontest.XCTrack.Event.INFO")
            addAction("org.xcontest.XCTrack.Event.BUTTON_CLICK")
            addAction("org.xcontest.XCTrack.Event.TEST")
        }
        registerReceiver(eventReceiver, filter, RECEIVER_EXPORTED)
    }

    private fun handleEvent(event: String, formatArgs: Any?) {
        scope.launch {
            val configs = AppDatabase.getDatabase(applicationContext).eventConfigDao().getAllConfigsSync()
            val config = configs.find { it.event == event }
            if (config != null) {
                Log.d(TAG, "Playing sound for event: $event")
                playSound(config)
            } else {
                Log.d(TAG, "No sound configured for event: $event")
            }
        }
    }

    private fun playSound(config: EventConfig) {
        val soundFile = File(applicationContext.filesDir, "Sounds")
        if (!soundFile.exists()) {
            Log.e("LogMonitorService", "Sound file does not exist: ${soundFile.absolutePath}")
            Log.d("LogMonitorService", "Exists: ${soundFile.exists()}, Readable: ${soundFile.canRead()}, Size: ${soundFile.length()}")
            return
        }

        Log.d(TAG, "Playing sound: ${soundFile.name}, event: ${config.event}, volumeType: ${config.volumeType}, volumePercentage: ${config.volumePercentage}, playCount: ${config.playCount}")

        try {
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
            Log.e(TAG, "Error playing sound: ${soundFile.name}", e)
        }
    }
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        isRunning.set(false)
        unregisterReceiver(eventReceiver) // Use global Context
        scope.cancel()
        super.onDestroy()
    }
}