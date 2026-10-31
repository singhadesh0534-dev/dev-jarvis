package com.adesh.devjarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adesh.devjarvis.ui.hud.HudScreen
import com.adesh.devjarvis.ui.settings.SettingsScreen
import com.adesh.devjarvis.ui.theme.JarvisTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = ROUTE_HUD) {
                    composable(ROUTE_HUD) {
                        HudScreen(viewModel = hiltViewModel(), onOpenSettings = { nav.navigate(ROUTE_SETTINGS) })
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    private companion object {
        const val ROUTE_HUD = "hud"
        const val ROUTE_SETTINGS = "settings"
    }
}
