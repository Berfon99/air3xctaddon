package com.xc.air3xctaddon.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorPalette = lightColors(
    primary = Color(0xFF00BFA5),
    primaryVariant = Color(0xFFFBC02D),
    secondary = Color(0xFF00B8D4)
)

@Composable
fun AIR3XCTAddonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColorPalette,
        content = content
    )
}