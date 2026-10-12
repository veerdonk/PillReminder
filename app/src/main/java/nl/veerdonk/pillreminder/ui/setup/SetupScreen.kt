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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.PermissionChecker
import nl.veerdonk.pillreminder.platform.SetupItem

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

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyLarge)
            }
            items(SetupItem.entries) { item ->
                SetupRow(
                    item = item,
                    ok = status.isOk(item),
                    onFix = { fix(item) },
                )
            }
            item {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_continue))
                }
            }
        }
    }
}

@Composable
private fun SetupRow(item: SetupItem, ok: Boolean, onFix: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val statusText = stringResource(if (ok) R.string.setup_status_ok else R.string.setup_status_missing)
                Text(
                    text = if (ok) "✓" else "✗",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { contentDescription = statusText },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(item.descriptionRes()), style = MaterialTheme.typography.bodyMedium)
            // The OnePlus row always offers its steps; the others only while something is missing.
            if (!ok || item == SetupItem.ONEPLUS_EXTRAS) {
                Button(onClick = onFix) {
                    Text(
                        stringResource(
                            if (item == SetupItem.ONEPLUS_EXTRAS) R.string.setup_button_steps else R.string.setup_button_grant,
                        ),
                    )
                }
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
