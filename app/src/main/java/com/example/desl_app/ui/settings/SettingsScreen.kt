package com.example.desl_app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.desl_app.ui.theme.DisplayBlue
import com.example.desl_app.ui.theme.DisplayContainer
import com.example.desl_app.ui.theme.DisplayHeader
import com.example.desl_app.ui.theme.DisplayPrimaryText
import com.example.desl_app.ui.theme.DisplaySecondaryText

@Composable
fun SettingsScreen(
    settingsVm: SettingsViewModel
) {
    val developerMode by settingsVm.developerMode.collectAsState()
    val bleEnabled by settingsVm.bleEnabled.collectAsState()


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DisplayHeader(
            text = "Settings",
        )

        DisplayContainer(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DisplayPrimaryText(
                    text = "Developer Mode",
                    color = Color.Black
                )
                Switch(
                    checked = developerMode,
                    onCheckedChange = { settingsVm.setDeveloperMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DisplayBlue,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray
                    ),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        if (developerMode) {
            DisplayContainer(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BLE ON",
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Switch(
                        checked = bleEnabled,
                        onCheckedChange = { settingsVm.setBleEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = DisplayBlue,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray
                        )
                    )
                }
            }
            DisplaySecondaryText(
                text = "Developer mode is ON - debug information will be visible on Home screen",
                color = Color.Gray
            )
        }
    }
}