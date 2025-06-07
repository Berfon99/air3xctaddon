package com.xc.air3xctaddon

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewModelScope
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddButtonEventActivity : ComponentActivity() {
    private var isListening by mutableStateOf(false)
    private var lastKeyCode by mutableStateOf("None")
    private var refreshTrigger by mutableStateOf(0)
    private val viewModel: MainViewModel by viewModels()

    data class ButtonEvent(
        val keyCode: String,
        val designation: String,
        val comment: String,
        val isChecked: Boolean,
        val eventName: String // e.g., "BUTTON_24"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataStoreSingleton.initialize(applicationContext)
        setContent {
            AIR3XCTAddonTheme {
                ButtonEventScreen(
                    isListening = isListening,
                    lastKeyCode = lastKeyCode,
                    viewModel = viewModel,
                    refreshTrigger = refreshTrigger,
                    onToggleListening = { toggleListening() },
                    onAddButtonEvent = {
                        if (lastKeyCode != "None") {
                            val keyCode = lastKeyCode
                            val designation = getButtonDesignation(keyCode.toInt())
                            val eventName = "BUTTON_$keyCode"
                            viewModel.addEvent("Button Events", eventName)
                            viewModel.viewModelScope.launch {
                                withContext(Dispatchers.IO) {
                                    DataStoreSingleton.getDataStore().edit { preferences ->
                                        preferences[stringPreferencesKey("${eventName}_comment")] = ""
                                        preferences[booleanPreferencesKey("${eventName}_isChecked")] = true
                                    }
                                }
                            }
                            lastKeyCode = "None" // Reset after adding
                            Log.d("AddButtonEventActivity", "Added button event: keyCode=$keyCode, designation=$designation, eventName=$eventName")
                        }
                    },
                    onUpdateComment = { eventName, comment ->
                        viewModel.viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                DataStoreSingleton.getDataStore().edit { preferences ->
                                    preferences[stringPreferencesKey("${eventName}_comment")] = comment
                                }
                            }
                            Log.d("AddButtonEventActivity", "Updated comment for $eventName: $comment")
                        }
                    },
                    onUpdateChecked = { eventName, isChecked ->
                        viewModel.viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                DataStoreSingleton.getDataStore().edit { preferences ->
                                    preferences[booleanPreferencesKey("${eventName}_isChecked")] = isChecked
                                }
                            }
                            Log.d("AddButtonEventActivity", "Updated isChecked for $eventName: $isChecked")
                        }
                        refreshTrigger++
                    },
                    onDeleteButtonEvent = { eventName ->
                        viewModel.deleteEvent(eventName)
                        viewModel.viewModelScope.launch {
                            withContext(Dispatchers.IO) {
                                DataStoreSingleton.getDataStore().edit { preferences ->
                                    preferences.remove(stringPreferencesKey("${eventName}_comment"))
                                    preferences.remove(booleanPreferencesKey("${eventName}_isChecked"))
                                }
                            }
                            Log.d("AddButtonEventActivity", "Deleted button event: $eventName")
                        }
                    }
                )
            }
        }
    }

    private fun toggleListening() {
        isListening = !isListening
        if (!isListening) {
            lastKeyCode = "None"
        }
        Log.d("AddButtonEventActivity", "Listening state changed: $isListening")
    }

    private fun getButtonDesignation(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> "Volume +"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume -"
            KeyEvent.KEYCODE_BACK -> "Back"
            KeyEvent.KEYCODE_MENU -> "Menu"
            KeyEvent.KEYCODE_POWER -> "Power"
            else -> "Button $keyCode"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isListening && event?.action == KeyEvent.ACTION_DOWN) {
            lastKeyCode = keyCode.toString()
            isListening = false // Stop listening automatically after detecting a key
            Log.d("AddButtonEventActivity", "Key event detected: keyCode=$keyCode")
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun ButtonEventScreen(
    isListening: Boolean,
    lastKeyCode: String,
    viewModel: MainViewModel,
    refreshTrigger: Int,
    onToggleListening: () -> Unit,
    onAddButtonEvent: () -> Unit,
    onUpdateComment: (String, String) -> Unit,
    onUpdateChecked: (String, Boolean) -> Unit,
    onDeleteButtonEvent: (String) -> Unit
) {
    val events by viewModel.events.collectAsState()
    var buttonEvents by remember { mutableStateOf<List<AddButtonEventActivity.ButtonEvent>>(emptyList()) }

    // Load button events asynchronously when events change or when refresh is triggered
    LaunchedEffect(events, refreshTrigger) {
        val eventItems = events.filterIsInstance<MainViewModel.EventItem.Event>()
            .filter { event ->
                event.name.startsWith("BUTTON_") &&
                        event.name != "BUTTON_CLICK" && // Exclude BUTTON_CLICK
                        event.name.removePrefix("BUTTON_").toIntOrNull() != null // Only include events with numeric keyCodes
            }

        val loadedEvents = eventItems.map { event ->
            val keyCode = event.name.removePrefix("BUTTON_")
            val designation = when (keyCode.toIntOrNull()) {
                KeyEvent.KEYCODE_VOLUME_UP -> "Volume +"
                KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume -"
                KeyEvent.KEYCODE_BACK -> "Back"
                KeyEvent.KEYCODE_MENU -> "Menu"
                KeyEvent.KEYCODE_POWER -> "Power"
                else -> "Button $keyCode"
            }

            // Load comment and isChecked from DataStore
            val dataStore = DataStoreSingleton.getDataStore()
            val preferences = dataStore.data.first()
            val comment = preferences[stringPreferencesKey("${event.name}_comment")] ?: ""
            val isChecked = preferences[booleanPreferencesKey("${event.name}_isChecked")] ?: true

            AddButtonEventActivity.ButtonEvent(
                keyCode = keyCode,
                designation = designation,
                comment = comment,
                isChecked = isChecked,
                eventName = event.name
            )
        }
        buttonEvents = loadedEvents
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onToggleListening() }) {
                Text(text = if (isListening) stringResource(R.string.stop_listening) else stringResource(R.string.start_listening))
            }
            Text(
                text = stringResource(R.string.button_id_label, lastKeyCode),
                style = MaterialTheme.typography.body1
            )
            if (lastKeyCode != "None") {
                IconButton(onClick = { onAddButtonEvent() }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_button),
                        tint = MaterialTheme.colors.primary
                    )
                }
            }
        }
        // Display list of added button events (only show actual events, not headers)
        if (buttonEvents.isNotEmpty()) {
            buttonEvents.forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.keyCode,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = event.designation,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = event.comment,
                        onValueChange = { onUpdateComment(event.eventName, it) },
                        label = { Text(text = stringResource(R.string.comment_label)) },
                        modifier = Modifier.weight(2f)
                    )
                    Checkbox(
                        checked = event.isChecked,
                        onCheckedChange = { newCheckedState ->
                            onUpdateChecked(event.eventName, newCheckedState)
                        }
                    )
                    IconButton(onClick = { onDeleteButtonEvent(event.eventName) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_button),
                            tint = MaterialTheme.colors.error
                        )
                    }
                }
            }
        }
    }
}