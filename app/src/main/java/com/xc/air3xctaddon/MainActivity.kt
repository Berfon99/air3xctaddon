package com.xc.air3xctaddon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xc.air3xctaddon.ui.MainScreen
import com.xc.air3xctaddon.ui.theme.AIR3XCTAddonTheme
import com.xc.air3xctaddon.utils.copySoundFilesFromAssets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assets.copySoundFilesFromAssets(getExternalFilesDir(null))
        setContent {
            AIR3XCTAddonTheme {
                MainScreen()
            }
        }
        startService(android.content.Intent(this, LogMonitorService::class.java))
    }
}