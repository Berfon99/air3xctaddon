package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity

class LaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("packageName")
        val configId = intent.getIntExtra("configId", -1)
        val launchInBackground = intent.getBooleanExtra("launchInBackground", true)
        Log.d("LaunchActivity", "Started with packageName=$packageName, configId=$configId, launchInBackground=$launchInBackground")

        if (packageName != null) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (launchInBackground) {
                        // Launch in background: Minimize focus change
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(launchIntent)
                        Log.d("LaunchActivity", "Launched app in background: $packageName")
                        // Restore XCTrack to foreground
                        val xcTrackIntent = packageManager.getLaunchIntentForPackage("org.xcontest.XCTrack")
                        if (xcTrackIntent != null) {
                            xcTrackIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                            )
                            startActivity(xcTrackIntent)
                            Log.d("LaunchActivity", "Restored XCTrack to foreground")
                        } else {
                            Log.w("LaunchActivity", "No launch intent found for org.xcontest.XCTrack")
                        }
                    } else {
                        // Launch in foreground: Standard behavior
                        launchIntent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        )
                        startActivity(launchIntent)
                        Log.d("LaunchActivity", "Launched app in foreground: $packageName")
                    }
                } else {
                    Log.e("LaunchActivity", "No launch intent found for package: $packageName")
                    Toast.makeText(this, "Cannot launch app: $packageName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("LaunchActivity", "Failed to launch app: $packageName, error: ${e.message}")
                Toast.makeText(this, "Failed to launch app: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.e("LaunchActivity", "No packageName provided")
            Toast.makeText(this, "No app specified to launch", Toast.LENGTH_LONG).show()
        }

        finish() // Close immediately
    }
}