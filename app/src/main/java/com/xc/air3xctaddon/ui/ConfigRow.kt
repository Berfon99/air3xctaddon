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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.xc.air3xctaddon.EventConfig
import com.xc.air3xctaddon.MainViewModel.EventItem
import com.xc.air3xctaddon.VolumeType
import com.xc.air3xctaddon.model.SoundFilesState
import com.xc.air3xctaddon.ui.components.DragHandle
import com.xc.air3xctaddon.ui.components.SpinnerItem
import com.xc.air3xctaddon.ui.components.DropdownMenuSpinner
import com.xc.air3xctaddon.ui.theme.RowBackground
import com.xc.air3xctaddon.ui.theme.SoundFieldBackground
import java.io.File

@Composable
fun ConfigRow(
    config: EventConfig,
    availableEvents: List<EventItem>,
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
    var soundFile by remember(config.soundFile) { mutableStateOf(config.soundFile) }
    var volumeType by remember { mutableStateOf(config.volumeType) }
    var volumePercentage by remember { mutableStateOf(config.volumePercentage) }
    var playCount by remember { mutableStateOf(config.playCount) }
    var forceRecompose by remember { mutableStateOf(0) }
    var eventMenuExpanded by remember { mutableStateOf(false) }
    var soundMenuExpanded by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val context = LocalContext.current

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
            Log.d("ConfigRow", "Available sound files: $soundFiles")
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
            Log.d("ConfigRow", "Calculated volume: $volume")

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
            // Drag Handle
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

            // Main Row Content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event dropdown with categories
                Box {
                    Button(
                        onClick = {
                            Log.d("ConfigRow", "Event button clicked")
                            eventMenuExpanded = true
                        },
                        modifier = Modifier
                            .width(240.dp)
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
                            .width(240.dp)
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
                                    is EventItem.Category -> {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.subtitle1.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colors.primary
                                        )
                                    }
                                    is EventItem.Event -> {
                                        DropdownMenuItem(
                                            content = {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.body2 // Smaller text for events (~14sp)
                                                )
                                            },
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Button(
                            onClick = {
                                Log.d("ConfigRow", "Sound button clicked")
                                soundMenuExpanded = true
                            },
                            modifier = Modifier
                                .width(240.dp)
                                .focusable()
                                .zIndex(1f)
                                .background(SoundFieldBackground)
                        ) {
                            Text(
                                text = if (soundFile.isEmpty()) "Select Sound" else soundFile,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = soundMenuExpanded,
                            onDismissRequest = { soundMenuExpanded = false },
                            modifier = Modifier
                                .width(240.dp)
                                .heightIn(max = 300.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth()
                            ) {
                                when (val state = soundFilesState) {
                                    is SoundFilesState.Loading -> {
                                        DropdownMenuItem(
                                            content = { Text("Loading...") },
                                            onClick = { /* Do nothing */ }
                                        )
                                    }
                                    is SoundFilesState.Success -> {
                                        state.files.forEach { fileName ->
                                            DropdownMenuItem(
                                                content = { Text(fileName) },
                                                onClick = {
                                                    soundFile = fileName
                                                    onUpdate(config.copy(soundFile = fileName))
                                                    soundMenuExpanded = false
                                                    Log.d("ConfigRow", "Selected sound file: $fileName")
                                                    forceRecompose += 1
                                                }
                                            )
                                        }
                                    }
                                    is SoundFilesState.Empty -> {
                                        DropdownMenuItem(
                                            content = { Text("No sound files") },
                                            onClick = {
                                                soundMenuExpanded = false
                                                Log.d("ConfigRow", "No sound files selected")
                                            }
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
                    }
                    IconButton(
                        onClick = {
                            Log.d("ConfigRow", "Play button clicked for file: $soundFile")
                            playSound(soundFile, volumeType, volumePercentage, playCount)
                        },
                        enabled = soundFile.isNotEmpty(),
                        modifier = Modifier
                            .size(36.dp)
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Sound",
                            tint = if (soundFile.isNotEmpty()) MaterialTheme.colors.primary else Color.Gray
                        )
                    }
                    IconButton(
                        onClick = {
                            Log.d("ConfigRow", "Stop button clicked for file: $soundFile")
                            stopSound()
                        },
                        enabled = mediaPlayer != null && mediaPlayer?.isPlaying == true,
                        modifier = Modifier
                            .size(36.dp)
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop Sound",
                            tint = if (mediaPlayer != null && mediaPlayer?.isPlaying == true) MaterialTheme.colors.primary else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

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
                        onUpdate(config.copy(volumeType = volumeType, volumePercentage = volumePercentage))
                        Log.d("ConfigRow", "Volume selected: $selected, volumeType: $volumeType, volumePercentage: $volumePercentage")
                    },
                    label = "Volume",
                    modifier = Modifier.width(100.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    modifier = Modifier.width(93.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Count",
                        fontSize = 14.sp,
                        color = Color.White, // Changed to white
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    DropdownMenuSpinner(
                        items = listOf(
                            SpinnerItem.Item("1"),
                            SpinnerItem.Item("2"),
                            SpinnerItem.Item("3"),
                            SpinnerItem.Item("4"),
                            SpinnerItem.Item("5")
                        ),
                        selectedItem = playCount.toString(),
                        onItemSelected = { selected ->
                            playCount = selected.toInt()
                            onUpdate(config.copy(playCount = playCount))
                            Log.d("ConfigRow", "Play count selected: $selected")
                        },
                        label = "",
                        modifier = Modifier.width(73.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = {
                    Log.d("ConfigRow", "Delete button clicked")
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White // Changed to white
                    )
                }
            }
        }
    }
}