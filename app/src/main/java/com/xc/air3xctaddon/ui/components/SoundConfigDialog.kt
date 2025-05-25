package com.xc.air3xctaddon.ui.components

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xc.air3xctaddon.R
import com.xc.air3xctaddon.VolumeType
import com.xc.air3xctaddon.model.SoundFilesState
import com.xc.air3xctaddon.ui.theme.SoundFieldBackground
import java.io.File

@Composable
fun SoundConfigDialog(
    soundFile: String,
    volumeType: VolumeType,
    volumePercentage: Int,
    playCount: Int,
    onSoundFileChanged: (String) -> Unit,
    onVolumeTypeChanged: (VolumeType) -> Unit,
    onVolumePercentageChanged: (Int) -> Unit,
    onPlayCountChanged: (Int) -> Unit,
    onPlaySound: () -> Unit,
    onStopSound: () -> Unit,
    mediaPlayer: MediaPlayer?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var soundMenuExpanded by remember { mutableStateOf(false) }
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
            if (soundFiles.isEmpty()) SoundFilesState.Empty else SoundFilesState.Success(soundFiles)
        } catch (e: Exception) {
            Log.e("SoundConfigDialog", "Error accessing Sounds directory", e)
            SoundFilesState.Error("Error accessing files")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
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

                // Sound File Selection
                Box {
                    Button(
                        onClick = { soundMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
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
                            .fillMaxWidth()
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
                                            onSoundFileChanged(fileName)
                                            soundMenuExpanded = false
                                            Log.d("SoundConfigDialog", "Selected sound file: $fileName")
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

                // Play/Stop Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            Log.d("SoundConfigDialog", "Play button clicked for file: $soundFile")
                            onPlaySound()
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
                            Log.d("SoundConfigDialog", "Stop button clicked for file: $soundFile")
                            onStopSound()
                        },
                        enabled = mediaPlayer != null && mediaPlayer.isPlaying,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(id = R.string.stop),
                            tint = if (mediaPlayer != null && mediaPlayer.isPlaying) MaterialTheme.colors.primary else Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Volume Selection
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
                                    onVolumeTypeChanged(VolumeType.MAXIMUM)
                                    onVolumePercentageChanged(100)
                                }
                                "SYSTEM" -> {
                                    onVolumeTypeChanged(VolumeType.SYSTEM)
                                    onVolumePercentageChanged(100)
                                }
                                else -> {
                                    onVolumeTypeChanged(VolumeType.PERCENTAGE)
                                    onVolumePercentageChanged(selected.removeSuffix("%").toInt())
                                }
                            }
                            Log.d("SoundConfigDialog", "Volume selected: $selected")
                        },
                        label = "",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Play Count Selection
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
                            onPlayCountChanged(selected.toInt())
                            Log.d("SoundConfigDialog", "Play count selected: $selected")
                        },
                        label = "",
                        modifier = Modifier.width(100.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = soundFile.isNotEmpty()
                    ) {
                        Text(stringResource(id = R.string.confirm))
                    }
                }
            }
        }
    }
}