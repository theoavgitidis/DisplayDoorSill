package com.example.desl_app.ui.navigation

sealed class Screens(val route: String) {
    object Home : Screens("home")
    object Images : Screens("images")
    object Settings : Screens("settings")
}