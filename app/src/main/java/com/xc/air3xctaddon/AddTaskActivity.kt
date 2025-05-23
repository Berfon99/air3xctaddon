package com.xc.air3xctaddon

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddTaskActivity : ComponentActivity() {
    private val systemAlertWindowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch {
            delay(1500) // Increased delay for permission to register
            val canDrawOverlays = Settings.canDrawOverlays(this@AddTaskActivity)
            Log.d("AddTaskActivity", "SYSTEM_ALERT_WINDOW check: canDrawOverlays=$canDrawOverlays")
            if (canDrawOverlays) {
                Toast.makeText(this@AddTaskActivity, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@AddTaskActivity,
                    "Overlay permission denied. If the app isn't listed in Settings, try reinstalling or use ADB: 'adb shell appops set com.xc.air3xctaddon SYSTEM_ALERT_WINDOW allow'",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        Log.d("AddTaskActivity", "Battery optimization check: isIgnoring=$isIgnoring")
        if (isIgnoring) {
            Toast.makeText(this, "Battery optimization disabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Battery optimization enabled. App launching may be restricted.", Toast.LENGTH_LONG).show()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                AddTaskScreen(
                    onTaskAdded = { taskPackage, taskName ->
                        val result = Intent().apply {
                            putExtra("task_type", "LaunchApp")
                            putExtra("task_package", taskPackage)
                            putExtra("task_name", taskName)
                        }
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onRequestPermissions = { requestPermissionsForLaunchApp() }
                )
            }
        }
    }

    private fun requestPermissionsForLaunchApp() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            Log.d("AddTaskActivity", "Requesting SYSTEM_ALERT_WINDOW for package: $packageName")
            systemAlertWindowLauncher.launch(intent)
        } else {
            Log.d("AddTaskActivity", "SYSTEM_ALERT_WINDOW already granted")
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            Log.d("AddTaskActivity", "Requesting battery optimization exemption for package: $packageName")
            batteryOptimizationLauncher.launch(intent)
        } else {
            Log.d("AddTaskActivity", "Battery optimization already disabled")
        }
    }
}

@Composable
fun AddTaskScreen(
    onTaskAdded: (String, String) -> Unit,
    onRequestPermissions: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val packageManager = context.packageManager
    val installedApps = remember {
        packageManager.getInstalledApplications(0)
            .filter { app -> app.enabled && packageManager.getLaunchIntentForPackage(app.packageName) != null }
            .map { app -> AppInfo(app.packageName, packageManager.getApplicationLabel(app).toString()) }
            .sortedBy { app -> app.name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Add Task",
            style = MaterialTheme.typography.headlineSmall
        )

        Button(
            onClick = { showAppPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select App to Launch")
        }

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions for App Launching")
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Select App") },
            text = {
                LazyColumn {
                    items(installedApps) { app ->
                        Text(
                            text = app.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTaskAdded(app.packageName, app.name)
                                    showAppPicker = false
                                }
                                .padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAppPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class AppInfo(val packageName: String, val name: String)