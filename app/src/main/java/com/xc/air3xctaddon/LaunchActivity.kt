package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import android.widget.Toast


class LaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("packageName")
        val configId = intent.getIntExtra("configId", -1)
        Log.d("LaunchActivity", "Started with packageName=$packageName, configId=$configId")

        if (packageName != null) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                    startActivity(launchIntent)
                    Log.d("LaunchActivity", "Launched app: $packageName")
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

        finish() // Immediately close to keep XCTrack in foreground
    }
}