package com.xc.air3xctaddon.ui

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.xc.air3xctaddon.*
import com.xc.air3xctaddon.ui.components.DropdownMenuSpinner
import com.xc.air3xctaddon.ui.components.SpinnerItem
import com.xc.air3xctaddon.R

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current.applicationContext as android.app.Application))) {
    val configs by viewModel.configs.collectAsState()
    val events by viewModel.events.collectAsState()
    val availableEvents by remember(configs, events) {
        derivedStateOf {
            val usedEvents = configs.map { it.event }.toSet()
            events.filter { item ->
                when (item) {
                    is MainViewModel.EventItem.Category -> true
                    is MainViewModel.EventItem.Event -> item.name !in usedEvents
                }
            }
        }
    }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<String?>(null) }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeight: Dp = 48.dp
    val rowHeightPx: Float = LocalDensity.current.run { rowHeight.toPx() }

    Log.d("MainScreen", "Configs: $configs, AvailableEvents: $availableEvents")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_main)) },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_settings))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(onClick = {
                            showMenu = false
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        }) {
                            Text(stringResource(R.string.menu_settings))
                        }
                        DropdownMenuItem(onClick = {
                            showMenu = false
                            context.startActivity(Intent(context, AboutActivity::class.java))
                        }) {
                            Text(stringResource(R.string.menu_about))
                        }
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
                            val offsetRows = (dragOffset / rowHeightPx).toInt()
                            val targetIndex = (index + offsetRows).coerceIn(0, configs.size - 1)
                            if (targetIndex != index && draggedIndex == index) {
                                viewModel.reorderConfigs(index, targetIndex)
                                draggedIndex = targetIndex
                                dragOffset = 0f
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        text = stringResource(R.string.close_app),
                        color = Color.White
                    )
                }

                if (availableEvents.isNotEmpty()) {
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_config),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddConfigDialog(
            availableEvents = availableEvents,
            onSoundSelected = { event ->
                selectedEvent = event
                showAddDialog = false
                showSoundDialog = true
            },
            onTelegramSelected = { event ->
                selectedEvent = event
                showAddDialog = false
                showTelegramDialog = true
            },
            onDismiss = {
                showAddDialog = false
                selectedEvent = null
            }
        )
    }

    if (showSoundDialog) {
        SoundConfigDialog(
            selectedEvent = selectedEvent,
            onAdd = { event, taskType, taskData, volumeType, volumePercentage, playCount, telegramChatId ->
                Log.d("MainScreen", "Adding Sound config: event=$event, taskType=$taskType, taskData=$taskData, volumeType=$volumeType, volumePercentage=$volumePercentage, playCount=$playCount, telegramChatId=$telegramChatId")
                viewModel.addConfig(
                    event = event,
                    taskType = taskType,
                    taskData = taskData ?: "",
                    volumeType = volumeType,
                    volumePercentage = volumePercentage,
                    playCount = playCount,
                    telegramChatId = telegramChatId ?: ""
                )
                showSoundDialog = false
                selectedEvent = null
            },
            onDismiss = {
                showSoundDialog = false
                selectedEvent = null
            }
        )
    }

    if (showTelegramDialog) {
        SendTelegramConfigDialog(
            selectedEvent = selectedEvent,
            onAdd = { event, telegramChatId ->
                Log.d("MainScreen", "Adding Telegram config: event=$event, telegramChatId=$telegramChatId")
                viewModel.addConfig(
                    event = event,
                    taskType = "SendTelegramPosition",
                    taskData = "Send Telegram Position",
                    volumeType = VolumeType.SYSTEM,
                    volumePercentage = 100,
                    playCount = 1,
                    telegramChatId = telegramChatId
                )
                showTelegramDialog = false
                selectedEvent = null
            },
            onDismiss = {
                showTelegramDialog = false
                selectedEvent = null
            }
        )
    }
}

