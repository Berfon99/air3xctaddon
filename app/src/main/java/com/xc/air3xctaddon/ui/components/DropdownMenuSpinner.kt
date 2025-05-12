package com.xc.air3xctaddon.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DropdownMenuSpinner(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(selectedItem) }

    Log.d("DropdownMenuSpinner", "Rendering DropdownMenuSpinner with Items: $items, Selected: $selected, Expanded: $expanded")

    Box(
        modifier = modifier
            .background(Color.Yellow)
            .focusable()
    ) {
        Text(
            text = selected,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(8.dp)
                .clickable {
                    expanded = true
                    Log.d("DropdownMenuSpinner", "Text clicked, expanded set to true")
                },
            fontSize = 14.sp,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                Log.d("DropdownMenuSpinner", "Dropdown dismissed, expanded set to false")
            }
        ) {
            if (items.isEmpty()) {
                DropdownMenuItem(
                    content = { Text("No items available") },
                    onClick = {
                        expanded = false
                        Log.d("DropdownMenuSpinner", "No items available clicked")
                    }
                )
            } else {
                items.forEach { item ->
                    DropdownMenuItem(
                        content = { Text(item) },
                        onClick = {
                            selected = item
                            onItemSelected(item)
                            expanded = false
                            Log.d("DropdownMenuSpinner", "Item selected: $item")
                        }
                    )
                }
            }
        }
    }
}