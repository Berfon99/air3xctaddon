package com.xc.air3xctaddon.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.AboutActivity
import com.xc.air3xctaddon.EventConfig
import com.xc.air3xctaddon.MainViewModel
import com.xc.air3xctaddon.SettingsActivity
import com.xc.air3xctaddon.VolumeType
import com.xc.air3xctaddon.MainViewModelFactory

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current.applicationContext as android.app.Application))) {
    val configs by viewModel.configs.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val availableEvents by remember { derivedStateOf { viewModel.getAvailableEvents() } }

    // Drag state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeight: Dp = 48.dp // Approximate height of a ConfigRow
    val rowHeightPx: Float = LocalDensity.current.run { rowHeight.toPx() }

    Log.d("MainScreen", "Configs: $configs, AvailableEvents: $availableEvents")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AIR3 XCT Addon") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            content = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            }
                        )
                        DropdownMenuItem(
                            content = { Text("About") },
                            onClick = {
                                showMenu = false
                                context.startActivity(Intent(context, AboutActivity::class.java))
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Liste des configurations
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(max = 1000.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(configs, key = { _, config -> config.id }) { index, config ->
                    ConfigRow(
                        config = config,
                        availableEvents = availableEvents,
                        onUpdate = { updatedConfig -> viewModel.updateConfig(updatedConfig) },
                        onDelete = { viewModel.deleteConfig(config) },
                        onDrag = { _, dragAmount ->
                            dragOffset += dragAmount
                            // Check if the dragged row should swap with another
                            val offsetRows = (dragOffset / rowHeightPx).toInt()
                            val targetIndex = (index + offsetRows).coerceIn(0, configs.size - 1)
                            if (targetIndex != index && draggedIndex == index) {
                                viewModel.reorderConfigs(index, targetIndex)
                                draggedIndex = targetIndex
                                dragOffset = 0f // Reset offset after swap
                            }
                        },
                        index = index,
                        isDragging = draggedIndex == index,
                        onDragStart = { draggedIndex = index },
                        onDragEnd = {
                            draggedIndex = null
                            dragOffset = 0f
                        },
                        dragOffset = dragOffset
                    )
                }
            }

            // Bottom Row for Close and Add buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Close App Button
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                        Log.d("MainScreen", "Close app clicked")
                    },
                    modifier = Modifier
                        .weight(0.25f)
                        .padding(end = 8.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.secondary
                    )
                ) {
                    Text(
                        text = "Close",
                        color = Color.White // Changed to white
                    )
                }

                // Add Configuration Button
                if (availableEvents.isNotEmpty()) {
                    Button(
                        onClick = {
                            val selectedEvent = availableEvents.firstOrNull { it is MainViewModel.EventItem.Event } as? MainViewModel.EventItem.Event
                            val defaultSoundFile = "Airspace.wav"
                            if (selectedEvent != null) {
                                Log.d("MainScreen", "Add button clicked, adding config: event=${selectedEvent.name}, soundFile=$defaultSoundFile")
                                viewModel.addConfig(
                                    event = selectedEvent.name,
                                    soundFile = defaultSoundFile,
                                    volumeType = VolumeType.SYSTEM,
                                    volumePercentage = 100,
                                    playCount = 1
                                )
                            } else {
                                Log.w("MainScreen", "Add button clicked, but no EventItem.Event found in availableEvents")
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Configuration",
                            tint = Color.White // Changed to white
                        )
                    }
                }
            }
        }
    }
}