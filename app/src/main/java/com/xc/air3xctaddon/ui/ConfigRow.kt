package com.xc.air3xctaddon.ui

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.xc.air3xctaddon.*
import com.xc.air3xctaddon.ui.components.DragHandle
import com.xc.air3xctaddon.ui.theme.RowBackground
import com.xc.air3xctaddon.R
import com.xc.air3xctaddon.ui.components.EventSelector
import com.xc.air3xctaddon.ui.components.TaskSelector
import com.xc.air3xctaddon.ui.components.ControlButtons
import com.xc.air3xctaddon.ui.components.SoundConfigDialog

@Composable
fun ConfigRow(
    config: EventConfig,
    availableEvents: List<MainViewModel.EventItem>,
    onUpdate: (EventConfig) -> Unit,
    onDelete: () -> Unit,
    onDrag: (Int, Float) -> Unit,
    index: Int,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    dragOffset: Float
) {
    var event by remember { mutableStateOf(config.event) }
    var taskType by remember { mutableStateOf(config.taskType ?: "") }
    var taskData by remember { mutableStateOf(config.taskData ?: "") }
    var telegramChatId by remember { mutableStateOf(config.telegramChatId ?: "") }
    var telegramGroupName by remember { mutableStateOf(config.telegramGroupName ?: "") }
    var soundDialogOpen by remember { mutableStateOf(false) }
    var telegramDialogOpen by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val telegramBotHelper = remember {
        TelegramBotHelper(
            BuildConfig.TELEGRAM_BOT_TOKEN,
            LocationServices.getFusedLocationProviderClient(context),
            settingsRepository
        )
    }

    var soundFile by remember { mutableStateOf(if (taskType == "Sound") taskData else "") }
    var volumeType by remember { mutableStateOf(config.volumeType) }
    var volumePercentage by remember { mutableStateOf(config.volumePercentage) }
    var playCount by remember { mutableStateOf(config.playCount) }

    // Load LaunchApp tasks from tasks table
    val launchAppTasks by AppDatabase.getDatabase(context).taskDao()
        .getAllTasks()
        .collectAsState(initial = emptyList<Task>())

    LaunchedEffect(launchAppTasks) {
        Log.d("ConfigRow", "launchAppTasks updated: ${launchAppTasks.size} tasks")
        launchAppTasks.forEach { task ->
            Log.d("ConfigRow", "Task: id=${task.id}, type=${task.taskType}, data=${task.taskData}, name=${task.taskName}, background=${task.launchInBackground}")
        }
    }

    fun playSound(fileName: String, volumeType: VolumeType, volumePercentage: Int, playCount: Int) {
        if (fileName.isEmpty()) {
            Log.d("ConfigRow", "Cannot play sound: No sound file selected")
            return
        }
        try {
            val soundsDir = java.io.File(context.getExternalFilesDir(null), "Sounds")
            val soundFilePath = java.io.File(soundsDir, fileName).absolutePath
            Log.d("ConfigRow", "Playing sound file: $soundFilePath, volumeType: $volumeType, volumePercentage: $volumePercentage%, playCount: $playCount")

            val volume = when (volumeType) {
                VolumeType.MAXIMUM -> 1.0f
                VolumeType.SYSTEM -> {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    if (maxVolume > 0) currentVolume / maxVolume else 1.0f
                }
                VolumeType.PERCENTAGE -> volumePercentage / 100.0f
            }

            // Use an array to make it mutable in the callback
            val currentPlayCount = intArrayOf(0)
            mediaPlayer?.release()
            val newMediaPlayer = MediaPlayer().apply {
                setDataSource(soundFilePath)
                prepare()
                setVolume(volume, volume)
                start()
                currentPlayCount[0]++
                Log.d("ConfigRow", "Started playback ${currentPlayCount[0]}/$playCount for: $fileName")
            }
            mediaPlayer = newMediaPlayer

            newMediaPlayer.setOnCompletionListener {
                if (currentPlayCount[0] < playCount) {
                    try {
                        newMediaPlayer.reset()
                        newMediaPlayer.setDataSource(soundFilePath)
                        newMediaPlayer.prepare()
                        newMediaPlayer.setVolume(volume, volume)
                        newMediaPlayer.start()
                        currentPlayCount[0]++
                        Log.d("ConfigRow", "Started playback ${currentPlayCount[0]}/$playCount for: $fileName")
                    } catch (e: Exception) {
                        Log.e("ConfigRow", "Error restarting playback ${currentPlayCount[0]}/$playCount for: $fileName", e)
                        newMediaPlayer.release()
                        mediaPlayer = null
                    }
                } else {
                    Log.d("ConfigRow", "Playback completed ${currentPlayCount[0]}/$playCount for: $fileName")
                    newMediaPlayer.release()
                    mediaPlayer = null
                }
            }

            newMediaPlayer.setOnErrorListener { _, what, extra ->
                Log.e("ConfigRow", "MediaPlayer error: what=$what, extra=$extra")
                newMediaPlayer.release()
                mediaPlayer = null
                true
            }
        } catch (e: Exception) {
            Log.e("ConfigRow", "Error playing sound file: $fileName", e)
            mediaPlayer = null
        }
    }

    fun stopSound() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
                Log.d("ConfigRow", "Stopped playback for: $soundFile")
            }
            player.release()
            mediaPlayer = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = if (isDragging) dragOffset.dp else 0.dp)
            .background(if (isDragging) RowBackground.copy(alpha = 0.8f) else RowBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DragHandle(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(index, dragAmount.y)
                            }
                        )
                    }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event Selection - takes up available space
                EventSelector(
                    selectedEvent = event,
                    availableEvents = availableEvents,
                    onEventSelected = { selectedEvent ->
                        event = selectedEvent
                        onUpdate(config.copy(event = selectedEvent))
                        Log.d("ConfigRow", "Selected event: $selectedEvent")
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Task Selection - takes up available space
                TaskSelector(
                    taskType = taskType,
                    taskData = taskData,
                    telegramGroupName = telegramGroupName,
                    launchAppTasks = launchAppTasks,
                    onSoundDialogOpen = { soundDialogOpen = true },
                    onTelegramDialogOpen = { telegramDialogOpen = true },
                    onLaunchAppSelected = { appTask ->
                        taskType = "LaunchApp"
                        taskData = appTask.taskData
                        telegramGroupName = appTask.taskName
                        onUpdate(config.copy(
                            taskType = taskType,
                            taskData = taskData,
                            telegramGroupName = appTask.taskName,
                            volumeType = VolumeType.SYSTEM,
                            volumePercentage = 100,
                            playCount = 1,
                            telegramChatId = null,
                            launchInBackground = appTask.launchInBackground
                        ))
                        Log.d("ConfigRow", "Selected task: LaunchApp, app: ${appTask.taskName}, launchInBackground=${appTask.launchInBackground}")
                    },
                    onZelloPttSelected = {
                        taskType = "ZELLO_PTT"
                        taskData = ""
                        telegramGroupName = "" // Fix: Use empty string instead of null
                        telegramChatId = ""   // Fix: Use empty string instead of null
                        onUpdate(config.copy(
                            taskType = taskType,
                            taskData = taskData,
                            telegramGroupName = null,
                            telegramChatId = null,
                            volumeType = VolumeType.SYSTEM,
                            volumePercentage = 100,
                            playCount = 1,
                            launchInBackground = true
                        ))
                        Log.d("ConfigRow", "Selected task: ZELLO_PTT")
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Control Buttons
                ControlButtons(
                    taskType = taskType,
                    taskData = taskData,
                    telegramChatId = telegramChatId,
                    volumeType = volumeType,
                    volumePercentage = volumePercentage,
                    playCount = playCount,
                    mediaPlayer = mediaPlayer,
                    config = config,
                    telegramBotHelper = telegramBotHelper,
                    context = context,
                    onPlaySound = { playSound(taskData, volumeType, volumePercentage, playCount) },
                    onStopSound = { stopSound() },
                    onDelete = onDelete
                )
            }
        }

        // Dialogs
        if (soundDialogOpen) {
            SoundConfigDialog(
                soundFile = soundFile,
                volumeType = volumeType,
                volumePercentage = volumePercentage,
                playCount = playCount,
                onSoundFileChanged = { soundFile = it },
                onVolumeTypeChanged = { volumeType = it },
                onVolumePercentageChanged = { volumePercentage = it },
                onPlayCountChanged = { playCount = it },
                onPlaySound = { playSound(soundFile, volumeType, volumePercentage, playCount) },
                onStopSound = { stopSound() },
                mediaPlayer = mediaPlayer,
                onConfirm = {
                    taskType = "Sound"
                    taskData = soundFile
                    onUpdate(config.copy(
                        taskType = taskType,
                        taskData = taskData,
                        volumeType = volumeType,
                        volumePercentage = volumePercentage,
                        playCount = playCount,
                        telegramChatId = null,
                        telegramGroupName = null
                    ))
                    soundDialogOpen = false
                    Log.d("ConfigRow", "Sound config saved: $soundFile, $volumeType, $volumePercentage, $playCount")
                },
                onDismiss = { soundDialogOpen = false }
            )
        }

        if (telegramDialogOpen) {
            SendTelegramConfigDialog(
                onAdd = { chatId, groupName ->
                    taskType = "SendTelegramPosition"
                    telegramChatId = chatId
                    telegramGroupName = groupName
                    onUpdate(config.copy(
                        taskType = taskType,
                        taskData = groupName,
                        volumeType = VolumeType.SYSTEM,
                        volumePercentage = 100,
                        playCount = 1,
                        telegramChatId = chatId,
                        telegramGroupName = groupName
                    ))
                    telegramDialogOpen = false
                    Log.d("ConfigRow", "Telegram config saved: chatId=$chatId, groupName=$groupName")
                },
                onDismiss = { telegramDialogOpen = false }
            )
        }
    }
}