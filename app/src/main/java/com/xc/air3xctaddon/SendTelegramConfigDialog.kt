package com.xc.air3xctaddon.ui.components

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xc.air3xctaddon.R
import androidx.compose.foundation.focusable

@Composable
fun SendTelegramConfigDialog(
    onAdd: (String, String) -> Unit, // chatId, groupName
    onDismiss: () -> Unit
) {
    var chatId by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var chatMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Mock Telegram chat data (replace with actual data source)
    val telegramChats by produceState<List<TelegramChat>>(
        initialValue = emptyList(),
        key1 = chatMenuExpanded
    ) {
        value = try {
            // Simulate fetching Telegram chats (replace with actual logic)
            listOf(
                TelegramChat("Group1", "123456"),
                TelegramChat("Group2", "789012")
            )
        } catch (e: Exception) {
            Log.e("SendTelegramConfigDialog", "Error fetching Telegram chats", e)
            emptyList()
        }
    }

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(id = R.string.select_telegram_chat),
                    style = MaterialTheme.typography.h6
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Chat Selection
                Box {
                    Button(
                        onClick = { chatMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusable()
                    ) {
                        Text(
                            text = if (groupName.isEmpty()) stringResource(id = R.string.select_chat) else groupName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = chatMenuExpanded,
                        onDismissRequest = { chatMenuExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        if (telegramChats.isEmpty()) {
                            DropdownMenuItem(
                                content = { Text(stringResource(id = R.string.no_telegram_chats)) },
                                onClick = { chatMenuExpanded = false }
                            )
                        } else {
                            telegramChats.forEach { chat ->
                                DropdownMenuItem(
                                    content = { Text(chat.name) },
                                    onClick = {
                                        chatId = chat.id
                                        groupName = chat.name
                                        chatMenuExpanded = false
                                        Log.d("SendTelegramConfigDialog", "Selected chat: ${chat.name}")
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(id = R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onAdd(chatId, groupName)
                        },
                        enabled = chatId.isNotEmpty() && groupName.isNotEmpty()
                    ) {
                        Text(stringResource(id = R.string.confirm))
                    }
                }
            }
        }
    }
}

// Mock data class (replace with actual implementation)
data class TelegramChat(val name: String, val id: String)