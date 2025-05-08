package com.xc.air3xctaddon

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
            }
        }
        // Start the foreground service
        startService(android.content.Intent(this, LogMonitorService::class.java))
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val configs by viewModel.configs.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val availableEvents by remember { derivedStateOf { viewModel.getAvailableEvents() } }

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
                                context.startActivity(android.content.Intent(context, SettingsActivity::class.java))
                            }
                        )
                        DropdownMenuItem(
                            content = { Text("About") },
                            onClick = {
                                showMenu = false
                                context.startActivity(android.content.Intent(context, AboutActivity::class.java))
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (availableEvents.isNotEmpty()) {
                FloatingActionButton(onClick = {
                    // Add default config with first available event
                    viewModel.addConfig(
                        event = availableEvents.first(),
                        soundFile = "",
                        volumeType = VolumeType.SYSTEM,
                        volumePercentage = 100,
                        playCount = 1
                    )
                    Log.d("MainScreen", "FAB clicked, added config")
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Configuration")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(configs, key = { _, config -> config.id }) { index, config ->
                ConfigRow(
                    config = config,
                    availableEvents = viewModel.getAvailableEvents() + config.event,
                    onUpdate = { updatedConfig -> viewModel.updateConfig(updatedConfig) },
                    onDelete = { viewModel.deleteConfig(config) },
                    onDrag = { from, to -> viewModel.reorderConfigs(from, to) },
                    index = index
                )
            }
        }
    }
}

@Composable
fun ConfigRow(
    config: EventConfig,
    availableEvents: List<Event>,
    onUpdate: (EventConfig) -> Unit,
    onDelete: () -> Unit,
    onDrag: (Int, Int) -> Unit,
    index: Int
) {
    var event by remember { mutableStateOf(config.event) }
    var soundFile by remember { mutableStateOf(config.soundFile) }
    var volumeType by remember { mutableStateOf(config.volumeType) }
    var volumePercentage by remember { mutableStateOf(config.volumePercentage) }
    var playCount by remember { mutableStateOf(config.playCount.toString()) }

    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Cyan) // Debug: Confirm Row is rendered
            .padding(8.dp)
            .clickable {
                Log.d("ConfigRow", "Row clicked for config: ${config.event.name}")
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Event Spinner
        DropdownMenuSpinner(
            items = availableEvents.map { it.name },
            selectedItem = event.name,
            onItemSelected = { selected ->
                event = Event.valueOf(selected)
                onUpdate(config.copy(event = event))
            },
            label = "Event"
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Sound File Button
        Button(
            onClick = {
                Log.d("ConfigRow", "Sound button clicked")
                // List files in Sounds folder
                val soundsDir = File(context.filesDir, "Sounds")
                soundsDir.mkdirs() // Ensure Sounds directory exists
                val soundFiles = soundsDir.listFiles()?.map { it.name } ?: emptyList()
                // For simplicity, pick first or empty
                soundFile = soundFiles.firstOrNull() ?: ""
                onUpdate(config.copy(soundFile = soundFile))
            },
            modifier = Modifier.background(Color.Green) // Debug: Confirm Button is rendered
        ) {
            Text(if (soundFile.isEmpty()) "Select Sound" else soundFile)
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Volume Spinner
        DropdownMenuSpinner(
            items = VolumeType.values().map { it.name } + (0..10).map { "${it * 10}%" },
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
            },
            label = "Volume"
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Play Count
        TextField(
            value = playCount,
            onValueChange = { value ->
                playCount = value
                val count = value.toIntOrNull() ?: 1
                onUpdate(config.copy(playCount = count))
            },
            label = { Text("Play Count") },
            modifier = Modifier.width(80.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Delete Button
        IconButton(onClick = {
            Log.d("ConfigRow", "Delete button clicked")
            onDelete()
        }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
fun DropdownMenuSpinner(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(selectedItem) }

    Log.d("DropdownMenuSpinner", "Rendering DropdownMenuSpinner with Items: $items, Selected: $selected, Expanded: $expanded")

    Box(
        modifier = Modifier
            .background(Color.Yellow) // Debug: Highlight clickable area
            .focusable() // Ensure touch events are receivable
    ) {
        Text(
            text = selected,
            modifier = Modifier
                .width(150.dp)
                .background(Color.White) // Debug: Ensure text is visible
                .padding(8.dp)
                .clickable {
                    expanded = true
                    Log.d("DropdownMenuSpinner", "Text clicked, expanded set to true")
                },
            fontSize = 16.sp,
            textAlign = TextAlign.Start
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                Log.d("DropdownMenuSpinner", "Dropdown dismissed, expanded set to false")
            }
        ) {
            if (items.isEmpty()) {
                DropdownMenuItem(
                    content = { Text("No events available") },
                    onClick = {
                        expanded = false
                        Log.d("DropdownMenuSpinner", "No events available clicked")
                    }
                )
            } else {
                items.forEach { item ->
                    DropdownMenuItem(
                        content = { Text(item) },
                        onClick = {
                            selected = item
                            onItemSelected(item)
                            expanded = false
                            Log.d("DropdownMenuSpinner", "Item selected: $item")
                        }
                    )
                }
            }
        }
    }
}