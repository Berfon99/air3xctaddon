package com.xc.air3xctaddon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.xc.air3xctaddon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class LogMonitorService : Service() {
    private lateinit var eventReceiver: BroadcastReceiver
    private lateinit var filter: IntentFilter
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var telegramBotHelper: TelegramBotHelper

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "LogMonitorServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        Log.d("LogMonitorService", getString(R.string.log_started_foreground))

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telegramBotHelper = TelegramBotHelper(BuildConfig.TELEGRAM_BOT_TOKEN, fusedLocationClient)

        eventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val event = it.action?.substringAfterLast(".")
                    Log.d("LogMonitorService", getString(R.string.log_received_event, event))
                    scope.launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val configDao = db.eventConfigDao()
                        val config = configDao.getAllConfigsSync().find { it.event == event }
                        if (config != null) {
                            when (config.taskType) {
                                "Sound" -> {
                                    Log.d("LogMonitorService", getString(R.string.log_found_config, event, config.taskData))
                                    if (!config.taskData.isNullOrEmpty()) {
                                        playSound(
                                            config.taskData,
                                            config.volumeType,
                                            config.volumePercentage,
                                            config.playCount
                                        )
                                    } else {
                                        Log.w("LogMonitorService", "No sound file specified for event: $event")
                                    }
                                }
                                "SendPosition" -> {
                                    Log.d("LogMonitorService", getString(R.string.log_found_config, event, config.taskData))
                                    // TODO: Implement SendPosition logic
                                    Log.d("LogMonitorService", "Sending position for event: $event")
                                }
                                "SendTelegramPosition" -> {
                                    Log.d("LogMonitorService", getString(R.string.log_found_config, event, config.taskData))
                                    if (config.telegramChatId?.isNotEmpty() == true) { // Fixed: Line 78
                                        telegramBotHelper.getCurrentLocation(
                                            onResult = { latitude, longitude ->
                                                telegramBotHelper.sendLiveLocation(
                                                    config.telegramChatId, // Fixed: Line 81
                                                    latitude,
                                                    longitude
                                                )
                                            },
                                            onError = { error ->
                                                Log.e("LogMonitorService", "Error getting location: $error")
                                            }
                                        )
                                    } else {
                                        Log.w("LogMonitorService", "No chat ID specified for event: $event")
                                    }
                                }
                                else -> {
                                    Log.w("LogMonitorService", getString(R.string.log_no_config, event))
                                }
                            }
                        } else {
                            Log.w("LogMonitorService", getString(R.string.log_no_config, event))
                        }
                    }
                }
            }
        }

        filter = IntentFilter().apply {
            addAction("org.xcontest.XCTrack.Event.TAKEOFF")
            addAction("org.xcontest.XCTrack.Event.LANDING")
            addAction("org.xcontest.XCTrack.Event.BATTERY50")
            addAction("org.xcontest.XCTrack.Event.BATTERY40")
            addAction("org.xcontest.XCTrack.Event.BATTERY30")
            addAction("org.xcontest.XCTrack.Event.BATTERY20")
            addAction("org.xcontest.XCTrack.Event.BATTERY10")
            addAction("org.xcontest.XCTrack.Event.BATTERY5")
            addAction("org.xcontest.XCTrack.Event.BATTERY_CHARGING")
            addAction("org.xcontest.XCTrack.Event.BATTERY_DISCHARGING")
            addAction("org.xcontest.XCTrack.Event.START_THERMALING")
            addAction("org.xcontest.XCTrack.Event.STOP_THERMALING")
            addAction("org.xcontest.XCTrack.Event.COMP_SSS_CROSSED")
            addAction("org.xcontest.XCTrack.Event.COMP_TURNPOINT_CROSSED")
            addAction("org.xcontest.XCTrack.Event.COMP_ESS_CROSSED")
            addAction("org.xcontest.XCTrack.Event.COMP_GOAL_CROSSED")
            addAction("org.xcontest.XCTrack.Event.SYSTEM_GPS_OK")
            addAction("org.xcontest.XCTrack.Event.AIRSPACE_CROSSED")
            addAction("org.xcontest.XCTrack.Event.AIRSPACE_RED_WARN")
            addAction("org.xcontest.XCTrack.Event.AIRSPACE_ORANGE_WARN")
        }

        registerReceiver(
            eventReceiver,
            filter,
            "org.xcontest.XCTrack.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            null,
            Context.RECEIVER_NOT_EXPORTED
        )
        Log.d("LogMonitorService", getString(R.string.log_registered_receiver))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun playSound(fileName: String, volumeType: VolumeType?, volumePercentage: Int?, playCount: Int?) {
        try {
            val soundsDir = File(getExternalFilesDir(null), "Sounds")
            val soundFilePath = File(soundsDir, fileName).absolutePath
            Log.d("LogMonitorService", "Playing sound: $soundFilePath, volumeType: $volumeType, volumePercentage: $volumePercentage%, playCount: $playCount")

            val volume = when (volumeType) {
                VolumeType.MAXIMUM -> 1.0f
                VolumeType.SYSTEM -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    if (maxVolume > 0) currentVolume / maxVolume else 1.0f
                }
                VolumeType.PERCENTAGE -> (volumePercentage ?: 100) / 100.0f
                null -> 1.0f
            }

            var currentPlayCount = 0
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(soundFilePath)
                prepare()
                setVolume(volume, volume)
                start()
                currentPlayCount++
                Log.d("LogMonitorService", "Started playback $currentPlayCount/$playCount for: $fileName")
            }

            mediaPlayer.setOnCompletionListener {
                if (currentPlayCount < (playCount ?: 1)) {
                    try {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(soundFilePath)
                        mediaPlayer.prepare()
                        mediaPlayer.setVolume(volume, volume)
                        mediaPlayer.start()
                        currentPlayCount++
                        Log.d("LogMonitorService", "Started playback $currentPlayCount/$playCount for: $fileName")
                    } catch (e: Exception) {
                        Log.e("LogMonitorService", "Error restarting playback $currentPlayCount/$playCount for: $fileName", e)
                        mediaPlayer.release()
                    }
                } else {
                    Log.d("LogMonitorService", "Playback completed $currentPlayCount/$playCount for: $fileName")
                    mediaPlayer.release()
                }
            }

            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.e("LogMonitorService", "MediaPlayer error: what=$what, extra=$extra")
                mediaPlayer.release()
                true
            }
        } catch (e: Exception) {
            Log.e("LogMonitorService", "Error playing sound file: $fileName", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LogMonitorService", getString(R.string.log_service_started))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(eventReceiver)
            Log.d("LogMonitorService", getString(R.string.log_unregistered_receiver))
        } catch (e: Exception) {
            Log.e("LogMonitorService", getString(R.string.log_error_unregistering), e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}