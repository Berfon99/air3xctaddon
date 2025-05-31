package com.xc.air3xctaddon.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xc.air3xctaddon.AppDatabase
import com.xc.air3xctaddon.Message
import com.xc.air3xctaddon.R
import kotlinx.coroutines.launch

@Composable
fun AddMessageDialog(
    onMessageSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val messageDao = AppDatabase.getDatabase(context).messageDao()
    val messages by messageDao.getAllMessages().collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<Message?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp) // Constrain dialog height for landscape
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()), // Allow scrolling if content overflows
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_message_dialog_title),
                    style = MaterialTheme.typography.h6
                )
                Text(stringResource(R.string.select_message_prompt))

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.message_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.message_content_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5
                )

                if (messages.isNotEmpty()) {
                    Text(
                        text = "Saved Messages",
                        style = MaterialTheme.typography.subtitle1,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp) // Limit LazyColumn height
                    ) {
                        LazyColumn {
                            items(messages) { message ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    elevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                title = message.title
                                                content = message.content
                                            }
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = message.title,
                                            style = MaterialTheme.typography.body1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { showDeleteConfirmation = message },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete message",
                                                tint = MaterialTheme.colors.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when {
                                title.isBlank() -> error = context.getString(R.string.error_empty_title)
                                content.isBlank() -> error = context.getString(R.string.error_empty_message)
                                else -> {
                                    coroutineScope.launch {
                                        try {
                                            messageDao.insert(Message(title = title, content = content))
                                            Log.d("AddMessageDialog", "Saved message: title=$title")
                                            onMessageSelected(title, content)
                                        } catch (e: Exception) {
                                            error = context.getString(R.string.failed_to_save_message, e.message ?: "")
                                            Log.e("AddMessageDialog", "Error saving message: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save_message))
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteConfirmation?.let { messageToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text(stringResource(R.string.delete_message_title)) },
            text = {
                Text(stringResource(R.string.delete_message_confirmation, messageToDelete.title))
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                messageDao.deleteById(messageToDelete.id)
                                Log.d("AddMessageDialog", "Deleted message: ${messageToDelete.title}")
                                showDeleteConfirmation = null
                            } catch (e: Exception) {
                                error = context.getString(R.string.failed_to_delete_message, e.message ?: "")
                                Log.e("AddMessageDialog", "Error deleting message: ${e.message}")
                                showDeleteConfirmation = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colors.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}