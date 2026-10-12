package nl.veerdonk.pillreminder.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.PermissionChecker
import nl.veerdonk.pillreminder.platform.SetupStatus

/**
 * The checklist status, re-checked every time the screen resumes (the user comes back from a
 * system settings page). Shared by the Setup screen and the Home banner.
 */
@Composable
fun rememberSetupStatus(checker: PermissionChecker): MutableState<SetupStatus> {
    val status = remember(checker) { mutableStateOf(checker.check()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status.value = checker.check() }
    return status
}

/**
 * FR-6.3: the Home warning banner. Draws nothing when everything is set. In red when a hard
 * requirement is missing, otherwise in a calmer tone. T10 puts it at the top of Home:
 * `PermissionBanner(rememberSetupStatus(checker).value, onOpenSetup = ...)`.
 */
@Composable
fun PermissionBanner(
    status: SetupStatus,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!status.showBanner) return
    val hard = status.missingHard.isNotEmpty()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hard) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = if (hard) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (hard) {
                    stringResource(R.string.banner_message_required)
                } else {
                    pluralStringResource(R.plurals.banner_message, status.missing.size, status.missing.size)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onOpenSetup) { Text(stringResource(R.string.banner_open_setup)) }
        }
    }
}
