package com.xc.air3xctaddon.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.EventConfig
import com.xc.air3xctaddon.TelegramBotHelper
import com.xc.air3xctaddon.R

@Composable
fun ControlButtons(
    taskType: String,
    taskData: String,
    telegramChatId: String,
    volumeType: com.xc.air3xctaddon.VolumeType,
    volumePercentage: Int,
    playCount: Int,
    mediaPlayer: MediaPlayer?,
    config: EventConfig,
    telegramBotHelper: TelegramBotHelper,
    context: Context,
    onPlaySound: () -> Unit,
    onStopSound: () -> Unit,
    onDelete: () -> Unit
) {
    Row {
        IconButton(
            onClick = {
                Log.d("ControlButtons", "Play button clicked for taskType: $taskType, taskData: $taskData, telegramChatId: $telegramChatId")
                when (taskType) {
                    "Sound" -> {
                        if (taskData.isNotEmpty()) {
                            Log.d("ControlButtons", "Main play button clicked for sound: $taskData")
                            onPlaySound()
                        } else {
                            Log.w("ControlButtons", "Sound taskData is empty")
                        }
                    }
                    "SendTelegramPosition" -> {
                        if (taskData.isNotEmpty()) {
                            Log.d("ControlButtons", "Main play button clicked for SendTelegramPosition: chatId=$taskData, event=${config.event}")
                            telegramBotHelper.getCurrentLocation(
                                onResult = { latitude, longitude ->
                                    telegramBotHelper.sendLocationMessage(
                                        chatId = taskData,
                                        latitude = latitude,
                                        longitude = longitude,
                                        event = config.event
                                    )
                                    Log.d("ControlButtons", "Sent location message to Telegram: lat=$latitude, lon=$longitude, event=${config.event}")
                                },
                                onError = { error ->
                                    Log.e("ControlButtons", "Failed to get location: $error")
                                }
                            )
                        } else {
                            Log.w("ControlButtons", "SendTelegramPosition taskData is empty")
                        }
                    }
                    "LaunchApp" -> {
                        if (taskData.isNotEmpty()) {
                            Log.d("ControlButtons", "Main play button clicked for LaunchApp: package=$taskData")
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(taskData)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                Log.e("ControlButtons", "No launch intent for package: $taskData")
                            }
                        } else {
                            Log.w("ControlButtons", "LaunchApp taskData is empty")
                        }
                    }
                    "ZELLO_PTT" -> {
                        try {
                            val zelloIntent = Intent("com.zello.ptt.up").apply {
                                putExtra("com.zello.stayHidden", true)
                            }
                            context.sendBroadcast(zelloIntent)
                            Log.d("ControlButtons", "Main play button clicked for ZELLO_PTT: sent com.zello.ptt.up")
                        } catch (e: Exception) {
                            Log.e("ControlButtons", "Failed to send Zello PTT intent for event: ${config.event}, configId=${config.id}", e)
                        }
                    }
                    "SendTelegramMessage" -> {
                        Log.d("ControlButtons", "Processing SendTelegramMessage with taskData: $taskData")
                        if (taskData.isNotEmpty() && taskData.contains("|")) {
                            val (chatId, message) = taskData.split("|", limit = 2)
                            if (chatId.isNotEmpty() && message.isNotEmpty()) {
                                Log.d("ControlButtons", "Playing Telegram message for chatId: $chatId, message: $message")
                                telegramBotHelper.sendMessage(
                                    chatId = chatId,
                                    message = message
                                )
                            } else {
                                Log.e("ControlButtons", "Invalid Telegram message config")
                            }
                        } else {
                            Log.e("ControlButtons", "Invalid Telegram message taskData format for event ${config.event}, id ${config.id}: $taskData")
                        }
                    }
                    else -> {
                        Log.w("ControlButtons", "Unknown taskType: $taskType")
                    }
                }
            },
            enabled = (taskType == "Sound" && taskData.isNotEmpty()) ||
                    (taskType == "SendTelegramPosition" && taskData.isNotEmpty()) ||
                    (taskType == "LaunchApp" && taskData.isNotEmpty()) ||
                    taskType == "ZELLO_PTT" ||
                    (taskType == "SendTelegramMessage" && taskData.isNotEmpty() && taskData.contains("|") && taskData.split("|", limit = 2).all { it.isNotEmpty()}),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(id = R.string.play),
                tint = if ((taskType == "Sound" && taskData.isNotEmpty()) ||
                    (taskType == "SendTelegramPosition" && taskData.isNotEmpty()) ||
                    (taskType == "LaunchApp" && taskData.isNotEmpty()) ||
                    taskType == "ZELLO_PTT" ||
                    (taskType == "SendTelegramMessage" && taskData.isNotEmpty() && taskData.contains("|") && taskData.split("|", limit = 2).all { it.isNotEmpty()})) MaterialTheme.colors.primary else Color.Gray
            )
        }

        IconButton(
            onClick = {
                if (taskType == "Sound") {
                    Log.d("ControlButtons", "Main stop button clicked for sound: $taskData")
                    onStopSound()
                }
            },
            enabled = taskType == "Sound" && mediaPlayer != null && mediaPlayer.isPlaying,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = stringResource(id = R.string.stop),
                tint = if (taskType == "Sound" && mediaPlayer != null && mediaPlayer.isPlaying) MaterialTheme.colors.primary else Color.Gray
            )
        }

        IconButton(
            onClick = {
                Log.d("ControlButtons", "Delete button clicked")
                onDelete()
            }
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.delete),
                tint = Color.White
            )
        }
    }
}