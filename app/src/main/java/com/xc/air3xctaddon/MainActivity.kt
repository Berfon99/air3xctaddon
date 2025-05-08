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
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Copy sound files from assets to external storage at startup
        copySoundFilesFromAssets()
        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
            }
        }
        // Start the foreground service
        startService(android.content.Intent(this, LogMonitorService::class.java))
    }

    private fun copySoundFilesFromAssets() {
        val soundsDir = File(getExternalFilesDir(null), "Sounds")
        try {
            soundsDir.mkdirs()
            Log.d("MainActivity", "Sounds directory for assets: ${soundsDir.absolutePath}, exists: ${soundsDir.exists()}")
            // Check existing sound files
            val existingFiles = soundsDir.listFiles()?.map { it.name }?.filter { it.endsWith(".mp3") || it.endsWith(".wav") }?.sorted() ?: emptyList()
            Log.d("MainActivity", "Existing sound files: $existingFiles")
            if (existingFiles.isNotEmpty()) {
                Log.d("MainActivity", "Skipping copy, sound files already exist")
                return
            }
            // List files in assets/sounds
            val assetFiles = assets.list("sounds")?.filter { it.endsWith(".mp3") || it.endsWith(".wav") }?.sorted() ?: emptyList()
            Log.d("MainActivity", "Sound files in assets/sounds: $assetFiles")
            if (assetFiles.isEmpty()) {
                Log.w("MainActivity", "No .mp3 or .wav files found in assets/sounds")
                return
            }
            // Copy each file
            assetFiles.forEach { fileName ->
                val inputStream = assets.open("sounds/$fileName")
                val outputFile = File(soundsDir, fileName)
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                Log.d("MainActivity", "Copied sound file: $fileName to ${outputFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error copying sound files from assets", e)
        }
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

// Sealed class to represent the state of sound files
sealed class SoundFilesState {
    object Loading : SoundFilesState()
    data class Success(val files: List<String>) : SoundFilesState()
    object Empty : SoundFilesState()
    data class Error(val message: String) : SoundFilesState()
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
    var soundFile by remember(config.soundFile) { mutableStateOf(config.soundFile) } // Sync with config
    var volumeType by remember { mutableStateOf(config.volumeType) }
    var volumePercentage by remember { mutableStateOf(config.volumePercentage) }
    var playCount by remember { mutableStateOf(config.playCount.toString()) }
    // Workaround to force recomposition
    var forceRecompose by remember { mutableStateOf(0) }
    // State for dropdown menu
    var soundMenuExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Load sound files state
    val soundFilesState by produceState<SoundFilesState>(initialValue = SoundFilesState.Loading, key1 = soundMenuExpanded) {
        value = try {
            val soundsDir = File(context.getExternalFilesDir(null), "Sounds")
            val soundFiles = soundsDir.listFiles()?.map { it.name }?.filter { it.endsWith(".mp3") || it.endsWith(".wav") }?.sorted() ?: emptyList()
            Log.d("ConfigRow", "Available sound files: $soundFiles")
            if (soundFiles.isEmpty()) SoundFilesState.Empty else SoundFilesState.Success(soundFiles)
        } catch (e: Exception) {
            Log.e("ConfigRow", "Error accessing Sounds directory", e)
            SoundFilesState.Error("Error accessing files")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Cyan) // Debug: Confirm Row is rendered
            .padding(8.dp),
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

        // Sound File Button with Dropdown
        Box {
            Button(
                onClick = {
                    Log.d("ConfigRow", "Sound button clicked")
                    soundMenuExpanded = true
                },
                modifier = Modifier
                    .focusable() // Ensure touch events are receivable
                    .zIndex(1f) // Ensure button is not overlapped
                    .background(Color.Green) // Debug: Confirm Button is rendered
            ) {
                Text(if (soundFile.isEmpty()) "Select Sound" else soundFile)
            }
            DropdownMenu(
                expanded = soundMenuExpanded,
                onDismissRequest = { soundMenuExpanded = false }
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
                                    onUpdate(config.copy(soundFile = soundFile))
                                    soundMenuExpanded = false
                                    Log.d("ConfigRow", "Selected sound file: $fileName")
                                    Log.d("ConfigRow", "Updated config with soundFile: $soundFile")
                                    // Force recomposition
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