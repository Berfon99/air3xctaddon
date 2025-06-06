package com.xc.air3xctaddon

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.MainViewModel.EventItem
import com.xc.air3xctaddon.MainViewModelFactory
import kotlinx.coroutines.launch

class AddXCTrackEventActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                AddXCTrackEventScreen()
            }
        }
    }
}

@Composable
fun AddXCTrackEventScreen() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(context.applicationContext as android.app.Application)
    )

    val events by viewModel.events.collectAsState(initial = emptyList())
    val availableEvents by remember { derivedStateOf { viewModel.getAvailableEvents() } }
    val categories = availableEvents.filterIsInstance<EventItem.Category>().map { it.name }

    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var eventName by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Log.d("AddXCTrackEventScreen", "Events: ${events.size}, AvailableEvents: ${availableEvents.size}, Categories: $categories")
    if (categories.isEmpty()) {
        Log.w("AddXCTrackEventScreen", "No categories available; check MainViewModel initialization or database")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_add_xctrack_event)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.no_categories_retry),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colors.error
                )
            }

            Box {
                Button(
                    onClick = { categoryMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = categories.isNotEmpty()
                ) {
                    Text(
                        text = if (selectedCategory.isEmpty()) stringResource(R.string.select_category) else stringResource(R.string.category_label, selectedCategory),
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    if (categories.isEmpty()) {
                        DropdownMenuItem(
                            content = { Text(stringResource(R.string.no_categories)) },
                            onClick = { categoryMenuExpanded = false }
                        )
                    } else {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                content = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryMenuExpanded = false
                                    Log.d("AddXCTrackEventScreen", "Selected category: $category")
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                label = { Text(stringResource(R.string.event_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    if (selectedCategory.isNotEmpty() && eventName.isNotEmpty()) {
                        scope.launch {
                            viewModel.addEvent(selectedCategory, eventName)
                            Log.d("AddXCTrackEventScreen", "Confirm clicked: Adding event '$eventName' to category '$selectedCategory'")
                            val intent = Intent(context, LogMonitorService::class.java)
                            context.stopService(intent)
                            context.startService(intent)
                            Log.d("AddXCTrackEventScreen", "Restarted LogMonitorService for new event: $eventName")
                            kotlinx.coroutines.delay(100)
                            (context as ComponentActivity).finish()
                        }
                    } else {
                        Log.w("AddXCTrackEventScreen", "Cannot add event: Category or event name empty")
                        message = "Please select a category and enter an event name."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCategory.isNotEmpty() && eventName.isNotEmpty()
            ) {
                Text(stringResource(R.string.confirm))
            }

            message?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Text(
                stringResource(
                    R.string.debug_events,
                    events.filterIsInstance<EventItem.Event>().size,
                    categories.size
                ),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}