package nl.veerdonk.pillreminder.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.PillReminderApp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.PermissionChecker
import nl.veerdonk.pillreminder.platform.SetupStatus
import nl.veerdonk.pillreminder.ui.setup.OnePlusGuideScreen
import nl.veerdonk.pillreminder.ui.setup.PermissionBanner
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
                AppNavHost(checker = checker, startDestination = start)
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

/** Navigation routes. T10 adds Home, History and Settings content behind these. */
object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val ONEPLUS_GUIDE = "oneplus_guide"
}

@Composable
private fun AppNavHost(checker: PermissionChecker, startDestination: String) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.HOME) {
            val status by rememberSetupStatus(checker)
            PlaceholderScreen(
                status = status,
                onOpenSetup = { navController.navigate(Routes.SETUP) },
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

@Composable
private fun PlaceholderScreen(status: SetupStatus, onOpenSetup: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            PermissionBanner(
                status = status,
                onOpenSetup = onOpenSetup,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    PillReminderTheme { PlaceholderScreen(status = SetupStatus(emptySet()), onOpenSetup = {}) }
}
