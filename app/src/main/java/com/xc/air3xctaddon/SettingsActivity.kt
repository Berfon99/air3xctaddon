package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.xc.air3xctaddon.ui.components.SelectTaskTypeDialog
import com.xc.air3xctaddon.ui.SendTelegramConfigDialog
import com.xc.air3xctaddon.ui.SendTelegramMessageConfigDialog
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.pm.PackageManager

class SettingsActivity : ComponentActivity() {
    private val _tasks = mutableStateListOf<Task>()
    private val tasks: List<Task> get() = _tasks

    private val taskResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val taskType = data.getStringExtra("task_type")
                val taskPackage = data.getStringExtra("task_package")
                val taskName = data.getStringExtra("task_name")
                val launchInBackground = data.getBooleanExtra("launch_in_background", true)
                Log.d("SettingsActivity", "Received task from AddTaskActivity: type=$taskType, package=$taskPackage, name=$taskName, launchInBackground=$launchInBackground")
                if (taskType == "LaunchApp" && taskPackage != null && taskName != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val task = Task(
                            id = 0,
                            taskType = taskType,
                            taskData = taskPackage,
                            taskName = taskName,
                            launchInBackground = launchInBackground
                        )
                        Log.d("SettingsActivity", "Inserting Task: id=${task.id}, taskType=${task.taskType}, taskData=${task.taskData}, taskName=${task.taskName}, launchInBackground=${task.launchInBackground}")
                        db.taskDao().insert(task)
                        delay(100)
                        val syncTasks = db.taskDao().getAllTasksSync()
                        Log.d("SettingsActivity", "Sync tasks after insert: ${syncTasks.map { "id=${it.id}, taskType=${it.taskType}, taskData=${it.taskData}, launchInBackground=${it.launchInBackground}" }}")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                var showTaskTypeDialog by remember { mutableStateOf(false) }
                var showTelegramPositionDialog by remember { mutableStateOf(false) }
                var showTelegramMessageDialog by remember { mutableStateOf(false) }
                var showPilotNameDialog by remember { mutableStateOf(false) }
                var showPilotNameWarningDialog by remember { mutableStateOf(false) }
                var pendingTelegramTaskType by remember { mutableStateOf<String?>(null) }
                val settingsRepository = remember { SettingsRepository(applicationContext) }

                SettingsScreen(
                    onAddTask = { showTaskTypeDialog = true },
                    onClearTasks = {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.taskDao().deleteAll("LaunchApp")
                            db.taskDao().deleteAll("SendTelegramPosition")
                            db.taskDao().deleteAll("SendTelegramMessage")
                            Log.d("SettingsActivity", "Cleared all LaunchApp, SendTelegramPosition, and SendTelegramMessage tasks")
                        }
                    }
                )

                if (showTaskTypeDialog) {
                    SelectTaskTypeDialog(
                        onLaunchAppSelected = {
                            taskResultLauncher.launch(Intent(this, AddTaskActivity::class.java))
                            showTaskTypeDialog = false
                        },
                        onTelegramPositionSelected = {
                            showTaskTypeDialog = false
                            if (settingsRepository.getPilotName().isNullOrBlank()) {
                                showPilotNameWarningDialog = true
                                pendingTelegramTaskType = "SendTelegramPosition"
                            } else {
                                showTelegramPositionDialog = true
                            }
                        },
                        onTelegramMessageSelected = {
                            showTaskTypeDialog = false
                            if (settingsRepository.getPilotName().isNullOrBlank()) {
                                showPilotNameWarningDialog = true
                                pendingTelegramTaskType = "SendTelegramMessage"
                            } else {
                                showTelegramMessageDialog = true
                            }
                        },
                        onDismiss = { showTaskTypeDialog = false }
                    )
                }

                if (showTelegramPositionDialog) {
                    SendTelegramConfigDialog(
                        onConfirm = { showTelegramPositionDialog = false },
                        onDismiss = { showTelegramPositionDialog = false }
                    )
                }

