package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(context.applicationContext as android.app.Application)
    )
    // Observe events to ensure UI gets updated when returning from AddEventActivity
    val events by viewModel.events.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    context.startActivity(Intent(context, AddEventActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add a new event")
            }

            // Display count of custom events (optional)
            val customEventCount = events.count { it is MainViewModel.EventItem.Event }
            Text("Total Events: $customEventCount")

            Text("Settings Activity - More to be implemented")
        }
    }
}