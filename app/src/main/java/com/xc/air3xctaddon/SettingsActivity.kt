package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
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
                                telegramChatId = null // Not used for LaunchApp
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                SettingsScreen(onAddTask = {
                    taskResultLauncher.launch(Intent(this, AddTaskActivity::class.java))
                })
            }
        }
    }
}

@Composable
fun SettingsScreen(onAddTask: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(context.applicationContext as android.app.Application)
    )
    val settingsRepository = remember { SettingsRepository(context) }
    val events by viewModel.events.collectAsState()
    var pilotName by remember { mutableStateOf(settingsRepository.getPilotName()) }
    var showPilotNameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    context.startActivity(Intent(context, AddEventActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_new_event))
            }

            Button(
                onClick = onAddTask,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add a new task")
            }

            val customEventCount = events.count { it is MainViewModel.EventItem.Event }
            Text(stringResource(R.string.event_count, customEventCount))

            Button(
                onClick = { showPilotNameDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_pilot_name))
            }

            pilotName?.let {
                Text(stringResource(R.string.current_pilot_name, it))
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