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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.xc.air3xctaddon.ui.MainScreen
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.utils.copySoundFilesFromAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit

// Define preference key
private val IS_AIR3_DEVICE = booleanPreferencesKey("is_air3_device")

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val REQUEST_STORAGE_PERMISSION = 101
        private const val REQUEST_LOCATION_PERMISSION = 102
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var showOverlayDialog by mutableStateOf(false)
    private var showXCTrackVersionDialog by mutableStateOf(false)

    private val systemAlertWindowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch {
            delay(1500) // Match delay from SettingsActivity/AddTaskActivity
            val canDrawOverlays = Settings.canDrawOverlays(this@MainActivity)
            Log.d(TAG, "SYSTEM_ALERT_WINDOW check: canDrawOverlays=$canDrawOverlays")
            if (canDrawOverlays) {
                Toast.makeText(this@MainActivity, getString(R.string.overlay_permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.overlay_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
            startLogMonitorService() // Proceed after permission check
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize DataStoreSingleton
        DataStoreSingleton.initialize(applicationContext)

        // Check XCTrack version code
        val xcTrackVersionCode = try {
            val packageInfo = packageManager.getPackageInfo("org.xcontest.XCTrack", 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            Log.d(TAG, "XCTrack detected, version code: $versionCode")
            versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "XCTrack not found: ${e.message}")
            -1L // Use -1 to indicate not installed
        } catch (e: Exception) {
            Log.e(TAG, "Error checking XCTrack: ${e.message}")
            -1L // Use -1 for errors
        }

        // Show dialog if XCTrack is installed and version code is < XC_TRACK_MIN_VERSION_CODE
        if (xcTrackVersionCode in 0 until Constants.XC_TRACK_MIN_VERSION_CODE) {
            showXCTrackVersionDialog = true
        } else {
            // Proceed with normal flow if XCTrack is not installed or version is OK
            proceedWithAppInitialization()
        }

        setContent {
            AIR3XCTAddonTheme {
                if (showXCTrackVersionDialog) {
                    XCTrackVersionDialog(
                        onConfirm = {
                            showXCTrackVersionDialog = false
                            finish() // Close the app
                        }
                    )
                } else {
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
                                    this@MainActivity,
                                    getString(R.string.overlay_permission_required),
                                    Toast.LENGTH_LONG
                                ).show()
                                startLogMonitorService() // Proceed even if denied
                            }
                        )
                    }
                }
            }
        }
    }

    private fun proceedWithAppInitialization() {
        // Save AIR³ status to DataStore
        scope.launch {
            DataStoreSingleton.getDataStore().edit { preferences ->
                preferences[IS_AIR3_DEVICE] = Build.BRAND == getString(R.string.brand_air3)
            }
            Log.d(TAG, "Saved AIR³ device status to DataStore: ${Build.BRAND == getString(R.string.brand_air3)}")
        }

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
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
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
    }

    private fun copyAndVerifySoundFiles() {
        try {
            val externalSoundsDir = File(getExternalFilesDir(null), "Sounds")
            val success = assets.copySoundFilesFromAssets(this@MainActivity, externalSoundsDir)

            if (!success) {
                Log.e(TAG, "Failed to copy sound files")
                Toast.makeText(this@MainActivity, R.string.sound_files_copy_failed, Toast.LENGTH_LONG).show()
                return
            }

            val files = externalSoundsDir.listFiles()?.filter { it.isFile && it.canRead() }
            if (files == null || files.isEmpty()) {
                Log.e(TAG, "No files found in ${externalSoundsDir.absolutePath}")
                Toast.makeText(this@MainActivity, R.string.sound_files_not_found, Toast.LENGTH_LONG).show()
                return
            }

            Log.d(TAG, "External sound directory (${externalSoundsDir.absolutePath}) contains: ${files.size} files")
            files.forEach {
                Log.d(TAG, "  - ${it.name} (${it.length()} bytes, readable: ${it.canRead()}, exists: ${it.exists()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up sound files", e)
            Toast.makeText(this@MainActivity, R.string.sound_files_error, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, R.string.notification_permission_required, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show()
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
                                Toast.makeText(this, getString(R.string.toast_location_permission_required), Toast.LENGTH_LONG).show()
                            }
                        }
                        Manifest.permission.POST_NOTIFICATIONS -> {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                Log.d(TAG, "POST_NOTIFICATIONS permission granted")
                                notificationGranted = true
                            } else {
                                Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                                Toast.makeText(this, R.string.notification_permission_required, Toast.LENGTH_LONG).show()
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
        Log.d(TAG, "Started LogMonitorService")
    }
}

@Composable
fun OverlayPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.overlay_permission_required),
                style = MaterialTheme.typography.headlineSmall,
                color = androidx.compose.ui.graphics.Color(0xFF2C387A) // Dark blue
            )
        },
        text = {
            Text(
                text = stringResource(R.string.overlay_permission_description),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFF2C387A).copy(alpha = 0.8f) // Dark blue with transparency
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFF6D00), // Orange
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.go_to_settings),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color(0xFF2C387A) // Dark blue
                )
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.White,
        titleContentColor = androidx.compose.ui.graphics.Color(0xFF2C387A),
        textContentColor = androidx.compose.ui.graphics.Color(0xFF2C387A).copy(alpha = 0.8f),
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun XCTrackVersionDialog(
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Non-dismissable */ },
        title = {
            Text(
                text = stringResource(R.string.xctrack_version_title),
                style = MaterialTheme.typography.headlineSmall,
                color = androidx.compose.ui.graphics.Color(0xFF2C387A) // Dark blue
            )
        },
        text = {
            Text(
                text = stringResource(R.string.xctrack_version_message),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFF2C387A).copy(alpha = 0.8f) // Dark blue with transparency
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFFF6D00), // Orange
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.ok_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = null, // Non-dismissable
        containerColor = androidx.compose.ui.graphics.Color.White,
        titleContentColor = androidx.compose.ui.graphics.Color(0xFF2C387A),
        textContentColor = androidx.compose.ui.graphics.Color(0xFF2C387A).copy(alpha = 0.8f),
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.medium
    )
}