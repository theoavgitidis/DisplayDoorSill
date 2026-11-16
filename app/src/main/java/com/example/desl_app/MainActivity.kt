package com.example.desl_app


import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.desl_app.ui.navigation.BottomNavBar
import com.example.desl_app.ui.navigation.BottomNavGraph
import com.example.desl_app.ui.settings.SettingsViewModel
import com.example.desl_app.ui.theme.DisplayTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DisplayTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val settingsVm: SettingsViewModel = viewModel()

    Scaffold(
        bottomBar = { BottomNavBar(navController) },
    ) { padding ->
        BottomNavGraph(
            navController = navController,
            padding = padding,
            settingsVm = settingsVm
        )
    }
}