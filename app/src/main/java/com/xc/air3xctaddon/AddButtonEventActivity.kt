package com.xc.air3xctaddon

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.R


class AddButtonEventActivity : ComponentActivity() {
    private var isListening by mutableStateOf(false)
    private val detectedButtons = mutableStateListOf<ButtonInfo>()

    data class ButtonInfo(val keyCode: Int, val name: String, val isMonitored: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                ButtonEventScreen(
                    isListening = isListening,
                    detectedButtons = detectedButtons,
                    onToggleListening = { toggleListening() },
                    onToggleMonitored = { index, isChecked ->
                        detectedButtons[index] = detectedButtons[index].copy(isMonitored = isChecked)
                    }
                )
            }
        }
    }

    private fun toggleListening() {
        isListening = !isListening
        Log.d("AddButtonEventActivity", "Listening state changed: $isListening")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isListening && event?.action == KeyEvent.ACTION_DOWN) {
            val buttonName = when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> "Volume +"
                KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume -"
                KeyEvent.KEYCODE_BACK -> "Back"
                KeyEvent.KEYCODE_MENU -> "Menu"
                KeyEvent.KEYCODE_POWER -> "Power"
                else -> "Button $keyCode"
            }
            detectedButtons.add(ButtonInfo(keyCode, buttonName, true))
            isListening = false // Stop listening after detecting a button
            Log.d("AddButtonEventActivity", "Key event detected: keyCode=$keyCode, name=$buttonName")
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun ButtonEventScreen(
    isListening: Boolean,
    detectedButtons: List<AddButtonEventActivity.ButtonInfo>,
    onToggleListening: () -> Unit,
    onToggleMonitored: (Int, Boolean) -> Unit
) {
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
            if (!isListening) {
                IconButton(onClick = { onToggleListening() }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.start_listening)
                    )
                }
            } else {
                Button(onClick = { onToggleListening() }) {
                    Text(stringResource(R.string.stop_listening))
                }
            }
            if (isListening) {
                Text(
                    text = stringResource(R.string.listening_for_button),
                    style = MaterialTheme.typography.body1
                )
            }
        }
        detectedButtons.forEachIndexed { index, button ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = button.isMonitored,
                    onCheckedChange = { onToggleMonitored(index, it) }
                )
                Text(
                    text = stringResource(R.string.button_info, button.keyCode, button.name),
                    style = MaterialTheme.typography.body1
                )
            }
        }
    }
}