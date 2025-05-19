package com.xc.air3xctaddon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.xc.air3xctaddon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogMonitorService : Service() {
    private lateinit var eventReceiver: BroadcastReceiver
    private lateinit var filter: IntentFilter
    private val scope = CoroutineScope(Dispatchers.IO) // Coroutine scope for database queries

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "LogMonitorServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        // Create notification channel
        createNotificationChannel()

        // Start foreground service
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        Log.d("LogMonitorService", getString(R.string.log_started_foreground))

        // Initialize eventReceiver
        eventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val event = it.action?.substringAfterLast(".")
                    Log.d("LogMonitorService", getString(R.string.log_received_event, event))
                    // Process event in coroutine scope
                    scope.launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val configDao = db.eventConfigDao()
                        val config = configDao.getAllConfigsSync().find { it.event == event }
                        if (config != null) {
                            Log.d("LogMonitorService", getString(R.string.log_found_config, event, config.soundFile))
                            // TODO: Implement sound playback (e.g., MediaPlayer)
                        } else {
                            Log.w("LogMonitorService", getString(R.string.log_no_config, event))
                        }
                    }
                }
            }
        }

        // Initialize filter for XCTrack events
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
            // Add other events as needed
        }

        // Register receiver with RECEIVER_NOT_EXPORTED
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