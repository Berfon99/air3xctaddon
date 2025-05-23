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
                    onTaskAdded = { taskPackage, taskName ->
                        val result = Intent().apply {
                            putExtra("task_type", "LaunchApp")
                            putExtra("task_package", taskPackage)
                            putExtra("task_name", taskName)
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
    onTaskAdded: (String, String) -> Unit
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