@Composable
fun AddConfigDialog(
    availableEvents: List<MainViewModel.EventItem>,
    onSoundSelected: (String) -> Unit,
    onTelegramSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEvent by remember { mutableStateOf<String?>(null) }
    var taskType by remember { mutableStateOf("Sound") }
    var showEventDropdown by remember { mutableStateOf(false) }

    // Log available events and selected event
    LaunchedEffect(availableEvents) {
        Log.d("AddConfigDialog", "Available events: $availableEvents")
    }
    LaunchedEffect(selectedEvent) {
        Log.d("AddConfigDialog", "Selected event: $selectedEvent")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_config),
                    style = MaterialTheme.typography.h6
                )

                // Event selection using TextField and DropdownMenu
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = selectedEvent ?: "",
                        onValueChange = {},
                        label = { Text("Event") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showEventDropdown = !showEventDropdown }) {
                                Icon(
                                    imageVector = if (showEventDropdown) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                    contentDescription = "Toggle dropdown"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = showEventDropdown,
                        onDismissRequest = { showEventDropdown = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val eventItems = availableEvents.filterIsInstance<MainViewModel.EventItem.Event>()
                        if (eventItems.isEmpty()) {
                            DropdownMenuItem(onClick = {}) {
                                Text("No events available")
                            }
                        } else {
                            eventItems.forEach { event ->
                                DropdownMenuItem(onClick = {
                                    selectedEvent = event.name
                                    showEventDropdown = false
                                    Log.d("AddConfigDialog", "Event selected: ${event.name}")
                                }) {
                                    Text(event.name)
                                }
                            }
                        }
                    }
                }

                // Task type selection using DropdownMenuSpinner
                DropdownMenuSpinner(
                    items = listOf("Sound", "SendTelegramPosition").map { SpinnerItem.Item(it) },
                    selectedItem = taskType,
                    onItemSelected = { taskType = it },
                    label = "Task Type",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedEvent?.let { event ->
                                when (taskType) {
                                    "Sound" -> onSoundSelected(event)
                                    "SendTelegramPosition" -> onTelegramSelected(event)
                                }
                            }
                        },
                        enabled = selectedEvent != null
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

@Composable
fun SoundConfigDialog(
    selectedEvent: String?,
    onAdd: (String, String, String?, VolumeType, Int, Int, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var taskData by remember { mutableStateOf("") }
    var volumeType by remember { mutableStateOf(VolumeType.SYSTEM) }
    var volumePercentage by remember { mutableStateOf("100") }
    var playCount by remember { mutableStateOf("1") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_config),
                    style = MaterialTheme.typography.h6
                )

                // Event display (non-editable, pre-selected)
                TextField(
                    value = selectedEvent ?: "",
                    onValueChange = {},
                    label = { Text("Event") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                // Task data (sound file)
                TextField(
                    value = taskData,
                    onValueChange = { taskData = it },
                    label = { Text("Sound File (e.g., Airspace.wav)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Volume type
                DropdownMenuSpinner(
                    items = VolumeType.values().map { SpinnerItem.Item(it.name) },
                    selectedItem = volumeType.name,
                    onItemSelected = { volumeType = VolumeType.valueOf(it) },
                    label = "Volume Type",
                    modifier = Modifier.fillMaxWidth()
                )

                // Volume percentage
                TextField(
                    value = volumePercentage,
                    onValueChange = { volumePercentage = it },
                    label = { Text("Volume Percentage") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Play count
                TextField(
                    value = playCount,
                    onValueChange = { playCount = it },
                    label = { Text("Play Count") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedEvent?.let { event ->
                                onAdd(
                                    event,
                                    "Sound",
                                    if (taskData.isNotBlank()) taskData else null,
                                    volumeType,
                                    volumePercentage.toIntOrNull() ?: 100,
                                    playCount.toIntOrNull() ?: 1,
                                    null
                                )
                            }
                        },
                        enabled = selectedEvent != null && taskData.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun SendTelegramConfigDialog(
    selectedEvent: String?,
    onAdd: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var telegramChatId by remember { mutableStateOf("") }
    var telegramGroupName by remember { mutableStateOf("") }
    var isLoadingGroups by remember { mutableStateOf(false) }
    var groupError by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<TelegramGroup>>(emptyList()) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val telegramBotHelper = remember { TelegramBotHelper(BuildConfig.TELEGRAM_BOT_TOKEN, fusedLocationClient) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (!isGranted) {
            groupError = "Location permission denied. Please grant permission to select a group."
        } else {
            isLoadingGroups = true
            telegramBotHelper.fetchGroups(
                onResult = { fetchedGroups ->
                    groups = fetchedGroups
                    isLoadingGroups = false
                    if (fetchedGroups.isEmpty()) {
                        groupError = "No groups found. Send /start in a group with @AIR3SendPositionBot."
                    } else {
                        groupError = null
                    }
                },
                onError = { error ->
                    isLoadingGroups = false
                    groupError = error
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Configure Telegram Position",
                    style = MaterialTheme.typography.h6
                )

                // Event display (non-editable, pre-selected)
                TextField(
                    value = selectedEvent ?: "",
                    onValueChange = {},
                    label = { Text("Event") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                )

                // Telegram group selection
                Text(
                    text = "Send /start to @AIR3SendPositionBot in a group to select it.",
                    style = MaterialTheme.typography.body2
                )
                if (isLoadingGroups) {
                    Text("Loading groups...")
                } else if (groupError != null) {
                    Text(
                        text = groupError ?: "Error loading groups",
                        color = MaterialTheme.colors.error
                    )
                } else {
                    DropdownMenuSpinner(
                        items = groups.map { SpinnerItem.Item(it.title) },
                        selectedItem = telegramGroupName,
                        onItemSelected = { selectedTitle ->
                            groups.find { it.title == selectedTitle }?.let { group ->
                                telegramChatId = group.chatId
                                telegramGroupName = group.title
                            }
                        },
                        label = "Select Group",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedEvent?.let { event ->
                                onAdd(event, telegramChatId)
                            }
                        },
                        enabled = selectedEvent != null && telegramChatId.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}