package com.example.desl_app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.desl_app.ui.home.HomeViewModel
import com.example.desl_app.ui.home.MainActivity
import com.example.desl_app.ui.images.ImagesScreen
import com.example.desl_app.ui.settings.SettingsScreen
import com.example.desl_app.ui.settings.SettingsViewModel

@Composable
fun BottomNavGraph(
    navController: NavHostController,
    padding: PaddingValues,
    settingsVm: SettingsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screens.Home.route,
        modifier = Modifier.padding(padding)
    ) {
        composable(Screens.Home.route) {
            val homeVm: HomeViewModel = viewModel()
            MainActivity(settingsVm = settingsVm, homeVm = homeVm)
        }

        composable(Screens.Images.route) {
            ImagesScreen()
        }

        composable(Screens.Settings.route) {
            SettingsScreen(settingsVm = settingsVm)
        }
    }
}