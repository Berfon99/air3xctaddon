package com.xc.air3xctaddon.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.R
import com.xc.air3xctaddon.Task
import com.xc.air3xctaddon.ui.theme.SoundFieldBackground

@Composable
fun TaskSelector(
    taskType: String,
    taskData: String,
    telegramGroupName: String?,
    launchAppTasks: List<Task>,
    onSoundDialogOpen: () -> Unit,
    onTelegramDialogOpen: () -> Unit,
    onLaunchAppSelected: (Task) -> Unit,
    onZelloPttSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    var taskMenuExpanded by remember { mutableStateOf(false) }

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
                    "SendTelegramPosition" -> stringResource(id = R.string.task_send_telegram_position, telegramGroupName ?: taskData)
                    "Sound" -> if (taskData.isNotEmpty()) taskData else stringResource(id = R.string.select_task)
                    "LaunchApp" -> {
                        val correspondingTask = launchAppTasks.find { it.taskData == taskData }
                        val backgroundText = if (correspondingTask?.launchInBackground == true)
                            stringResource(id = R.string.background) else stringResource(id = R.string.foreground)
                        stringResource(id = R.string.task_launch_app, telegramGroupName ?: taskData, backgroundText)
                    }
                    "ZELLO_PTT" -> stringResource(id = R.string.task_zello_ptt)
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
                content = { Text(stringResource(id = R.string.task_send_telegram_position_item)) },
                onClick = {
                    taskMenuExpanded = false
                    onTelegramDialogOpen()
                    Log.d("TaskSelector", "Selected task: SendTelegramPosition")
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
            launchAppTasks.forEach { appTask ->
                val backgroundText = if (appTask.launchInBackground)
                    stringResource(id = R.string.background) else stringResource(id = R.string.foreground)
                DropdownMenuItem(
                    content = { Text(stringResource(id = R.string.task_launch_app_item, appTask.taskName, backgroundText)) },
                    onClick = {
                        onLaunchAppSelected(appTask)
                        taskMenuExpanded = false
                        Log.d("TaskSelector", "Selected task: LaunchApp, taskName=${appTask.taskName}")
                    }
                )
            }
        }
    }
}