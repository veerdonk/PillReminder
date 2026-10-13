package nl.veerdonk.pillreminder.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.PermissionChecker
import nl.veerdonk.pillreminder.platform.SetupStatus
import nl.veerdonk.pillreminder.ui.common.AppCard
import nl.veerdonk.pillreminder.ui.common.IconBadge

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
    val colors = MaterialTheme.colorScheme
    AppCard(
        modifier = modifier,
        containerColor = if (hard) colors.errorContainer else colors.tertiaryContainer,
    ) {
        val content = if (hard) colors.onErrorContainer else colors.onTertiaryContainer
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                R.drawable.ic_priority,
                containerColor = if (hard) colors.error else colors.tertiary,
                contentColor = if (hard) colors.onError else colors.onTertiary,
            )
            Text(
                text = if (hard) {
                    stringResource(R.string.banner_message_required)
                } else {
                    pluralStringResource(R.plurals.banner_message, status.missing.size, status.missing.size)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = onOpenSetup,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (hard) colors.error else colors.tertiary,
                contentColor = if (hard) colors.onError else colors.onTertiary,
            ),
        ) { Text(stringResource(R.string.banner_open_setup)) }
    }
}
