package com.xc.air3xctaddon

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme

class AddButtonEventActivity : ComponentActivity() {
    private var isListening by mutableStateOf(false)
    private var lastKeyCode by mutableStateOf("None")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                ButtonEventScreen(
                    isListening = isListening,
                    lastKeyCode = lastKeyCode,
                    onToggleListening = { toggleListening() }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isListening && event?.action == KeyEvent.ACTION_DOWN) {
            lastKeyCode = keyCode.toString()
            Log.d("AddButtonEventActivity", "Key event detected: keyCode=$keyCode")
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun ButtonEventScreen(
    isListening: Boolean,
    lastKeyCode: String,
    onToggleListening: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { onToggleListening() }) {
            Text(if (isListening) stringResource(R.string.stop_listening) else stringResource(R.string.start_listening))
        }
        Text(
            text = stringResource(R.string.button_id_label, lastKeyCode),
            style = MaterialTheme.typography.body1
        )
    }
}