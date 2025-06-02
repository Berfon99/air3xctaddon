package com.xc.air3xctaddon.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.*
import com.xc.air3xctaddon.R
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Define preference key
private val IS_AIR3_DEVICE = booleanPreferencesKey("is_air3_device")

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current.applicationContext as android.app.Application))) {
    val configs by viewModel.configs.collectAsState()
    val availableEvents by viewModel.events.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showBrandLimitDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Initialize DataStore
    LaunchedEffect(Unit) {
        DataStoreSingleton.initialize(context.applicationContext)
    }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeight: Dp = 48.dp
    val rowHeightPx: Float = LocalDensity.current.run { rowHeight.toPx() }

    // Filter out TASK_CONFIG entries for display
    val filteredConfigs = configs.filter { it.event != "TASK_CONFIG" }

    Log.d("MainScreen", context.getString(R.string.log_filtered_configs, filteredConfigs.toString(), availableEvents.toString()))

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
                        DropdownMenuItem(
                            content = { Text(stringResource(R.string.menu_settings)) },
                            onClick = {
                                showMenu = false
                                context.startActivity(Intent(context, SettingsActivity::class.java))
                            }
                        )
                        DropdownMenuItem(
                            content = { Text(stringResource(R.string.menu_about)) },
                            onClick = {
                                showMenu = false
                                context.startActivity(Intent(context, AboutActivity::class.java))
                            }
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
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
                itemsIndexed(filteredConfigs, key = { _, config -> config.id }) { index, config ->
                    ConfigRow(
                        config = config,
                        availableEvents = availableEvents,
                        onUpdate = { updatedConfig -> viewModel.updateConfig(updatedConfig) },
                        onDelete = { viewModel.deleteConfig(config) },
                        onDrag = { _, dragAmount ->
                            dragOffset += dragAmount
                            val offsetRows = (dragOffset / rowHeightPx).toInt()
                            val targetIndex = (index + offsetRows).coerceIn(0, filteredConfigs.size - 1)
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
                        onClick = {
                            // Check AIR³ status and row count
                            coroutineScope.launch {
                                val isAir3 = DataStoreSingleton.getDataStore().data
                                    .map { preferences ->
                                        preferences[IS_AIR3_DEVICE] ?: true
                                    }
                                    .first()
                                if (isAir3 || filteredConfigs.size < 1) {
                                    // Always create the row regardless of Zello installation
                                    val selectedEvent = availableEvents.firstOrNull { item -> item is MainViewModel.EventItem.Event } as? MainViewModel.EventItem.Event
                                    if (selectedEvent != null) {
                                        Log.d("MainScreen", context.getString(R.string.log_add_config_clicked, selectedEvent.name, "", ""))
                                        viewModel.addConfig(
                                            event = selectedEvent.name,
                                            taskType = "",
                                            taskData = "",
                                            volumeType = VolumeType.SYSTEM,
                                            volumePercentage = 100,
                                            playCount = 1,
                                            telegramChatId = null
                                        )
                                    } else {
                                        Log.w("MainScreen", context.getString(R.string.log_no_event_item))
                                    }
                                } else {
                                    showBrandLimitDialog = true
                                    Log.d("MainScreen", context.getString(R.string.log_non_air3_limit, filteredConfigs.size))
                                }
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
                            contentDescription = stringResource(R.string.add_config),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // Brand limitation dialog
    if (showBrandLimitDialog) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissable */ },
            title = { Text(stringResource(R.string.device_compatibility_title)) },
            text = { Text(stringResource(R.string.brand_limit_restriction)) },
            confirmButton = {
                Button(onClick = { showBrandLimitDialog = false }) {
                    Text(stringResource(R.string.ok_button))
                }
            },
            dismissButton = null // Makes dialog non-dismissable
        )
    }
}