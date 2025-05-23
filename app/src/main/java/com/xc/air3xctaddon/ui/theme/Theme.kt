package com.xc.air3xctaddon.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorPalette = lightColors(
    primary = Color(0xFFFF6D00),
    primaryVariant = Color(0xFFDD2C00),
    secondary = Color(0xFF2C387A)
)

@Composable
fun AIR3XCTAddonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColorPalette,
        content = content
    )
}