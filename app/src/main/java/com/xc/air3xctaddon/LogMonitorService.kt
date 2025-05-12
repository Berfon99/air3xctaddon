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
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import java.io.RandomAccessFile

class LogMonitorService : Service() {
    private val CHANNEL_ID = "LogMonitorServiceChannel"
    private val TAG = "LogMonitorService"
    private var fileObserver: FileObserver? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dao: EventConfigDao
    private var lastFileSize: Long = 0

    companion object {
        const val ACTION_LOG_FILE_STATUS = "com.xc.air3xctaddon.LOG_FILE_STATUS"
        const val EXTRA_LOG_FILE_NAME = "log_file_name"
        const val EXTRA_IS_OBSERVED = "is_observed"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "air3xctaddon-db"
        ).build()
        dao = db.eventConfigDao()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AIR3 XCT Addon")
            .setContentText("Monitoring XCTrack log")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        // Essayer le dossier principal
        val primaryLogDir = File(
            Environment.getExternalStorageDirectory().path,
            "Android/data/org.xcontest.XCTrack/files/Log"
        )
        monitorLogFile(primaryLogDir)
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
        Log.d(TAG, "Notification channel created")
    }

    private fun monitorLogFile(logDir: File) {
        Log.d(TAG, "Checking storage permission for directory: ${logDir.absolutePath}")
        val hasPermission = hasStoragePermission()
        Log.d(TAG, "Storage permission granted: $hasPermission")
        if (!hasPermission) {
            Log.w(TAG, "Storage permission not granted")
            broadcastLogFileStatus("unknown.log", false)
            scope.launch {
                delay(5000)
                monitorLogFile(logDir)
            }
            return
        }

        try {
            Log.d(TAG, "Attempting to access log directory: ${logDir.absolutePath}")
            Log.d(TAG, "Directory exists: ${logDir.exists()}, readable: ${logDir.canRead()}, writable: ${logDir.canWrite()}")
            if (!logDir.exists()) {
                Log.w(TAG, "Log directory does not exist: ${logDir.absolutePath}")
                broadcastLogFileStatus("unknown.log", false)
                scope.launch {
                    delay(5000)
                    monitorLogFile(logDir)
                }
                return
            }
            if (!logDir.canRead()) {
                Log.w(TAG, "Log directory not readable: ${logDir.absolutePath}")
                broadcastLogFileStatus("unknown.log", false)
                scope.launch {
                    delay(5000)
                    monitorLogFile(logDir)
                }
                return
            }

            val logFiles = logDir.listFiles { file ->
                file.extension == "log" || file.extension == "txt"
            }
            Log.d(TAG, "Files in log directory (${logDir.absolutePath}): ${logFiles?.map { it.name }?.joinToString() ?: "none"}")
            if (logFiles.isNullOrEmpty()) {
                Log.w(TAG, "No .log or .txt files found in directory: ${logDir.absolutePath}")
                broadcastLogFileStatus("unknown.log", false)
                scope.launch {
                    delay(5000)
                    monitorLogFile(logDir)
                }
                return
            }

            val logFile = logFiles.maxByOrNull { it.lastModified() }
            Log.d(TAG, "Selected log file: ${logFile?.absolutePath}, last modified: ${logFile?.lastModified()}, readable: ${logFile?.canRead()}")
            if (logFile == null || !logFile.canRead()) {
                Log.w(TAG, "Cannot read log file: ${logFile?.absolutePath}")
                broadcastLogFileStatus(logFile?.name ?: "unknown.log", false)
                scope.launch {
                    delay(5000)
                    monitorLogFile(logDir)
                }
                return
            }

            Log.d(TAG, "Starting to monitor log file: ${logFile.absolutePath}")
            broadcastLogFileStatus(logFile.name, true)
            lastFileSize = logFile.length()
            fileObserver = object : FileObserver(logFile.path, FileObserver.MODIFY) {
                override fun onEvent(event: Int, path: String?) {
                    if (event == FileObserver.MODIFY) {
                        Log.d(TAG, "Log file modified: $path")
                        scope.launch {
                            readLogFile(logFile)
                        }
                    }
                }
            }
            fileObserver?.startWatching()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException accessing log directory: ${logDir.absolutePath}", e)
            broadcastLogFileStatus("unknown.log", false)
            scope.launch {
                delay(5000)
                monitorLogFile(logDir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing log directory: ${logDir.absolutePath}", e)
            broadcastLogFileStatus("unknown.log", false)
            scope.launch {
                delay(5000)
                monitorLogFile(logDir)
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Checked storage permission: $hasPermission")
        return hasPermission
    }

    private fun broadcastLogFileStatus(fileName: String, isObserved: Boolean) {
        val intent = Intent(ACTION_LOG_FILE_STATUS)
        intent.putExtra(EXTRA_LOG_FILE_NAME, fileName)
        intent.putExtra(EXTRA_IS_OBSERVED, isObserved)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Broadcasted log file status: $fileName, isObserved: $isObserved")
    }

    private suspend fun readLogFile(logFile: File) {
        try {
            Log.d(TAG, "Attempting to read log file: ${logFile.absolutePath}, readable: ${logFile.canRead()}")
            if (!logFile.exists() || !logFile.canRead()) {
                Log.w(TAG, "Log file does not exist or is not readable: ${logFile.absolutePath}")
                broadcastLogFileStatus(logFile.name, false)
                return
            }

            val configs = dao.getAllConfigs().firstOrNull() ?: emptyList()
            Log.d(TAG, "Reading log file with ${configs.size} configs: ${configs.map { it.event.name }}")
            if (configs.isEmpty()) {
                Log.w(TAG, "No configurations found, skipping log read")
                return
            }

            RandomAccessFile(logFile, "r").use { raf ->
                if (lastFileSize > logFile.length()) {
                    Log.d(TAG, "File truncated or replaced, resetting position")
                    lastFileSize = 0
                }
                raf.seek(lastFileSize)
                val newLines = mutableListOf<String>()
                var line: String?
                while (raf.readLine().also { line = it } != null) {
                    newLines.add(line!!)
                }
                lastFileSize = raf.filePointer
                Log.d(TAG, "Read ${newLines.size} new lines")

                newLines.forEach { line ->
                    configs.forEach { config ->
                        val eventPattern = "[EventMapping] Event: ${config.event.name}"
                        if (line.contains(eventPattern)) {
                            Log.d(TAG, "Detected event: ${config.event.name} in line: $line")
                            playSound(config)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading log file: ${logFile.absolutePath}", e)
            broadcastLogFileStatus(logFile.name, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log file: ${logFile.absolutePath}", e)
            broadcastLogFileStatus(logFile.name, false)
        }
    }

    private fun playSound(config: EventConfig) {
        val soundFile = File(filesDir, "Sounds/${config.soundFile}")
        if (!soundFile.exists()) {
            Log.e(TAG, "Sound file does not exist: ${soundFile.absolutePath}")
            return
        }

        Log.d(TAG, "Playing sound: ${soundFile.name}, event: ${config.event.name}, volumeType: ${config.volumeType}, volumePercentage: ${config.volumePercentage}, playCount: ${config.playCount}")

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
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        fileObserver?.stopWatching()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}