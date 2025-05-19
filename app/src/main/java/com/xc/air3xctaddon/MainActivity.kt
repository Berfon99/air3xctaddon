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
        private const val REQUEST_STORAGE_PERMISSION = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check storage permissions and copy files
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting WRITE_EXTERNAL_STORAGE permission")
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
        } else {
            copyAndVerifySoundFiles()
        }

        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
            }
        }

        requestNotificationPermissions()
    }

    private fun copyAndVerifySoundFiles() {
        try {
            // Copy sound files to external storage
            val externalSoundsDir = File(getExternalFilesDir(null), "Sounds")
            val success = assets.copySoundFilesFromAssets(externalSoundsDir)

            if (!success) {
                Log.e(TAG, "Failed to copy sound files")
                Toast.makeText(this, getString(R.string.sound_files_copy_failed), Toast.LENGTH_LONG).show()
                return
            }

            // Verify copied files
            val files = externalSoundsDir.listFiles()?.filter { it.isFile && it.canRead() }
            if (files == null || files.isEmpty()) {
                Log.e(TAG, "No files found in ${externalSoundsDir.absolutePath}")
                Toast.makeText(this, getString(R.string.sound_files_not_found), Toast.LENGTH_LONG).show()
                return
            }

            Log.d(TAG, "External sound directory (${externalSoundsDir.absolutePath}) contains: ${files.size} files")
            files.forEach {
                Log.d(TAG, "  - ${it.name} (${it.length()} bytes, readable: ${it.canRead()}, exists: ${it.exists()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up sound files", e)
            Toast.makeText(this, getString(R.string.sound_files_error), Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermissions() {
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
        when (requestCode) {
            REQUEST_NOTIFICATION_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "POST_NOTIFICATIONS permission granted")
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                    Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show()
                }
                startLogMonitorService()
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission granted")
                    copyAndVerifySoundFiles()
                } else {
                    Log.w(TAG, "WRITE_EXTERNAL_STORAGE permission denied")
                    Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startLogMonitorService() {
        val intent = Intent(this, LogMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d("MainActivity", "Started LogMonitorService")
    }
}