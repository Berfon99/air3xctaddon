package com.xc.air3xctaddon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class XCTrackEventReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "XCTrackEventReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (!action.startsWith(EventConstants.ACTION_PREFIX)) return

        val event = action.substring(EventConstants.ACTION_PREFIX.length)
        // Handle formatArgs (likely String, String[], or Serializable)
        val formatArgs = intent.getSerializableExtra(EventConstants.EXTRA_FORMAT_ARGS)
        val formatArgsStr = formatArgs?.toString() ?: "null"
        Log.d(TAG, context.getString(R.string.log_xctrack_event, event, formatArgsStr))

        // Handle known events
        when (event) {
            "TAKEOFF" -> {
                // TODO: Add logic for TAKEOFF (e.g., update flight status)
                Log.d(TAG, context.getString(R.string.log_handling_event, event, formatArgsStr))
            }
            "LANDING" -> {
                // TODO: Add logic for LANDING
                Log.d(TAG, context.getString(R.string.log_handling_event, event, formatArgsStr))
            }
            "BATTERY20" -> {
                // TODO: Add logic for BATTERY20 (e.g., show low battery warning)
                Log.d(TAG, context.getString(R.string.log_handling_event, event, formatArgsStr))
            }
            "AIRSPACE_CROSSED_SOON" -> {
                // TODO: Add logic for AIRSPACE_CROSSED_SOON (e.g., alert pilot)
                Log.d(TAG, context.getString(R.string.log_handling_event, event, formatArgsStr))
            }
            "BATTERY50", "BATTERY40", "BATTERY30", "BATTERY10", "BATTERY5",
            "BATTERY_CHARGING", "BATTERY_DISCHARGING", "START_THERMALING",
            "STOP_THERMALING", "COMP_SSS_CROSSED", "COMP_TURNPOINT_CROSSED",
            "COMP_ESS_CROSSED", "COMP_GOAL_CROSSED", "SYSTEM_GPS_OK",
            "AIRSPACE_CROSSED", "AIRSPACE_RED_WARN", "AIRSPACE_ORANGE_WARN",
            "BT_OK", "BT_KO", "LIVETRACK_MESSAGE", "AIRSPACE_OBSTACLE",
            "CALL_REJECTED", "COMP_TURNPOINT_PREV", "LIVETRACK_ENABLED",
            "_LANDING_CONFIRMATION_NEEDED", "BUTTON_CLICK" -> {
                // TODO: Add logic for other known events
                Log.d(TAG, context.getString(R.string.log_handling_event, event, formatArgsStr))
            }
            "TEST" -> {
                // Handle TEST event for verification
                Log.d(TAG, context.getString(R.string.log_handling_event, event, formatArgsStr))
            }
            else -> {
                // Log new or unhandled events for future implementation
                Log.i(TAG, context.getString(R.string.log_new_event, event, formatArgsStr))
            }
        }
    }
}