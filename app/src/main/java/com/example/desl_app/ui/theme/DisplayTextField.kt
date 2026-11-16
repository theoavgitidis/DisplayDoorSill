package com.example.desl_app.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DisplayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().height(120.dp),
        enabled = enabled,
        singleLine = singleLine,
        label = label?.let { { Text(it, color = TextPrimary) } },
        placeholder = placeholder?.let { { Text(it, color = TextSecondary) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = SquareShape,
        textStyle = TextStyle(
            fontSize = 40.sp,
            color = TextPrimary,
            fontFamily = DisplayFont
        ),
        colors = OutlinedTextFieldDefaults.colors(
            // Background
            unfocusedContainerColor = GreyBorder,
            focusedContainerColor = GreyBorder,
            disabledContainerColor = GreyDisabled,

            // Text
            unfocusedTextColor = TextPrimary,
            focusedTextColor = TextPrimary,
            disabledTextColor = TextSecondary,

            // Border
            unfocusedBorderColor = GreyBorder,
            focusedBorderColor = DisplayBlue,
            disabledBorderColor = GreyDisabled,

            // Cursor
            cursorColor = DisplayBlue
        )
    )
}