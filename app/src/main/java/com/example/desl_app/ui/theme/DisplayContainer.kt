package com.example.desl_app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DisplayContainer(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    borderWidth: Dp = 1.dp,
    selected: Boolean = false,
    contentAlignment: Alignment = Alignment.TopStart,
    title: String? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val borderColor = if (selected) DisplayBlue else GreyBorder

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick
        )
    } else Modifier

    DisplayBox(
        modifier = modifier
            .then(clickableModifier)
            .background(AppWhite, SquareShape)
            .border(borderWidth, borderColor, SquareShape)
            .padding(padding),
        contentAlignment = contentAlignment
    ) {
        if (title != null || subtitle != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
            ) {
                title?.let {
                    androidx.compose.material3.Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                subtitle?.let {
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Content kommt darunter
                Box(Modifier.fillMaxWidth()) { content() }
            }
        } else {
            content()
        }
    }
}
