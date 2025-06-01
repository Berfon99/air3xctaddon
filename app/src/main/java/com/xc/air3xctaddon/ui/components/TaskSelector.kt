package com.xc.air3xctaddon.ui.components

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.R
import com.xc.air3xctaddon.SettingsActivity
import com.xc.air3xctaddon.Task
import com.xc.air3xctaddon.ui.theme.SoundFieldBackground

@Composable
fun TaskSelector(
    taskType: String,
    taskData: String,
    telegramGroupName: String?,
    launchAppTasks: List<Task>,
    onSoundDialogOpen: () -> Unit,
    onLaunchAppSelected: (Task) -> Unit,
    onZelloPttSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var taskMenuExpanded by remember { mutableStateOf(false) }
    var showOtherDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier) {
        Button(
            onClick = {
                Log.d("TaskSelector", "Task button clicked")
                taskMenuExpanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusable()
                .background(SoundFieldBackground)
        ) {
            Text(
                text = when (taskType) {
                    "Sound" -> if (taskData.isNotEmpty()) taskData else stringResource(id = R.string.select_task)
                    "ZELLO_PTT" -> stringResource(id = R.string.task_zello_ptt)
                    "SendTelegramPosition" -> telegramGroupName?.let { stringResource(id = R.string.task_send_telegram_position, it) } ?: stringResource(id = R.string.select_task)
                    "SendTelegramMessage" -> telegramGroupName?.let { stringResource(id = R.string.task_send_telegram_message, it) } ?: stringResource(id = R.string.select_task)
                    "LaunchApp" -> {
                        val task = launchAppTasks.find { it.taskData == taskData }
                        task?.let {
                            val backgroundText = if (it.launchInBackground) stringResource(id = R.string.background) else stringResource(id = R.string.foreground)
                            stringResource(id = R.string.task_launch_app, it.taskName, backgroundText)
                        } ?: stringResource(id = R.string.select_task)
                    }
                    else -> stringResource(id = R.string.select_task)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = taskMenuExpanded,
            onDismissRequest = { taskMenuExpanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            DropdownMenuItem(
                content = { Text(stringResource(id = R.string.task_sound)) },
                onClick = {
                    taskMenuExpanded = false
                    onSoundDialogOpen()
                    Log.d("TaskSelector", "Selected task: Sound")
                }
            )
            DropdownMenuItem(
                content = { Text(stringResource(id = R.string.task_zello_ptt)) },
                onClick = {
                    taskMenuExpanded = false
                    onZelloPttSelected()
                    Log.d("TaskSelector", "Selected task: ZELLO_PTT")
                }
            )
            launchAppTasks.forEach { task ->
                val displayText = when (task.taskType) {
                    "SendTelegramPosition" -> stringResource(id = R.string.task_send_telegram_position, task.taskName)
                    "SendTelegramMessage" -> stringResource(id = R.string.task_send_telegram_message, task.taskName)
                    "LaunchApp" -> {
                        val backgroundText = if (task.launchInBackground) stringResource(id = R.string.background) else stringResource(id = R.string.foreground)
                        stringResource(id = R.string.task_launch_app_item, task.taskName, backgroundText)
                    }
                    else -> task.taskName
                }
                DropdownMenuItem(
                    content = { Text(displayText) },
                    onClick = {
                        onLaunchAppSelected(task)
                        taskMenuExpanded = false
                        Log.d("TaskSelector", "Selected task: ${task.taskType}, taskName=${task.taskName}")
                    }
                )
            }
            if (launchAppTasks.isEmpty()) {
                DropdownMenuItem(
                    content = { Text(stringResource(id = R.string.task_other)) },
                    onClick = {
                        taskMenuExpanded = false
                        showOtherDialog = true
                        Log.d("TaskSelector", "Selected task: Other")
                    }
                )
            }
        }
    }

    if (showOtherDialog) {
        AlertDialog(
            onDismissRequest = { showOtherDialog = false },
            title = { Text(stringResource(R.string.add_task_required_title)) },
            text = { Text(stringResource(R.string.add_task_required_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showOtherDialog = false
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            putExtra("open_task_type_dialog", true)
                        }
                        context.startActivity(intent)
                        Log.d("TaskSelector", "Navigating to SettingsActivity with SelectTaskTypeDialog")
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showOtherDialog = false
                        Log.d("TaskSelector", "Other dialog cancelled")
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}