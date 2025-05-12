package com.xc.air3xctaddon.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.AboutActivity
import com.xc.air3xctaddon.Event
import com.xc.air3xctaddon.EventConfig
import com.xc.air3xctaddon.MainViewModel
import com.xc.air3xctaddon.MainViewModelFactory
import com.xc.air3xctaddon.SettingsActivity
import com.xc.air3xctaddon.VolumeType

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current.applicationContext as Application))) {
    val configs by viewModel.configs.collectAsState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val availableEvents by remember { derivedStateOf { viewModel.getAvailableEvents() } }

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
        },
        floatingActionButton = {
            if (availableEvents.isNotEmpty()) {
                FloatingActionButton(onClick = {
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
            } else {
                Log.d("MainScreen", "FAB not shown: availableEvents is empty")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
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