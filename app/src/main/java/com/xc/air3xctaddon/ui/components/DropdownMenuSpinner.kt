package com.xc.air3xctaddon.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xc.air3xctaddon.R

sealed class SpinnerItem {
    data class Header(val name: String) : SpinnerItem()
    data class Item(val name: String) : SpinnerItem()
}

@Composable
fun DropdownMenuSpinner(
    items: List<SpinnerItem>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    label: String,
    context: Context,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(selectedItem) }

    Log.d("DropdownMenuSpinner", context.getString(R.string.log_dropdown_rendering, items.toString(), selected, expanded))

    Box(
        modifier = modifier
            .background(MaterialTheme.colors.surface)
            .focusable()
    ) {
        Text(
            text = selected,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(8.dp)
                .clickable {
                    expanded = true
                    Log.d("DropdownMenuSpinner", context.getString(R.string.log_dropdown_text_clicked))
                },
            fontSize = 14.sp,
            textAlign = TextAlign.Start
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                Log.d("DropdownMenuSpinner", context.getString(R.string.log_dropdown_dismissed))
            },
            modifier = Modifier
                .width(800.dp)
                .heightIn(max = 300.dp)
        ) {
            if (items.isEmpty()) {
                DropdownMenuItem(
                    content = { Text(stringResource(R.string.no_items_available)) },
                    onClick = {
                        expanded = false
                        Log.d("DropdownMenuSpinner", context.getString(R.string.log_dropdown_no_items_clicked))
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    items.forEach { item ->
                        when (item) {
                            is SpinnerItem.Header -> {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.subtitle1.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colors.primary
                                )
                            }
                            is SpinnerItem.Item -> {
                                DropdownMenuItem(
                                    content = { Text(item.name) },
                                    onClick = {
                                        selected = item.name
                                        onItemSelected(item.name)
                                        expanded = false
                                        Log.d("DropdownMenuSpinner", context.getString(R.string.log_dropdown_item_selected, item.name))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}