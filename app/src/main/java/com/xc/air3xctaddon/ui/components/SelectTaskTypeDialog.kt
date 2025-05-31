package com.xc.air3xctaddon.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.xc.air3xctaddon.R

@Composable
fun SelectTaskTypeDialog(
    onLaunchAppSelected: () -> Unit,
    onTelegramSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.select_task_type),
                    style = MaterialTheme.typography.h6
                )
                Button(
                    onClick = onTelegramSelected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.send_position_to_telegram))
                }
                Button(
                    onClick = onLaunchAppSelected,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(id = R.string.launch_an_app))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        }
    }
}