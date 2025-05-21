package com.xc.air3xctaddon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun DragHandle(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.size(width = 36.dp, height = 32.dp)
    ) {
        val lineHeight = 4.dp.toPx()
        val lineWidth = 24.dp.toPx()
        val spacing = 4.dp.toPx()
        val yStart = (size.height - (3 * lineHeight + 2 * spacing)) / 2

        // Draw three horizontal lines
        drawLine(
            color = Color.Gray,
            start = Offset((size.width - lineWidth) / 2, yStart),
            end = Offset((size.width + lineWidth) / 2, yStart),
            strokeWidth = lineHeight
        )
        drawLine(
            color = Color.Gray,
            start = Offset((size.width - lineWidth) / 2, yStart + lineHeight + spacing),
            end = Offset((size.width + lineWidth) / 2, yStart + lineHeight + spacing),
            strokeWidth = lineHeight
        )
        drawLine(
            color = Color.Gray,
            start = Offset((size.width - lineWidth) / 2, yStart + 2 * (lineHeight + spacing)),
            end = Offset((size.width + lineWidth) / 2, yStart + 2 * (lineHeight + spacing)),
            strokeWidth = lineHeight
        )
    }
}