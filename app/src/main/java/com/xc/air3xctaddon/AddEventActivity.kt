package com.xc.air3xctaddon

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.MainViewModel
import com.xc.air3xctaddon.MainViewModel.EventItem
import com.xc.air3xctaddon.MainViewModelFactory

class AddEventActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                AddEventScreen()
            }
        }
    }
}

@Composable
fun AddEventScreen(viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current.applicationContext as android.app.Application))) {
    val context = LocalContext.current
    val availableEvents by remember { derivedStateOf { viewModel.getAvailableEvents() } }
    val categories = availableEvents.filterIsInstance<EventItem.Category>().map { it.name }

    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var eventName by remember { mutableStateOf("") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Log.d("AddEventScreen", "Categories: $categories, Selected: $selectedCategory, EventName: $eventName")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Event") }
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
            // Category Dropdown
            Box {
                Button(
                    onClick = { categoryMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (selectedCategory.isEmpty()) "Select Category" else selectedCategory,
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
                            content = { Text("No categories available") },
                            onClick = { categoryMenuExpanded = false }
                        )
                    } else {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                content = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    categoryMenuExpanded = false
                                    Log.d("AddEventScreen", "Selected category: $category")
                                }
                            )
                        }
                    }
                }
            }

            // Event Name Input
            OutlinedTextField(
                value = eventName,
                onValueChange = { eventName = it },
                label = { Text("Event Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Confirm Button
            Button(
                onClick = {
                    if (selectedCategory.isNotEmpty() && eventName.isNotEmpty()) {
                        viewModel.addEvent(selectedCategory, eventName)
                        Log.d("AddEventScreen", "Confirm clicked: Adding event '$eventName' to category '$selectedCategory'")
                        (LocalContext.current as ComponentActivity).finish()
                    } else {
                        Log.w("AddEventScreen", "Cannot add event: Category or event name empty")
                        message = "Please select a category and enter an event name."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCategory.isNotEmpty() && eventName.isNotEmpty()
            ) {
                Text("Confirm")
            }

            // Display message if not null
            message?.let {
                Text(it, modifier = Modifier.padding(8.dp))
            }
        }
    }
}