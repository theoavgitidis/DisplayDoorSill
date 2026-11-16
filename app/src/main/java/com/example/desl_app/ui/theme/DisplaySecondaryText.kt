package com.example.desl_app.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun DisplaySecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    style: TextStyle = TextStyle(
        fontSize = 38.sp,
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Normal,
        color = color
    )
) {
    Text(
        text = text,
        modifier = modifier,
        style = style
    )
}