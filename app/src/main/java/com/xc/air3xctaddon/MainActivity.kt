package com.xc.air3xctaddon

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.xc.air3xctaddon.ui.MainScreen
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.utils.copySoundFilesFromAssets
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import android.content.pm.PackageManager


class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val REQUEST_STORAGE_PERMISSION = 101
        private const val REQUEST_LOCATION_PERMISSION = 102
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var showOverlayDialog by mutableStateOf(false)

    private val systemAlertWindowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch {
            delay(1500) // Match delay from SettingsActivity/AddTaskActivity
            val canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
            Log.d(TAG, "SYSTEM_ALERT_WINDOW check: canDrawOverlays=$canDrawOverlays")
            if (canDrawOverlays) {
                Toast.makeText(this@MainActivity, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Overlay permission denied. If the app isn't listed in Settings, try reinstalling or use ADB: 'adb shell appops set com.xc.air3xctaddon SYSTEM_ALERT_WINDOW allow'",
                    Toast.LENGTH_LONG
                ).show()
            }
            startLogMonitorService() // Proceed after permission check
        }
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

        // Check location and notification permissions
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            Log.d(TAG, "ACCESS_FINE_LOCATION permission needed")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            Log.d(TAG, "POST_NOTIFICATIONS permission needed")
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: $permissionsToRequest")
            requestPermissions(
                permissionsToRequest.toTypedArray(),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            // Check SYSTEM_ALERT_WINDOW permission
            if (!Settings.canDrawOverlays(this)) {
                Log.d(TAG, "SYSTEM_ALERT_WINDOW permission needed")
                showOverlayDialog = true // Show dialog to explain and request
            } else {
                startLogMonitorService()
            }
        }

        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
                if (showOverlayDialog) {
                    OverlayPermissionDialog(
                        onConfirm = {
                            showOverlayDialog = false
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            Log.d(TAG, "Requesting SYSTEM_ALERT_WINDOW for package: $packageName")
                            systemAlertWindowLauncher.launch(intent)
                        },
                        onDismiss = {
                            showOverlayDialog = false
                            Toast.makeText(
                                this,
                                "Overlay permission is required to launch apps in the background",
                                Toast.LENGTH_LONG
                            ).show()
                            startLogMonitorService() // Proceed even if denied
                        }
                    )
                }
            }
        }
    }

    private fun copyAndVerifySoundFiles() {
        try {
            val externalSoundsDir = File(getExternalFilesDir(null), "Sounds")
            val success = assets.copySoundFilesFromAssets(externalSoundsDir)

            if (!success) {
                Log.e(TAG, "Failed to copy sound files")
                Toast.makeText(this, getString(R.string.sound_files_copy_failed), Toast.LENGTH_LONG).show()
                return
            }

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
                // Check SYSTEM_ALERT_WINDOW after notification permission
                if (!Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "SYSTEM_ALERT_WINDOW permission needed")
                    showOverlayDialog = true
                } else {
                    startLogMonitorService()
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission granted")
                    copyAndVerifySoundFiles()
                } else {
                    Log.w(TAG, "WRITE_EXTERNAL_STORAGE permission denied")
                    Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
                }
                // Check SYSTEM_ALERT_WINDOW after storage permission
                if (!Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "SYSTEM_ALERT_WINDOW permission needed")
                    showOverlayDialog = true
                } else {
                    startLogMonitorService()
                }
            }
            REQUEST_LOCATION_PERMISSION -> {
                var locationGranted = false
                var notificationGranted = true // Default to true if not requested
                for (i in permissions.indices) {
                    when (permissions[i]) {
                        Manifest.permission.ACCESS_FINE_LOCATION -> {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                Log.d(TAG, "ACCESS_FINE_LOCATION permission granted")
                                locationGranted = true
                            } else {
                                Log.w(TAG, "ACCESS_FINE_LOCATION permission denied")
                                Toast.makeText(this, "Location permission required for Telegram position", Toast.LENGTH_LONG).show()
                            }
                        }
                        Manifest.permission.POST_NOTIFICATIONS -> {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                Log.d(TAG, "POST_NOTIFICATIONS permission granted")
                                notificationGranted = true
                            } else {
                                Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                                Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show()
                                notificationGranted = false
                            }
                        }
                    }
                }
                // Check SYSTEM_ALERT_WINDOW after location/notification permissions
                if (!Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "SYSTEM_ALERT_WINDOW permission needed")
                    showOverlayDialog = true
                } else {
                    startLogMonitorService()
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

@Composable
fun OverlayPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Overlay Permission Required") },
        text = {
            Text(
                "This app needs permission to display over other apps to launch applications in the background while keeping XCTrack active. Please enable 'Display over other apps' in Settings."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Go to Settings")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}