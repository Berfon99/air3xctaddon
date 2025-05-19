package com.xc.air3xctaddon

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log

class LogMonitorService : Service() {
    private lateinit var eventReceiver: BroadcastReceiver
    private lateinit var filter: IntentFilter

    override fun onCreate() {
        super.onCreate()
        // Initialize eventReceiver
        eventReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val event = it.action?.substringAfterLast(".")
                    Log.d("LogMonitorService", "Received event: $event")
                    // Process event, e.g., trigger sound or update config
                    // Example: Interact with MainViewModel if needed
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
        Log.d("LogMonitorService", "Registered event receiver")
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