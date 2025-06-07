package com.xc.air3xctaddon.ui.components

import android.util.Log
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth() // Ensure left-align
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
                                style = MaterialTheme.typography.subtitle1.copy(
                                    fontWeight = if (item.level == 0) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (item.level == 0) Color(0xFF1565C0) else Color(0xFF1976D2)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = (item.level * 16).dp,
                                        top = if (item.level == 0) 8.dp else 4.dp,
                                        bottom = 4.dp,
                                        end = 8.dp
                                    )
                            )
                        }
                        is MainViewModel.EventItem.Event -> {
                            DropdownMenuItem(
                                content = {
                                    Text(
                                        text = item.displayName,
                                        modifier = Modifier.padding(start = (item.level * 16).dp)
                                    )
                                },
                                onClick = {
                                    onEventSelected(item.name)
                                    eventMenuExpanded = false
                                    Log.d("EventSelector", "Selected event: ${item.name}, displayName: ${item.displayName}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}