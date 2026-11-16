package com.example.desl_app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp


val SquareShape = RoundedCornerShape(0.dp) // eckig




@Composable
fun DisplayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading, // disable while loading
        modifier = modifier.height(120.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
        shape = SquareShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = DisplayBlue,
            contentColor = AppWhite,
            disabledContainerColor = GreyDisabled,
            disabledContentColor = AppWhite
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(44.dp),
                    color = AppWhite // IMPORTANT: visible on blue
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            DisplayPrimaryText(
                text = text,   // will show "Connecting..."
                color = AppWhite
            )
        }
    }
}
