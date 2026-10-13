package nl.veerdonk.pillreminder.ui.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.PermissionChecker
import nl.veerdonk.pillreminder.platform.SetupItem
import nl.veerdonk.pillreminder.platform.SetupStatus
import nl.veerdonk.pillreminder.ui.common.AppCard
import nl.veerdonk.pillreminder.ui.common.IconBadge
import nl.veerdonk.pillreminder.ui.theme.Tabular

@StringRes
private fun SetupItem.titleRes(): Int = when (this) {
    SetupItem.NOTIFICATIONS -> R.string.setup_item_notifications_title
    SetupItem.EXACT_ALARMS -> R.string.setup_item_exact_alarms_title
    SetupItem.USAGE_ACCESS -> R.string.setup_item_usage_access_title
    SetupItem.FULL_SCREEN_INTENT -> R.string.setup_item_full_screen_title
    SetupItem.BATTERY_OPTIMISATION -> R.string.setup_item_battery_title
    SetupItem.MAPS_NAVIGATION -> R.string.setup_item_maps_title
    SetupItem.ONEPLUS_EXTRAS -> R.string.setup_item_oneplus_title
}

@StringRes
private fun SetupItem.descriptionRes(): Int = when (this) {
    SetupItem.NOTIFICATIONS -> R.string.setup_item_notifications_desc
    SetupItem.EXACT_ALARMS -> R.string.setup_item_exact_alarms_desc
    SetupItem.USAGE_ACCESS -> R.string.setup_item_usage_access_desc
    SetupItem.FULL_SCREEN_INTENT -> R.string.setup_item_full_screen_desc
    SetupItem.BATTERY_OPTIMISATION -> R.string.setup_item_battery_desc
    SetupItem.MAPS_NAVIGATION -> R.string.setup_item_maps_desc
    SetupItem.ONEPLUS_EXTRAS -> R.string.setup_item_oneplus_desc
}

/**
 * The setup checklist (design §9-10): a live status per row, re-checked on resume, and a
 * button that opens the matching system screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    checker: PermissionChecker,
    onOpenOnePlusGuide: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    var status by rememberSetupStatus(checker)

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = checker.check()
        // Denied (possibly for good): the system won't ask again, so send the user to the settings page.
        if (!granted) startSettings(context, checker, SetupItem.NOTIFICATIONS)
    }

    fun fix(item: SetupItem) {
        when (item) {
            SetupItem.NOTIFICATIONS ->
                if (hasPostNotificationsPermission(context)) {
                    startSettings(context, checker, item) // Granted, so a channel must be off.
                } else {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            SetupItem.ONEPLUS_EXTRAS -> onOpenOnePlusGuide()
            else -> startSettings(context, checker, item)
        }
    }

    SetupContent(status = status, onFix = ::fix, onContinue = onContinue)
}

/** The stateless setup checklist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupContent(status: SetupStatus, onFix: (SetupItem) -> Unit, onContinue: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SetupProgress(status) }
            items(SetupItem.entries) { item ->
                SetupRow(
                    item = item,
                    ok = status.isOk(item),
                    onFix = { onFix(item) },
                )
            }
            item {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(56.dp),
                ) { Text(stringResource(R.string.setup_continue), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** The intro with a progress bar: how much of the checklist is done. */
@Composable
private fun SetupProgress(status: SetupStatus) {
    val total = SetupItem.entries.size
    val done = SetupItem.entries.count { status.isOk(it) }
    Column(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Text(
                stringResource(R.string.setup_progress, done, total),
                style = MaterialTheme.typography.labelLarge.merge(Tabular),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupRow(item: SetupItem, ok: Boolean, onFix: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val statusText = stringResource(if (ok) R.string.setup_status_ok else R.string.setup_status_missing)
            val (badge, badgeContent) = when {
                ok -> colors.primary to colors.onPrimary
                item.hard -> colors.errorContainer to colors.error
                item.optional -> colors.surfaceVariant to colors.onSurfaceVariant
                else -> colors.tertiaryContainer to colors.tertiary
            }
            IconBadge(
                icon = if (ok) R.drawable.ic_check else R.drawable.ic_priority,
                containerColor = badge,
                contentColor = badgeContent,
                contentDescription = statusText,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(item.titleRes()), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        when {
                            item.hard -> R.string.setup_required
                            item.optional -> R.string.setup_optional
                            else -> R.string.setup_recommended
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.hard && !ok) colors.error else colors.onSurfaceVariant,
                )
            }
        }
        Text(stringResource(item.descriptionRes()), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        // The OnePlus row always offers its steps; the others only while something is missing.
        if (!ok || item == SetupItem.ONEPLUS_EXTRAS) {
            FilledTonalButton(onClick = onFix) {
                Text(
                    stringResource(
                        if (item == SetupItem.ONEPLUS_EXTRAS) R.string.setup_button_steps else R.string.setup_button_grant,
                    ),
                )
            }
        }
    }
}

private fun hasPostNotificationsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Opens the item's settings page, or the app details page if this build of Android lacks it. */
internal fun startSettings(context: Context, checker: PermissionChecker, item: SetupItem) {
    try {
        context.startActivity(checker.fixIntent(item))
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(checker.appDetailsIntent())
        } catch (_: ActivityNotFoundException) {
            // Nothing can handle it; the row simply stays unchecked.
        }
    }
}
