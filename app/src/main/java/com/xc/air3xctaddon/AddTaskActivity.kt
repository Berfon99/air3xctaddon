package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme

class AddTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                AddTaskScreen(
                    onTaskAdded = { taskPackage, taskName, launchInBackground ->
                        val result = Intent().apply {
                            putExtra("task_type", "LaunchApp")
                            putExtra("task_package", taskPackage)
                            putExtra("task_name", taskName)
                            putExtra("launch_in_background", launchInBackground)
                        }
                        setResult(RESULT_OK, result)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun AddTaskScreen(
    onTaskAdded: (String, String, Boolean) -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var launchInBackground by remember { mutableStateOf(false) } // Changed default to false
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

        selectedApp?.let {
            Text("Selected: ${it.name}")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = launchInBackground,
                onCheckedChange = { launchInBackground = it }
            )
            Text("Launch in background (keep XCTrack in foreground)")
        }

        Button(
            onClick = {
                selectedApp?.let {
                    onTaskAdded(it.packageName, it.name, launchInBackground)
                }
            },
            enabled = selectedApp != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Confirm")
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
                                    selectedApp = app
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