package com.adesh.devjarvis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adesh.devjarvis.core.JarvisSession
import com.adesh.devjarvis.service.ServiceController
import com.adesh.devjarvis.settings.SettingsRepository
import com.adesh.devjarvis.ui.activity.ActivityLogScreen
import com.adesh.devjarvis.ui.boot.BootScreen
import com.adesh.devjarvis.ui.hud.HudScreen
import com.adesh.devjarvis.ui.memory.MemoryScreen
import com.adesh.devjarvis.ui.settings.SettingsScreen
import com.adesh.devjarvis.ui.setup.SetupScreen
import com.adesh.devjarvis.ui.theme.JarvisTheme
import com.adesh.devjarvis.update.AppUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var session: JarvisSession
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var updater: AppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val home = if (settings.current.setupDone) ROUTE_HUD else ROUTE_SETUP_FIRST
        // Boot animation on a normal cold start only — not when launched by the assistant gesture.
        val isAssist = intent?.action == Intent.ACTION_ASSIST || intent?.action == Intent.ACTION_VOICE_COMMAND
        val start = if (savedInstanceState == null && !isAssist && settings.current.bootAnimation) ROUTE_BOOT else home
        setContent {
            JarvisTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = start) {
                    composable(ROUTE_BOOT) {
                        BootScreen(viewModel = hiltViewModel(), onDone = {
                            nav.navigate(home) { popUpTo(ROUTE_BOOT) { inclusive = true } }
                        })
                    }
                    composable(ROUTE_HUD) {
                        HudScreen(
                            viewModel = hiltViewModel(),
                            onOpenSettings = { nav.navigate(ROUTE_SETTINGS) },
                            onOpenActivity = { nav.navigate(ROUTE_ACTIVITY) },
                            onOpenMemory = { nav.navigate(ROUTE_MEMORY) },
                        )
                    }
                    composable(ROUTE_ACTIVITY) {
                        ActivityLogScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() })
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            viewModel = hiltViewModel(),
                            onBack = { nav.popBackStack() },
                            onOpenSetup = { nav.navigate(ROUTE_SETUP) },
                            onOpenMemory = { nav.navigate(ROUTE_MEMORY) },
                        )
                    }
                    composable(ROUTE_MEMORY) {
                        MemoryScreen(viewModel = hiltViewModel(), onBack = { nav.popBackStack() })
                    }
                    composable(ROUTE_SETUP_FIRST) {
                        SetupScreen(viewModel = hiltViewModel(), onDone = {
                            nav.navigate(ROUTE_HUD) { popUpTo(ROUTE_SETUP_FIRST) { inclusive = true } }
                        })
                    }
                    composable(ROUTE_SETUP) {
                        SetupScreen(viewModel = hiltViewModel(), onDone = { nav.popBackStack() })
                    }
                }
            }
        }
        if (savedInstanceState == null) {
            handleAssist(intent)
            updater.checkIfDue() // quiet; HUD shows a banner if a newer build exists
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAssist(intent)
    }

    override fun onResume() {
        super.onResume()
        // App khula hai = foreground: yahi sahi waqt hai mic service ko (dobara) start karne ka,
        // e.g. reboot ya XOS ke maarne ke baad.
        if (settings.current.alwaysOn && !session.ui.value.alwaysOn) ServiceController.start(this)
    }

    /** Home long-press / assistant button: seedha sunna shuru. */
    private fun handleAssist(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_ASSIST || action == Intent.ACTION_VOICE_COMMAND) {
            if (ServiceController.hasMicPermission(this)) session.onAssistLaunch()
        }
    }

    private companion object {
        const val ROUTE_BOOT = "boot"
        const val ROUTE_HUD = "hud"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_ACTIVITY = "activity"
        const val ROUTE_MEMORY = "memory"
        const val ROUTE_SETUP_FIRST = "setup_first"
        const val ROUTE_SETUP = "setup"
    }
}
