package com.xc.air3xctaddon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Text
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIR3XCTAddonTheme {
                Text("Settings Activity - To be implemented")
            }
        }
    }
}