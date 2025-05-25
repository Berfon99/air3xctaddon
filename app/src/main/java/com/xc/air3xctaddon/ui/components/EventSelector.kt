package com.xc.air3xctaddon.ui.components

import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.MainViewModel

@Composable
fun EventSelector(
    selectedEvent: String,
    availableEvents: List<MainViewModel.EventItem>,
    onEventSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var eventMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = {
                Log.d("EventSelector", "Event button clicked")
                eventMenuExpanded = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusable()
        ) {
            Text(
                text = selectedEvent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = eventMenuExpanded,
            onDismissRequest = { eventMenuExpanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                availableEvents.forEach { item ->
                    when (item) {
                        is MainViewModel.EventItem.Category -> {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.subtitle1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colors.primary
                            )
                        }
                        is MainViewModel.EventItem.Event -> {
                            DropdownMenuItem(
                                content = { Text(item.name) },
                                onClick = {
                                    onEventSelected(item.name)
                                    eventMenuExpanded = false
                                    Log.d("EventSelector", "Selected event: ${item.name}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}