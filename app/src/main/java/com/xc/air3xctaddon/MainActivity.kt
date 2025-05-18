package com.xc.air3xctaddon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.xc.air3xctaddon.ui.MainScreen
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.utils.copySoundFilesFromAssets
import java.io.File

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the necessary sound file directories and copy assets
        if (!copyAndVerifySoundFiles()) {
            Toast.makeText(this, "Failed to copy sound files. Some features may not work.", Toast.LENGTH_LONG).show()
        }

        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
            }
        }

        requestPermissions()
    }

    private fun copyAndVerifySoundFiles(): Boolean {
        try {
            // Copy sound files from assets to internal storage
            val internalSoundsDir = File(filesDir, "Sounds")
            val success = assets.copySoundFilesFromAssets(internalSoundsDir)

            if (!success) {
                Log.e(TAG, "Failed to copy sound files")
                return false
            }

            // Verify copied files
            val files = internalSoundsDir.listFiles()
            Log.d(TAG, "Internal sound directory (${internalSoundsDir.absolutePath}) contains: ${files?.size ?: 0} files")
            files?.forEach {
                Log.d(TAG, "  - ${it.name} (${it.length()} bytes, readable: ${it.canRead()})")
            }

            return files?.isNotEmpty() == true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up sound files", e)
            return false
        }
    }

    private fun requestPermissions() {
        // Check for notification permissions on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting POST_NOTIFICATIONS permission")
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        } else {
            startLogMonitorService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted")
                startLogMonitorService()
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                Toast.makeText(this, "La permission de notification est nécessaire pour le bon fonctionnement de l'application.", Toast.LENGTH_LONG).show()
                // Start service anyway but it may not show notifications properly
                startLogMonitorService()
            }
        }
    }

    private fun startLogMonitorService() {
        val intent = Intent(this, LogMonitorService::class.java)

        // Start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Log.d(TAG, "LogMonitorService started")
    }
}