package com.xc.air3xctaddon.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.MainViewModel
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    AIR3XCTAddonTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            val configs by viewModel.configs.collectAsState()
            val availableEvents by remember { derivedStateOf { viewModel.getAvailableEvents() } }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 1000.dp)
            ) {
                itemsIndexed(configs) { index, config ->
                    ConfigRow(
                        config = config,
                        availableEvents = availableEvents,
                        onUpdate = { updatedConfig ->
                            viewModel.updateConfig(updatedConfig)
                        },
                        onDelete = {
                            viewModel.deleteConfig(config)
                        },
                        onDrag = { from, to ->
                            viewModel.reorderConfigs(from, to)
                        },
                        index = index
                    )
                }
            }
        }
    }
}