package com.xc.air3xctaddon

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private val taskResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val taskType = data.getStringExtra("task_type")
                val taskPackage = data.getStringExtra("task_package")
                val taskName = data.getStringExtra("task_name")
                if (taskType == "LaunchApp" && taskPackage != null && taskName != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        db.eventConfigDao().insert(
                            EventConfig(
                                id = 0, // Room auto-generates, 0 is ignored
                                event = "TASK_CONFIG",
                                taskType = "LaunchApp",
                                taskData = taskPackage,
                                telegramGroupName = taskName,
                                volumeType = VolumeType.SYSTEM,
                                volumePercentage = 100,
                                playCount = 1,
                                position = 0,
                                telegramChatId = null
                            )
                        )
                    }
                    // Request permissions for LaunchApp
                    requestPermissionsForLaunchApp()
                }
            }
        }
    }

    private val systemAlertWindowLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch {
            delay(1500) // Increased delay for permission to register
            val canDrawOverlays = Settings.canDrawOverlays(this@SettingsActivity)
            Log.d("SettingsActivity", "SYSTEM_ALERT_WINDOW check: canDrawOverlays=$canDrawOverlays")
            if (canDrawOverlays) {
                Toast.makeText(this@SettingsActivity, "Overlay permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@SettingsActivity,
                    "Overlay permission denied. If the app isn't listed in Settings, try reinstalling or use ADB: 'adb shell appops set com.xc.air3xctaddon SYSTEM_ALERT_WINDOW allow'",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(packageName)
        Log.d("SettingsActivity", "Battery optimization check: isIgnoring=$isIgnoring")
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
                SettingsScreen(
                    onAddTask = {
                        taskResultLauncher.launch(Intent(this, AddTaskActivity::class.java))
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
            Log.d("SettingsActivity", "Requesting SYSTEM_ALERT_WINDOW for package: $packageName")
            systemAlertWindowLauncher.launch(intent)
        } else {
            Log.d("SettingsActivity", "SYSTEM_ALERT_WINDOW already granted")
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            Log.d("SettingsActivity", "Requesting battery optimization exemption for package: $packageName")
            batteryOptimizationLauncher.launch(intent)
        } else {
            Log.d("SettingsActivity", "Battery optimization already disabled")
        }
    }
}

@Composable
fun SettingsScreen(onAddTask: () -> Unit = {}, onRequestPermissions: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(context.applicationContext as android.app.Application)
    )
    val settingsRepository = remember { SettingsRepository(context) }
    val events by viewModel.events.collectAsState()
    var pilotName by remember { mutableStateOf(settingsRepository.getPilotName()) }
    var showPilotNameDialog by remember { mutableStateOf(false) }

    // Collect LaunchApp tasks from database
    val launchAppTasks by produceState<List<EventConfig>>(
        initialValue = emptyList(),
        key1 = context
    ) {
        val db = AppDatabase.getDatabase(context)
        db.eventConfigDao().getAllConfigs().collectLatest { configs ->
            value = configs.filter { it.event == "TASK_CONFIG" && it.taskType == "LaunchApp" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Add Pilot Name
            item {
                Button(
                    onClick = { showPilotNameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_pilot_name))
                }
            }

            // 2. Current Pilot Name
            pilotName?.let { name ->
                item {
                    Text(stringResource(R.string.current_pilot_name, name))
                }
            }

            // 3. Add an Event
            item {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, AddEventActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_new_event))
                }
            }

            // 4. Total of Events
            item {
                val customEventCount = events.count { it is MainViewModel.EventItem.Event }
                Text(stringResource(R.string.event_count, customEventCount))
            }

            // 5. Add a New Task
            item {
                Button(
                    onClick = onAddTask,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add a new task")
                }
            }

            // 6. Grant Permissions for App Launching
            item {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permissions for App Launching")
                }
            }

            // 7. List of Added Tasks
            if (launchAppTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Configured Tasks",
                        style = MaterialTheme.typography.h6
                    )
                }
                items(launchAppTasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onDelete = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val db = AppDatabase.getDatabase(context)
                                db.eventConfigDao().delete(task)
                            }
                        }
                    )
                }
            }
        }

        if (showPilotNameDialog) {
            TextInputDialog(
                title = stringResource(R.string.add_pilot_name),
                label = stringResource(R.string.pilot_name_label),
                initialValue = pilotName ?: "",
                onConfirm = { newPilotName ->
                    if (newPilotName.isNotBlank()) {
                        settingsRepository.savePilotName(newPilotName)
                        pilotName = newPilotName
                    }
                    showPilotNameDialog = false
                },
                onDismiss = { showPilotNameDialog = false }
            )
        }
    }
}

@Composable
fun TaskRow(task: EventConfig, onDelete: () -> Unit) {
    val context = LocalContext.current
    // Resolve app name from package name (taskData)
    val appName = try {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(task.taskData ?: "", 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        task.taskData ?: "Unknown App"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Launch $appName",
            style = MaterialTheme.typography.body1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Task",
                tint = MaterialTheme.colors.error
            )
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.trim().isNotEmpty()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}