package com.example.desl_app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val screen: Screens,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screens.Home, "Home", Icons.Filled.Home),
    BottomNavItem(Screens.Images, "Images", Icons.Filled.Image),
    BottomNavItem(Screens.Settings, "Settings", Icons.Filled.Settings)
)