package com.example.desl_app.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

@Composable
fun DisplayHeader(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes leftLogoRes: Int? = null,
    @DrawableRes rightLogoRes: Int? = null,
    logoSize: Dp = 64.dp
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Logo
        if (leftLogoRes != null) {
            Image(
                painter = painterResource(id = leftLogoRes),
                contentDescription = "Left Logo",
                modifier = Modifier.size(logoSize)
            )
        } else {
            Spacer(modifier = Modifier.size(logoSize))
        }

        // Title
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = TextPrimary,
            fontSize = 68.sp,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Right Logo
        if (rightLogoRes != null) {
            Image(
                painter = painterResource(id = rightLogoRes),
                contentDescription = "Right Logo",
                modifier = Modifier.size(logoSize)
            )
        } else {
            Spacer(modifier = Modifier.size(logoSize))
        }
    }
}