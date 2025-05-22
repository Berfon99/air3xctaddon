package com.xc.air3xctaddon.ui

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var eventMenuExpanded by remember { mutableStateOf(false) }
    var taskMenuExpanded by remember { mutableStateOf(false) }
    var soundDialogOpen by remember { mutableStateOf(false) }
    var telegramDialogOpen by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val context = LocalContext.current
    val telegramBotHelper = remember { TelegramBotHelper(BuildConfig.TELEGRAM_BOT_TOKEN, LocationServices.getFusedLocationProviderClient(context)) }

    var soundFile by remember { mutableStateOf(if (taskType == "Sound") taskData else "") }
    var volumeType by remember { mutableStateOf(config.volumeType) }
    var volumePercentage by remember { mutableStateOf(config.volumePercentage) }
    var playCount by remember { mutableStateOf(config.playCount) }
    var soundMenuExpanded by remember { mutableStateOf(false) }

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
                            text = if (taskData.isEmpty()) stringResource(id = R.string.select_task) else taskData,
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
                            content = { Text("SendTelegramPosition") },
                            onClick = {
                                taskMenuExpanded = false
                                telegramDialogOpen = true
                                Log.d("ConfigRow", "Selected task: SendTelegramPosition")
                            }
                        )
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
                                                telegramChatId = null
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
                        onAdd = { selectedChatId ->
                            taskType = "SendTelegramPosition"
                            taskData = "Send Telegram Position"
                            telegramChatId = selectedChatId
                            onUpdate(config.copy(
                                taskType = taskType,
                                taskData = taskData,
                                volumeType = VolumeType.SYSTEM,
                                volumePercentage = 100,
                                playCount = 1,
                                telegramChatId = telegramChatId
                            ))
                            telegramDialogOpen = false
                            Log.d("ConfigRow", "Telegram config saved: chatId=$selectedChatId")
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
                            Log.d("ConfigRow", "Main play button clicked for SendTelegramPosition: chatId=$telegramChatId")
                            telegramBotHelper.getCurrentLocation(
                                onResult = { latitude, longitude ->
                                    telegramBotHelper.sendLiveLocation(telegramChatId, latitude, longitude)
                                    Log.d("ConfigRow", "Sent location to Telegram: lat=$latitude, lon=$longitude")
                                },
                                onError = { error ->
                                    Log.e("ConfigRow", "Failed to get location: $error")
                                }
                            )
                        }
                    },
                    enabled = (taskType == "Sound" && taskData.isNotEmpty()) || (taskType == "SendTelegramPosition" && telegramChatId.isNotEmpty()),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(id = R.string.play),
                        tint = if ((taskType == "Sound" && taskData.isNotEmpty()) || (taskType == "SendTelegramPosition" && telegramChatId.isNotEmpty())) MaterialTheme.colors.primary else Color.Gray
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