                if (showTelegramMessageDialog) {
                    SendTelegramMessageConfigDialog(
                        onConfirm = { showTelegramMessageDialog = false },
                        onDismiss = { showTelegramMessageDialog = false }
                    )
                }

                if (showPilotNameDialog) {
                    TextInputDialog(
                        title = stringResource(R.string.add_pilot_name),
                        label = stringResource(R.string.pilot_name_label),
                        initialValue = settingsRepository.getPilotName() ?: "",
                        onConfirm = { newPilotName ->
                            if (newPilotName.isNotBlank()) {
                                settingsRepository.savePilotName(newPilotName)
                                when (pendingTelegramTaskType) {
                                    "SendTelegramPosition" -> showTelegramPositionDialog = true
                                    "SendTelegramMessage" -> showTelegramMessageDialog = true
                                }
                                pendingTelegramTaskType = null
                            }
                            showPilotNameDialog = false
                        },
                        onDismiss = {
                            showPilotNameDialog = false
                            pendingTelegramTaskType = null
                        }
                    )
                }

                if (showPilotNameWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showPilotNameWarningDialog = false },
                        title = { Text(stringResource(R.string.pilot_name_required_title)) },
                        text = { Text(stringResource(R.string.pilot_name_required_message)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showPilotNameWarningDialog = false
                                    showPilotNameDialog = true
                                }
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = {
                                    showPilotNameWarningDialog = false
                                    pendingTelegramTaskType = null
                                }
                            ) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(onAddTask: () -> Unit = {}, onClearTasks: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(context.applicationContext as android.app.Application)
    )
    val settingsRepository = remember { SettingsRepository(context) }
    val events by viewModel.events.collectAsState()
    var pilotName by remember { mutableStateOf(settingsRepository.getPilotName()) }
    var showPilotNameDialog by remember { mutableStateOf(false) }

    val launchAppTasks by AppDatabase.getDatabase(context).taskDao()
        .getAllTasks()
        .collectAsState(initial = emptyList())

    LaunchedEffect(launchAppTasks) {
        Log.d("SettingsScreen", "launchAppTasks updated: ${launchAppTasks.map { "id=${it.id}, taskType=${it.taskType}, taskData=${it.taskData}, launchInBackground=${it.launchInBackground}" }}")
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
            item {
                Button(
                    onClick = { showPilotNameDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_pilot_name))
                }
            }

            pilotName?.let { name ->
                item {
                    Text(stringResource(R.string.current_pilot_name, name))
                }
            }

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

            item {
                val customEventCount = events.count { it is MainViewModel.EventItem.Event }
                Text(stringResource(R.string.event_count, customEventCount))
            }

            item {
                Button(
                    onClick = onAddTask,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_new_task))
                }
            }

            item {
                Button(
                    onClick = onClearTasks,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) {
                    Text(stringResource(R.string.clear_all_tasks))
                }
            }

            if (launchAppTasks.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.configured_tasks),
                        style = MaterialTheme.typography.h6
                    )
                }
                items(launchAppTasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onDelete = {
                            CoroutineScope(Dispatchers.IO).launch {
                                val db = AppDatabase.getDatabase(context)
                                db.taskDao().delete(task)
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

fun getAppName(context: android.content.Context, packageName: String): String {
    return try {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}

@Composable
fun TaskRow(task: Task, onDelete: () -> Unit) {
    val context = LocalContext.current
    val displayText = when (task.taskType) {
        "SendTelegramPosition" -> stringResource(R.string.task_send_telegram_position, task.taskName)
        "SendTelegramMessage" -> stringResource(R.string.task_send_telegram_message, task.taskName)
        "LaunchApp" -> {
            val appName = getAppName(context, task.taskData)
            if (task.launchInBackground) {
                stringResource(R.string.launch_app_background, appName)
            } else {
                stringResource(R.string.launch_app_foreground, appName)
            }
        }
        else -> task.taskName
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.body1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_task),
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