package com.xc.air3xctaddon

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
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        Log.d("LogMonitorService", "Started foreground service")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        SettingsRepository.initialize(this) // Initialize singleton
        telegramBotHelper = TelegramBotHelper(this, BuildConfig.TELEGRAM_BOT_TOKEN, fusedLocationClient)

        eventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val extrasString = it.extras?.keySet()?.joinToString() ?: "none"
                    Log.d("LogMonitorService", "Received intent: action=${it.action}, extras=$extrasString")
                    val event = it.action?.removePrefix(getString(R.string.action_prefix))
                    val formatArgs = it.getSerializableExtra("formatArgs")
                    Log.d("LogMonitorService", "Received event: $event, formatArgs=$formatArgs")
                    if (event != null) {
                        scope.launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            val configDao = db.eventConfigDao()
                            val taskDao = db.taskDao()
                            val configs = configDao.getAllConfigsSync()
                                .filter { it.event.equals(event, ignoreCase = true) }
                                .sortedBy { it.position }
                            val tasks = taskDao.getAllTasksSync()
                            Log.d("LogMonitorService", "Found ${configs.size} configs for event $event: ${configs.map { "id=${it.id}, taskData=${it.taskData}, taskType=${it.taskType}" }}")
                            if (configs.isNotEmpty()) {
                                configs.forEach { config ->
                                    Log.d("LogMonitorService", "Found config for event $event: taskData=${config.taskData}, taskType=${config.taskType}, id=${config.id}, launchInBackground=${config.launchInBackground}")
                                    when (config.taskType) {
                                        "Sound" -> {
                                            if (!config.taskData.isNullOrEmpty()) {
                                                playSound(
                                                    config.taskData,
                                                    config.volumeType,
                                                    config.volumePercentage,
                                                    config.playCount
                                                )
                                            } else {
                                                Log.w("LogMonitorService", "No sound file specified for event $event, configId=${config.id}")
                                            }
                                        }
                                        "SendTelegramPosition" -> {
                                            if (!config.taskData.isNullOrEmpty()) {
                                                Log.d("LogMonitorService", "Sending Telegram position: chatId=${config.taskData}, configId=${config.id}")
                                                telegramBotHelper.getCurrentLocation(
                                                    onResult = { latitude, longitude ->
                                                        telegramBotHelper.sendLocationMessage(
                                                            chatId = config.taskData,
                                                            latitude = latitude,
                                                            longitude = longitude,
                                                            event = config.event
                                                        )
                                                        Log.d("LogMonitorService", "Sent Telegram position for event $event: lat=$latitude, lon=$longitude")
                                                    },
                                                    onError = { error ->
                                                        Log.e("LogMonitorService", "Error getting location for event $event, configId=${config.id}: $error")
                                                    }
                                                )
                                            } else {
                                                Log.w("LogMonitorService", "Invalid taskData for ${config.taskType}, configId=${config.id}")
                                            }
                                        }
                                        "LaunchApp" -> {
                                            val task = tasks.find { it.taskType == config.taskType && it.taskData == config.taskData }
                                            if (task != null) {
                                                Log.d("LogMonitorService", "Preparing to launch app: packageName=${config.taskData}, configId=${config.id}, launchInBackground=${config.launchInBackground}")
                                                val launchIntent = Intent(this@LogMonitorService, LaunchActivity::class.java).apply {
                                                    putExtra("packageName", config.taskData)
                                                    putExtra("configId", config.id)
                                                    putExtra("launchInBackground", config.launchInBackground)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                try {
                                                    startActivity(launchIntent)
                                                    Log.d("LogMonitorService", "Started LaunchActivity: packageName=${config.taskData}, configId=${config.id}, launchInBackground=${config.launchInBackground}")
                                                } catch (e: SecurityException) {
                                                    Log.e("LogMonitorService", "Failed to start LaunchActivity: packageName=${config.taskData}, configId=${config.id}, error=$e")
                                                }
                                            } else {
                                                Log.w("LogMonitorService", "No task found for type=${config.taskType}, taskData=${config.taskData}, event=$event, configId=${config.id}")
                                            }
                                        }
                                        "ZELLO_PTT" -> {
                                            try {
                                                val zelloDownIntent = Intent("com.zello.ptt.down").apply {
                                                    putExtra("com.zello.stayHidden", true)
                                                }
                                                sendBroadcast(zelloDownIntent)
                                                Log.d("LogMonitorService", "Sent Zello PTT down intent for event $event, configId=${config.id}")
                                                Thread.sleep(100) // Brief delay to mimic press duration
                                                val zelloUpIntent = Intent("com.zello.ptt.up").apply {
                                                    putExtra("com.zello.stayHidden", true)
                                                }
                                                sendBroadcast(zelloUpIntent)
                                                Log.d("LogMonitorService", "Sent Zello PTT up intent for event $event, configId=${config.id}")
                                            } catch (e: Exception) {
                                                Log.e("LogMonitorService", "Failed to send Zello PTT intents for event $event, configId=${config.id}", e)
                                            }
                                        }
                                        "SendTelegramMessage" -> {
                                            if (!config.taskData.isNullOrEmpty() && config.taskData.contains("|")) {
                                                val (chatId, message) = config.taskData.split("|", limit = 2)
                                                if (chatId.isNotEmpty() && message.isNotEmpty()) {
                                                    telegramBotHelper.sendMessage(
                                                        chatId = chatId,
                                                        message = message
                                                    )
                                                    Log.d("LogMonitorService", "Sent Telegram message for event $event: chatId=$chatId, message=$message")
                                                } else {
                                                    Log.e("LogMonitorService", "Invalid Telegram message config for event $event, configId=${config.id}")
                                                }
                                            } else {
                                                Log.e("LogMonitorService", "Invalid Telegram message format for event $event, configId=${config.id}: ${config.taskData ?: "null"}")
                                            }
                                        }
                                        else -> {
                                            Log.w("LogMonitorService", "Unknown task type: ${config.taskType ?: "null"} for event $event, configId=${config.id}")
                                        }
                                    }
                                }
                            } else {
                                Log.w("LogMonitorService", "No configs found for event: $event")
                            }
                        }
                    } else {
                        Log.w("LogMonitorService", "No event extracted from action: ${it.action ?: "null"}")
                    }
                }
            }
        }

        filter = IntentFilter().apply {
            addAction("${getString(R.string.action_prefix)}TAKEOFF")
            addAction("${getString(R.string.action_prefix)}LANDING")
            addAction("${getString(R.string.action_prefix)}BATTERY50")
            addAction("${getString(R.string.action_prefix)}BATTERY40")
            addAction("${getString(R.string.action_prefix)}BATTERY30")
            addAction("${getString(R.string.action_prefix)}BATTERY20")
            addAction("${getString(R.string.action_prefix)}BATTERY10")
            addAction("${getString(R.string.action_prefix)}BATTERY5")
            addAction("${getString(R.string.action_prefix)}BATTERY_CHARGING")
            addAction("${getString(R.string.action_prefix)}BATTERY_DISCHARGING")
            addAction("${getString(R.string.action_prefix)}START_THERMALING")
            addAction("${getString(R.string.action_prefix)}STOP_THERMALING")
            addAction("${getString(R.string.action_prefix)}COMP_SSS_CROSSED")
            addAction("${getString(R.string.action_prefix)}COMP_TURNPOINT_CROSSED")
            addAction("${getString(R.string.action_prefix)}COMP_ESS_CROSSED")
            addAction("${getString(R.string.action_prefix)}COMP_GOAL_CROSSED")
            addAction("${getString(R.string.action_prefix)}SYSTEM_GPS_OK")
            addAction("${getString(R.string.action_prefix)}AIRSPACE_CROSSED")
            addAction("${getString(R.string.action_prefix)}AIRSPACE_RED_WARN")
            addAction("${getString(R.string.action_prefix)}AIRSPACE_ORANGE_WARN")
            addAction("${getString(R.string.action_prefix)}AIRSPACE_CROSSED_SOON")
            addAction("${getString(R.string.action_prefix)}AIRSPACE_OBSTACLE")
            addAction("${getString(R.string.action_prefix)}LIVETRACK_MESSAGE")
            addAction("${getString(R.string.action_prefix)}LIVETRACK_ENABLED")
            addAction("${getString(R.string.action_prefix)}BUTTON_CLICK")
            addAction("${getString(R.string.action_prefix)}CALL_REJECTED")
            addAction("${getString(R.string.action_prefix)}COMP_TURNPOINT_PREV")
            addAction("${getString(R.string.action_prefix)}_LANDING_CONFIRMATION_NEEDED")
            addAction("${getString(R.string.action_prefix)}BT_OK")
            addAction("${getString(R.string.action_prefix)}BT_KO")
            addAction("${getString(R.string.action_prefix)}TEST")
            addAction("com.xc.air3xctaddon.EVENT")
        }

        registerReceiver(
            eventReceiver,
            filter,
            "org.xcontest.XCTrack.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            null,
            Context.RECEIVER_EXPORTED
        )
        Log.d("LogMonitorService", "Registered event receiver")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                getString(R.string.notification_channel_id),
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
            Log.d("LogMonitorService", "Playing sound: $soundFilePath, volumeType=${volumeType.toString()}, volumePercentage=${volumePercentage ?: 100}%, playCount=${playCount ?: 1}")

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
                Log.d("LogMonitorService", "Started playback $currentPlayCount/${playCount ?: 1} for: $fileName")
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
                        Log.d("LogMonitorService", "Started playback $currentPlayCount/${playCount ?: 1} for: $fileName")
                    } catch (e: Exception) {
                        Log.e("LogMonitorService", "Error restarting playback $currentPlayCount/${playCount ?: 1} for: $fileName", e)
                        mediaPlayer.release()
                    }
                } else {
                    Log.d("LogMonitorService", "Playback completed $currentPlayCount/${playCount ?: 1} for: $fileName")
                    mediaPlayer.release()
                }
            }

            mediaPlayer.setOnErrorListener { _, what, extra ->
                Log.e("LogMonitorService", "MediaPlayer error: what=$what, extra=$extra")
                mediaPlayer.release()
                true
            }
        } catch (e: Exception) {
            Log.e("LogMonitorService", "Error playing sound: $fileName", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LogMonitorService", "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(eventReceiver)
            Log.d("LogMonitorService", "Unregistered event receiver")
        } catch (e: Exception) {
            Log.e("LogMonitorService", "Error unregistering receiver", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}