@Composable
fun SendTelegramConfigDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var telegramChatId by remember { mutableStateOf("") }
    var telegramGroupName by remember { mutableStateOf("") }
    var isLoadingGroups by remember { mutableStateOf(true) } // Start with loading
    var isCheckingBot by remember { mutableStateOf(false) }
    var isSendingStart by remember { mutableStateOf(false) }
    var groupError by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<TelegramGroup>>(emptyList()) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var botInfo by remember { mutableStateOf<TelegramBotInfo?>(null) }
    var selectedGroup by remember { mutableStateOf<TelegramGroup?>(null) }
    var showBotSetupDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val telegramBotHelper = remember { TelegramBotHelper(BuildConfig.TELEGRAM_BOT_TOKEN, fusedLocationClient) }

    fun fetchGroups() {
        isLoadingGroups = true
        groupError = null
        telegramBotHelper.fetchGroups(
            onResult = { fetchedGroups ->
                groups = fetchedGroups
                isLoadingGroups = false

                // Clear previous selection if it's no longer valid
                if (telegramChatId.isNotEmpty()) {
                    val stillExists = fetchedGroups.any { it.chatId == telegramChatId }
                    if (!stillExists) {
                        // Previously selected group no longer exists, clear selection
                        telegramChatId = ""
                        telegramGroupName = ""
                        selectedGroup = null
                    }
                }

                // Auto-select first group only if no valid selection exists
                if (telegramChatId.isEmpty() && fetchedGroups.isNotEmpty()) {
                    val firstGroup = fetchedGroups.first()
                    telegramChatId = firstGroup.chatId
                    telegramGroupName = firstGroup.title
                    selectedGroup = firstGroup
                }
            },
            onError = { error ->
                isLoadingGroups = false
                groupError = error
                // Clear selection on error
                telegramChatId = ""
                telegramGroupName = ""
                selectedGroup = null
            }
        )
    }

    fun checkBotInSelectedGroup() {
        selectedGroup?.let { group ->
            isCheckingBot = true
            telegramBotHelper.checkBotInGroup(
                chatId = group.chatId,
                onResult = { isMember, isActive ->
                    isCheckingBot = false
                    // Update the selected group with fresh bot status
                    selectedGroup = group.copy(isBotMember = isMember, isBotActive = isActive)

                    // Also update the group in the list
                    groups = groups.map {
                        if (it.chatId == group.chatId)
                            it.copy(isBotMember = isMember, isBotActive = isActive)
                        else it
                    }

                    if (!isMember) {
                        showBotSetupDialog = true
                    }
                },
                onError = { error ->
                    isCheckingBot = false
                    groupError = "Failed to check bot status: $error"
                    // On error, assume bot is not available
                    selectedGroup = group.copy(isBotMember = false, isBotActive = false)
                }
            )
        }
    }

    fun sendStartCommand() {
        selectedGroup?.let { group ->
            isSendingStart = true
            telegramBotHelper.sendStartCommand(
                chatId = group.chatId,
                onResult = {
                    isSendingStart = false
                    selectedGroup = group.copy(isBotActive = true)
                    // Update the group in the list as well
                    groups = groups.map {
                        if (it.chatId == group.chatId)
                            it.copy(isBotActive = true)
                        else it
                    }
                    // Configuration is complete
                    onAdd(group.chatId)
                },
                onError = { error ->
                    isSendingStart = false
                    groupError = "Failed to activate bot: $error"
                }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (!isGranted) {
            groupError = "Location permission denied. Please grant permission to continue."
        } else {
            fetchGroups()
        }
    }

    // Check bot status when selectedGroup changes
    LaunchedEffect(selectedGroup?.chatId) {
        selectedGroup?.let { group ->
            if (group.chatId.isNotEmpty()) {
                checkBotInSelectedGroup()
            }
        }
    }

    // Refresh data every time dialog opens
    LaunchedEffect(Unit) {
        // Reset all state when dialog opens
        telegramChatId = ""
        telegramGroupName = ""
        selectedGroup = null
        groups = emptyList()
        groupError = null

        // Get bot info first
        telegramBotHelper.getBotInfo(
            onResult = { info ->
                botInfo = info
            },
            onError = { error ->
                Log.e("TelegramDialog", "Failed to get bot info: $error")
            }
        )

        permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (showBotSetupDialog) {
        AlertDialog(
            onDismissRequest = { showBotSetupDialog = false },
            title = { Text("Add Bot to Group") },
            text = {
                Column {
                    Text("The bot is not added to the selected group yet.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Would you like to open Telegram to add the bot to '${selectedGroup?.title}'?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("After adding the bot, come back here and refresh to continue the setup.",
                        style = MaterialTheme.typography.caption)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        botInfo?.let { info ->
                            telegramBotHelper.openTelegramToAddBot(context, info.username, selectedGroup?.title)
                        }
                        showBotSetupDialog = false
                    }
                ) {
                    Text("Open Telegram")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBotSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .widthIn(min = 800.dp)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configure Telegram Position",
                        style = MaterialTheme.typography.h6
                    )

                    // Refresh button
                    IconButton(
                        onClick = { fetchGroups() },
                        enabled = !isLoadingGroups
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow, // You might want to use a refresh icon
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                when {
                    isLoadingGroups -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Text("Searching for available groups...")
                        }
                    }

                    groupError != null -> {
                        Card(
                            backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = groupError ?: "Error loading groups",
                                    color = MaterialTheme.colors.error
                                )
                                Button(
                                    onClick = {
                                        groupError = null
                                        fetchGroups()
                                    },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    groups.isEmpty() -> {
                        Card(
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "No groups found with the bot",
                                    style = MaterialTheme.typography.subtitle1
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "To get started:",
                                    style = MaterialTheme.typography.body2
                                )
                                Text("1. Create or choose a Telegram group", style = MaterialTheme.typography.body2)
                                Text("2. Add the bot to that group", style = MaterialTheme.typography.body2)
                                Text("3. Send /start in the group", style = MaterialTheme.typography.body2)
                                Text("4. Come back here and refresh", style = MaterialTheme.typography.body2)

                                Row(
                                    modifier = Modifier.padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            botInfo?.let { info ->
                                                telegramBotHelper.openTelegramToAddBot(context, info.username)
                                            }
                                        },
                                        enabled = botInfo != null
                                    ) {
                                        Text("Open Bot in Telegram")
                                    }

                                    OutlinedButton(onClick = { fetchGroups() }) {
                                        Text("Refresh")
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Show group selection
                        Text("Select the group where you want to send position updates:")

                        DropdownMenuSpinner(
                            items = groups.map { SpinnerItem.Item(it.title) },
                            selectedItem = telegramGroupName.ifEmpty { "Select Group" },
                            onItemSelected = { selectedTitle ->
                                groups.find { it.title == selectedTitle }?.let { group ->
                                    telegramChatId = group.chatId
                                    telegramGroupName = group.title
                                    selectedGroup = group
                                    // Always check bot status when a group is manually selected
                                    checkBotInSelectedGroup()
                                }
                            },
                            label = "Telegram Group",
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Show bot status for selected group
                        selectedGroup?.let { group ->
                            when {
                                isCheckingBot -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                        Text("Checking bot status...")
                                    }
                                }

                                !group.isBotMember -> {
                                    Card(
                                        backgroundColor = MaterialTheme.colors.secondary.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Bot Setup Required",
                                                style = MaterialTheme.typography.subtitle1,
                                                color = MaterialTheme.colors.secondary
                                            )
                                            Text("The bot needs to be added to this group.")
                                            Row(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { showBotSetupDialog = true }
                                                ) {
                                                    Text("Add Bot to Group")
                                                }
                                                OutlinedButton(
                                                    onClick = { checkBotInSelectedGroup() }
                                                ) {
                                                    Text("Refresh Status")
                                                }
                                            }
                                        }
                                    }
                                }

                                group.isBotMember && !group.isBotActive -> {
                                    Card(
                                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Activate Bot",
                                                style = MaterialTheme.typography.subtitle1,
                                                color = MaterialTheme.colors.primary
                                            )
                                            Text("The bot is in the group but needs to be activated.")

                                            if (isSendingStart) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 8.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                                    Text("Activating bot...")
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { sendStartCommand() }
                                                    ) {
                                                        Text("Activate Bot")
                                                    }
                                                    OutlinedButton(
                                                        onClick = { checkBotInSelectedGroup() }
                                                    ) {
                                                        Text("Refresh Status")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                group.isBotMember && group.isBotActive -> {
                                    Card(
                                        backgroundColor = Color.Green.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow, // You might want to use a checkmark icon
                                                    contentDescription = "Ready",
                                                    tint = Color.Green
                                                )
                                                Text(
                                                    text = "Ready to send position updates!",
                                                    color = Color.Green,
                                                    style = MaterialTheme.typography.subtitle1
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { checkBotInSelectedGroup() }
                                            ) {
                                                Text("Refresh")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onAdd(telegramChatId)
                        },
                        enabled = selectedGroup?.isBotMember == true && selectedGroup?.isBotActive == true
                    ) {
                        Text(stringResource(id = R.string.confirm))
                    }
                }
            }
        }
    }
}