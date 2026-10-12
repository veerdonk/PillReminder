package nl.veerdonk.pillreminder.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.AppContainer
import nl.veerdonk.pillreminder.PillReminderApp
import nl.veerdonk.pillreminder.ui.history.HistoryScreen
import nl.veerdonk.pillreminder.ui.history.HistoryViewModel
import nl.veerdonk.pillreminder.ui.home.HomeScreen
import nl.veerdonk.pillreminder.ui.home.HomeViewModel
import nl.veerdonk.pillreminder.ui.settings.SettingsScreen
import nl.veerdonk.pillreminder.ui.settings.SettingsViewModel
import nl.veerdonk.pillreminder.ui.setup.OnePlusGuideScreen
import nl.veerdonk.pillreminder.ui.setup.SetupScreen
import nl.veerdonk.pillreminder.ui.setup.rememberSetupStatus
import nl.veerdonk.pillreminder.ui.theme.PillReminderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PillReminderApp).container
        val checker = container.permissionChecker
        // Setup opens by itself on first launch and while a hard requirement is missing (FR-9.1).
        val start = if (checker.check().isSetupNeeded(checker.setupSeen)) Routes.SETUP else Routes.HOME
        setContent {
            PillReminderTheme {
                AppNavHost(container = container, startDestination = start)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // On the app scope, so leaving the screen doesn't cancel a tick halfway.
        val container = (application as PillReminderApp).container
        container.appScope.launch { container.reminderController.tick() }
    }
}

/** Navigation routes. */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SETUP = "setup"
    const val ONEPLUS_GUIDE = "oneplus_guide"
}

@Composable
private fun AppNavHost(container: AppContainer, startDestination: String) {
    val checker = container.permissionChecker
    val navController = rememberNavController()
    val factory = remember(container) { AppViewModelFactory(container) }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) {
            val status by rememberSetupStatus(checker)
            HomeScreen(
                viewModel = viewModel<HomeViewModel>(factory = factory),
                setupStatus = status,
                onOpenSetup = { navController.navigate(Routes.SETUP) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = viewModel<HistoryViewModel>(factory = factory),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel<SettingsViewModel>(factory = factory),
                detectionLog = container.detectionLog,
                onTestAlarm = { container.testAlarm.startTestAlarm() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                checker = checker,
                onOpenOnePlusGuide = { navController.navigate(Routes.ONEPLUS_GUIDE) },
                onContinue = {
                    checker.setupSeen = true
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ONEPLUS_GUIDE) {
            OnePlusGuideScreen(checker = checker, onBack = { navController.popBackStack() })
        }
    }
}
