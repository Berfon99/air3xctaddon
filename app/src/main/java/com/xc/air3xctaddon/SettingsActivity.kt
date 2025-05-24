package com.xc.air3xctaddon

import android.content.Intent
import android.content.pm.PackageManager
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
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    // Declare a mutable state to hold the tasks, so we can trigger recomposition
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
                        // Delay to ensure transaction commits
                        delay(100)
                        // Log synchronous query
                        val syncTasks = db.taskDao().getAllTasksSync()
                        Log.d("SettingsActivity", "Sync tasks after insert: ${syncTasks.map { "id=${it.id}, taskType=${it.taskType}, taskData=${it.taskData}, launchInBackground=${it.launchInBackground}" }}")
                        // No need to collectLatest here from the Flow for immediate UI update in Activity,
                        // as the Composable will observe the Flow directly.
                        // You might want to update _tasks here if you're managing the list in the Activity
                        // and passing it down, but the Composable is already observing the DB.
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                // Now, the SettingsScreen receives the taskResultLauncher directly as a callback
                SettingsScreen(
                    onAddTask = {
                        taskResultLauncher.launch(Intent(this, AddTaskActivity::class.java))
                    },
                    onClearTasks = {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.taskDao().deleteAll("LaunchApp")
                            Log.d("SettingsActivity", "Cleared all LaunchApp tasks")
                        }
                    }
                )
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

    // Collect LaunchApp tasks from tasks table directly.
    // The `key` parameter is no longer needed in collectAsState.
    // The UI will recompose automatically when the Flow emits new data.
    val launchAppTasks by AppDatabase.getDatabase(context).taskDao()
        .getAllTasks()
        .collectAsState(initial = emptyList())

    LaunchedEffect(launchAppTasks) {
        // This LaunchedEffect will re-run whenever launchAppTasks changes (i.e., when DB emits new data)
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
                    onClick = onAddTask, // Call the lambda provided by the Activity
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add a new task")
                }
            }

            // 6. Clear All Tasks
            item {
                Button(
                    onClick = onClearTasks, // Call the lambda provided by the Activity
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) {
                    Text("Clear All Tasks")
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
                                db.taskDao().delete(task)
                                // The UI will automatically refresh because launchAppTasks is observing the database Flow
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
fun TaskRow(task: Task, onDelete: () -> Unit) {
    val context = LocalContext.current
    val appName = try {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(task.taskData, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        task.taskData
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Launch $appName (${if (task.launchInBackground) "Background" else "Foreground"})",
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