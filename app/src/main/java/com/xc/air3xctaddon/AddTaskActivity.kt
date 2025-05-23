package com.xc.air3xctaddon

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
                    onTaskSelected = { packageName, appName ->
                        val result = Intent().apply {
                            putExtra("task_type", "LaunchApp")
                            putExtra("task_package", packageName)
                            putExtra("task_name", appName)
                        }
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@Composable
fun AddTaskScreen(
    onTaskSelected: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var showAppList by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val packageManager = context.packageManager
    val apps = remember {
        packageManager.getInstalledApplications(0)
            .filter { app -> app.packageName != context.packageName }
            .sortedBy { app -> app.loadLabel(packageManager).toString() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Task") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (!showAppList) {
                Button(
                    onClick = { showAppList = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Launch an app")
                }
            } else {
                Text("Select an app to launch", style = MaterialTheme.typography.h6)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(apps) { app ->
                        val appName = app.loadLabel(packageManager).toString()
                        Text(
                            text = appName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTaskSelected(app.packageName, appName)
                                }
                                .padding(16.dp)
                        )
                        Divider()
                    }
                }
            }
        }
    }
}