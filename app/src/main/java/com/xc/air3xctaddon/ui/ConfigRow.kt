package com.xc.air3xctaddon.ui

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.location.LocationServices
import com.xc.air3xctaddon.*
import com.xc.air3xctaddon.model.SoundFilesState
import com.xc.air3xctaddon.ui.components.DragHandle
import com.xc.air3xctaddon.ui.components.SpinnerItem
import com.xc.air3xctaddon.ui.components.DropdownMenuSpinner
import com.xc.air3xctaddon.ui.theme.RowBackground
import com.xc.air3xctaddon.ui.theme.SoundFieldBackground
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import com.xc.air3xctaddon.R

@Composable
fun ConfigRow(
    config: EventConfig,
    availableEvents: List<MainViewModel.EventItem>,
    onUpdate: (EventConfig) -> Unit,
    onDelete: () -> Unit,
    onDrag: (Int, Int) -> Unit,
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
    var eventMenuExpanded by remember { mutableStateOf(false) }
    var taskMenuExpanded by remember { mutableStateOf(false) }
    var soundDialogOpen by remember { mutableStateOf(false) }
    var telegramDialogOpen by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val telegramBotHelper = remember { TelegramBotHelper(BuildConfig.TELEGRAM_BOT_TOKEN, LocationServices.getFusedLocationProviderClient(context), settingsRepository) }

    var soundFile by remember { mutableStateOf(if (taskType == "Sound") taskData else "") }
    var volumeType by remember { mutableStateOf(config.volumeType) }
    var volumePercentage by remember { mutableStateOf(config.volumePercentage) }
    var playCount by remember { mutableStateOf(config.playCount) }
    var soundMenuExpanded by remember { mutableStateOf(false) }

    // Load LaunchApp tasks from tasks table
    val launchAppTasks by AppDatabase.getDatabase(context).taskDao()
        .getAllTasks()
        .collectAsState(initial = emptyList())

    LaunchedEffect(launchAppTasks) {
        Log.d("ConfigRow", "launchAppTasks updated: ${launchAppTasks.map { "id=${it.id}, taskType=${it.taskType}, taskData=${it.taskData}, taskName=${it.taskName}, launchInBackground=${it.launchInBackground}" }}")
    }

    val soundFilesState by produceState<SoundFilesState>(
        initialValue = SoundFilesState.Loading,
        key1 = soundMenuExpanded
    ) {
        value = try {
            val soundsDir = File(context.getExternalFilesDir(null), "Sounds")
            val soundFiles = soundsDir.listFiles()
                ?.map { it.name }
                ?.filter { it.endsWith(".mp3") || it.endsWith(".wav") }
                ?.sorted() ?: emptyList()
            if (soundFiles.isEmpty()) SoundFilesState.Empty else SoundFilesState.Success(soundFiles)
        } catch (e: Exception) {
            Log.e("ConfigRow", "Error accessing Sounds directory", e)
            SoundFilesState.Error("Error accessing files")
        }
    }

    fun playSound(fileName: String, volumeType: VolumeType, volumePercentage: Int, playCount: Int) {
        if (fileName.isEmpty()) {
            Log.d("ConfigRow", "Cannot play sound: No sound file selected")
            return
        }
        try {
            val soundsDir = File(context.getExternalFilesDir(null), "Sounds")
            val soundFilePath = File(soundsDir, fileName).absolutePath
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

            var currentPlayCount by mutableIntStateOf(0)
            mediaPlayer?.release()
            val newMediaPlayer = MediaPlayer().apply {
                setDataSource(soundFilePath)
                prepare()
                setVolume(volume, volume)
                start()
                currentPlayCount++
                Log.d("ConfigRow", "Started playback $currentPlayCount/$playCount for: $fileName")
            }
            mediaPlayer = newMediaPlayer

            newMediaPlayer.setOnCompletionListener {
                if (currentPlayCount < playCount) {
                    try {
                        newMediaPlayer.reset()
                        newMediaPlayer.setDataSource(soundFilePath)
                        newMediaPlayer.prepare()
                        newMediaPlayer.setVolume(volume, volume)
                        newMediaPlayer.start()
                        currentPlayCount++
                        Log.d("ConfigRow", "Started playback $currentPlayCount/$playCount for: $fileName")
                    } catch (e: Exception) {
                        Log.e("ConfigRow", "Error restarting playback $currentPlayCount/$playCount for: $fileName", e)
                        newMediaPlayer.release()
                        mediaPlayer = null
                    }
                } else {
                    Log.d("ConfigRow", "Playback completed $currentPlayCount/$playCount for: $fileName")
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
                                onDrag(index, dragAmount.y.toInt())
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
                Box {
                    Button(
                        onClick = {
                            Log.d("ConfigRow", "Event button clicked")
                            eventMenuExpanded = true
                        },
                        modifier = Modifier
                            .width(336.dp)
                            .focusable()
                    ) {
                        Text(
                            text = event,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = eventMenuExpanded,
                        onDismissRequest = { eventMenuExpanded = false },
                        modifier = Modifier
                            .width(336.dp)
                            .heightIn(max = 300.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .fillMaxWidth()
                        ) {
                            availableEvents.forEach { item ->
                                when (item) {
                                    is MainViewModel.EventItem.Category -> {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.subtitle1,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colors.primary
                                        )
                                    }
                                    is MainViewModel.EventItem.Event -> {
                                        DropdownMenuItem(
                                            content = { Text(item.name) },
                                            onClick = {
                                                event = item.name
                                                onUpdate(config.copy(event = item.name))
                                                eventMenuExpanded = false
                                                Log.d("ConfigRow", "Selected event: ${item.name}")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box {
                    Button(
                        onClick = {
                            Log.d("ConfigRow", "Task button clicked")
                            taskMenuExpanded = true
                        },
                        modifier = Modifier
                            .width(360.dp)
                            .focusable()
                            .background(SoundFieldBackground)
                    ) {
                        Text(
                            text = when (taskType) {
                                "SendTelegramPosition" -> "Send Telegram Position: ${telegramGroupName ?: taskData}"
                                "Sound" -> if (taskData.isNotEmpty()) taskData else stringResource(id = R.string.select_task)
                                "LaunchApp" -> {
                                    // Find the corresponding task to get the background flag
                                    val correspondingTask = launchAppTasks.find { it.taskData == taskData }
                                    val backgroundText = if (correspondingTask?.launchInBackground == true) "Background" else "Foreground"
                                    "Launch: ${telegramGroupName ?: taskData} ($backgroundText)"
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
                        modifier = Modifier.width(360.dp)
                    ) {
                        DropdownMenuItem(
                            content = { Text("Sound") },
                            onClick = {
                                taskMenuExpanded = false
                                soundDialogOpen = true
                                Log.d("ConfigRow", "Selected task: Sound")
                            }
                        )
                        DropdownMenuItem(
                            content = { Text("Send Telegram Position") },
                            onClick = {
                                taskMenuExpanded = false
                                telegramDialogOpen = true
                                Log.d("ConfigRow", "Selected task: SendTelegramPosition")
                            }
                        )
                        launchAppTasks.forEach { appTask ->
                            val backgroundText = if (appTask.launchInBackground) "Background" else "Foreground"
                            DropdownMenuItem(
                                content = { Text("Launch: ${appTask.taskName} ($backgroundText)") },
                                onClick = {
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
                                    taskMenuExpanded = false
                                    Log.d("ConfigRow", "Selected task: LaunchApp, app: ${appTask.taskName}, launchInBackground=${appTask.launchInBackground}")
                                }
                            )
                        }
                    }
                }

                if (soundDialogOpen) {
                    Dialog(onDismissRequest = { soundDialogOpen = false }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            elevation = 8.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = stringResource(id = R.string.select_sound),
                                    style = MaterialTheme.typography.h6
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Box {
                                    Button(
                                        onClick = { soundMenuExpanded = true },
                                        modifier = Modifier
                                            .width(480.dp)
                                            .focusable()
                                            .background(SoundFieldBackground)
                                    ) {
                                        Text(
                                            text = if (soundFile.isEmpty()) stringResource(id = R.string.select_sound) else soundFile,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = soundMenuExpanded,
                                        onDismissRequest = { soundMenuExpanded = false },
                                        modifier = Modifier
                                            .width(480.dp)
                                            .heightIn(max = 300.dp)
                                    ) {
                                        when (val state = soundFilesState) {
                                            is SoundFilesState.Loading -> {
                                                DropdownMenuItem(
                                                    content = { Text(stringResource(id = R.string.loading)) },
                                                    onClick = { /* Do nothing */ }
                                                )
                                            }
                                            is SoundFilesState.Success -> {
                                                state.files.forEach { fileName ->
                                                    DropdownMenuItem(
                                                        content = { Text(fileName) },
                                                        onClick = {
                                                            soundFile = fileName
                                                            soundMenuExpanded = false
                                                            Log.d("ConfigRow", "Selected sound file: $fileName")
                                                        }
                                                    )
                                                }
                                            }
                                            is SoundFilesState.Empty -> {
                                                DropdownMenuItem(
                                                    content = { Text(stringResource(id = R.string.no_sound_files)) },
                                                    onClick = { soundMenuExpanded = false }
                                                )
                                            }
                                            is SoundFilesState.Error -> {
                                                DropdownMenuItem(
                                                    content = { Text(state.message) },
                                                    onClick = { soundMenuExpanded = false }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = {
                                            Log.d("ConfigRow", "Play button clicked for file: $soundFile")
                                            playSound(soundFile, volumeType, volumePercentage, playCount)
                                        },
                                        enabled = soundFile.isNotEmpty(),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = stringResource(id = R.string.play),
                                            tint = if (soundFile.isNotEmpty()) MaterialTheme.colors.primary else Color.Gray
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            Log.d("ConfigRow", "Stop button clicked for file: $soundFile")
                                            stopSound()
                                        },
                                        enabled = mediaPlayer != null && mediaPlayer?.isPlaying == true,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = stringResource(id = R.string.stop),
                                            tint = if (mediaPlayer != null && mediaPlayer?.isPlaying == true) MaterialTheme.colors.primary else Color.Gray
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Volume:",
                                        style = MaterialTheme.typography.body1,
                                        modifier = Modifier.width(80.dp)
                                    )
                                    DropdownMenuSpinner(
                                        items = listOf(
                                            SpinnerItem.Item("MAXIMUM"),
                                            SpinnerItem.Item("SYSTEM"),
                                            SpinnerItem.Header("Percentage"),
                                            *(0..10).map { SpinnerItem.Item("${it * 10}%") }.toTypedArray()
                                        ),
                                        selectedItem = when (volumeType) {
                                            VolumeType.MAXIMUM -> "MAXIMUM"
                                            VolumeType.SYSTEM -> "SYSTEM"
                                            VolumeType.PERCENTAGE -> "$volumePercentage%"
                                        },
                                        onItemSelected = { selected ->
                                            when (selected) {
                                                "MAXIMUM" -> {
                                                    volumeType = VolumeType.MAXIMUM
                                                    volumePercentage = 100
                                                }
                                                "SYSTEM" -> {
                                                    volumeType = VolumeType.SYSTEM
                                                    volumePercentage = 100
                                                }
                                                else -> {
                                                    volumeType = VolumeType.PERCENTAGE
                                                    volumePercentage = selected.removeSuffix("%").toInt()
                                                }
                                            }
                                            Log.d("ConfigRow", "Volume selected: $selected")
                                        },
                                        label = "",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Count:",
                                        style = MaterialTheme.typography.body1,
                                        modifier = Modifier.width(80.dp)
                                    )
                                    DropdownMenuSpinner(
                                        items = (1..5).map { SpinnerItem.Item(it.toString()) },
                                        selectedItem = playCount.toString(),
                                        onItemSelected = { selected ->
                                            playCount = selected.toInt()
                                            Log.d("ConfigRow", "Play count selected: $selected")
                                        },
                                        label = "",
                                        modifier = Modifier.width(100.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { soundDialogOpen = false },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(stringResource(id = R.string.cancel))
                                    }
                                    Button(
                                        onClick = {
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
                                        enabled = soundFile.isNotEmpty()
                                    ) {
                                        Text(stringResource(id = R.string.confirm))
                                    }
                                }
                            }
                        }
                    }
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

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (taskType == "Sound" && taskData.isNotEmpty()) {
                            Log.d("ConfigRow", "Main play button clicked for sound: $taskData")
                            playSound(taskData, volumeType, volumePercentage, playCount)
                        } else if (taskType == "SendTelegramPosition" && telegramChatId.isNotEmpty()) {
                            Log.d("ConfigRow", "Main play button clicked for SendTelegramPosition: chatId=$telegramChatId, event=${config.event}")
                            telegramBotHelper.getCurrentLocation(
                                onResult = { latitude, longitude ->
                                    telegramBotHelper.sendLocationMessage(
                                        chatId = telegramChatId,
                                        latitude = latitude,
                                        longitude = longitude,
                                        event = config.event
                                    )
                                    Log.d("ConfigRow", "Sent location message to Telegram: lat=$latitude, lon=$longitude, event=${config.event}")
                                },
                                onError = { error ->
                                    Log.e("ConfigRow", "Failed to get location: $error")
                                }
                            )
                        } else if (taskType == "LaunchApp" && taskData.isNotEmpty()) {
                            Log.d("ConfigRow", "Main play button clicked for LaunchApp: package=$taskData")
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(taskData)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            } else {
                                Log.e("ConfigRow", "No launch intent for package: $taskData")
                            }
                        }
                    },
                    enabled = (taskType == "Sound" && taskData.isNotEmpty()) ||
                            (taskType == "SendTelegramPosition" && telegramChatId.isNotEmpty()) ||
                            (taskType == "LaunchApp" && taskData.isNotEmpty()),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(id = R.string.play),
                        tint = if ((taskType == "Sound" && taskData.isNotEmpty()) ||
                            (taskType == "SendTelegramPosition" && telegramChatId.isNotEmpty()) ||
                            (taskType == "LaunchApp" && taskData.isNotEmpty())) MaterialTheme.colors.primary else Color.Gray
                    )
                }
                IconButton(
                    onClick = {
                        if (taskType == "Sound") {
                            Log.d("ConfigRow", "Main stop button clicked for sound: $taskData")
                            stopSound()
                        }
                    },
                    enabled = taskType == "Sound" && mediaPlayer != null && mediaPlayer?.isPlaying == true,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(id = R.string.stop),
                        tint = if (taskType == "Sound" && mediaPlayer != null && mediaPlayer?.isPlaying == true) MaterialTheme.colors.primary else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    Log.d("ConfigRow", "Delete button clicked")
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = Color.White
                    )
                }
            }
        }
    }
}