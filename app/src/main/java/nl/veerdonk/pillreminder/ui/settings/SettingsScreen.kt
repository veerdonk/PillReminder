package nl.veerdonk.pillreminder.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.platform.DetectionLogEntry
import nl.veerdonk.pillreminder.platform.NavigationStatus
import nl.veerdonk.pillreminder.ui.common.AppCard
import nl.veerdonk.pillreminder.ui.common.BackButton
import nl.veerdonk.pillreminder.ui.common.CardLabel
import nl.veerdonk.pillreminder.ui.common.ScreenGap
import nl.veerdonk.pillreminder.ui.common.TimePickerDialog
import nl.veerdonk.pillreminder.ui.common.formatTime
import nl.veerdonk.pillreminder.ui.theme.Tabular
import java.time.LocalTime
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    detectionLog: StateFlow<DetectionLogEntry?>,
    navigationStatus: StateFlow<NavigationStatus>,
    onTestAlarm: () -> Unit,
    onOpenSetup: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsContent(
        form = viewModel.form,
        saved = viewModel.saved,
        onEdit = viewModel::edit,
        onSave = viewModel::save,
        onReset = viewModel::resetToDefaults,
        detectionLog = detectionLog,
        navigationStatus = navigationStatus,
        onTestAlarm = onTestAlarm,
        onOpenSetup = onOpenSetup,
        onBack = onBack,
    )
}

/** Changes the form; [SettingsScreen] wires it to [SettingsViewModel.edit]. */
internal typealias FormEdit = ((SettingsForm) -> SettingsForm) -> Unit

/** The stateless Settings screen; [form] is null while loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    form: SettingsForm?,
    saved: Settings?,
    onEdit: FormEdit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    detectionLog: StateFlow<DetectionLogEntry?>,
    navigationStatus: StateFlow<NavigationStatus>,
    onTestAlarm: () -> Unit,
    onOpenSetup: () -> Unit,
    onBack: () -> Unit,
) {
    val result = remember(form) { form?.parse() }
    val unchanged = result?.settings != null && result.settings == saved
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = result?.isValid == true && !unchanged,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.settings_save)) }
                },
            )
        },
    ) { innerPadding ->
        if (form == null || result == null) return@Scaffold
        val errors = result.errors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ScreenGap),
        ) {
            Group(R.string.settings_group_wake) {
                TimeField(R.string.setting_weekday_earliest, form.weekdayEarliest, errors[SettingsField.WEEKDAY_EARLIEST]) { t ->
                    onEdit { it.copy(weekdayEarliest = t) }
                }
                TimeField(R.string.setting_weekday_fallback, form.weekdayFallback, errors[SettingsField.WEEKDAY_FALLBACK]) { t ->
                    onEdit { it.copy(weekdayFallback = t) }
                }
                TimeField(R.string.setting_weekend_earliest, form.weekendEarliest, errors[SettingsField.WEEKEND_EARLIEST]) { t ->
                    onEdit { it.copy(weekendEarliest = t) }
                }
                TimeField(R.string.setting_weekend_fallback, form.weekendFallback, errors[SettingsField.WEEKEND_FALLBACK]) { t ->
                    onEdit { it.copy(weekendFallback = t) }
                }
                NumberField(R.string.setting_min_unlocks, form.minUnlocks, errors[SettingsField.MIN_UNLOCKS]) { v ->
                    onEdit { it.copy(minUnlocks = v) }
                }
                NumberField(R.string.setting_min_app_usage, form.minAppUsageMinutes, errors[SettingsField.MIN_APP_USAGE]) { v ->
                    onEdit { it.copy(minAppUsageMinutes = v) }
                }
                NumberField(R.string.setting_detection_window, form.detectionWindowMinutes, errors[SettingsField.DETECTION_WINDOW]) { v ->
                    onEdit { it.copy(detectionWindowMinutes = v) }
                }
            }

            Group(R.string.settings_group_doses) {
                NumberField(R.string.setting_doses_per_day, form.dosesPerDay, errors[SettingsField.DOSES_PER_DAY]) { v ->
                    onEdit { it.copy(dosesPerDay = v) }
                }
                IntervalField(form, errors[SettingsField.DOSE_INTERVAL], onEdit)
                SwitchField(R.string.setting_status_notification, R.string.setting_status_notification_hint, form.showStatusNotification) { on ->
                    onEdit { it.copy(showStatusNotification = on) }
                }
            }

            Group(R.string.settings_group_alarm) {
                SwitchField(R.string.setting_hold_first_alarm, R.string.setting_hold_first_alarm_hint, form.holdFirstAlarm) { on ->
                    onEdit { it.copy(holdFirstAlarm = on) }
                }
                NumberField(R.string.setting_escalation_delay, form.escalationMinutes, errors[SettingsField.ESCALATION_DELAY]) { v ->
                    onEdit { it.copy(escalationMinutes = v) }
                }
                NumberField(R.string.setting_snooze_duration, form.snoozeMinutes, errors[SettingsField.SNOOZE_DURATION]) { v ->
                    onEdit { it.copy(snoozeMinutes = v) }
                }
                NumberField(R.string.setting_max_snoozes, form.maxSnoozes, errors[SettingsField.MAX_SNOOZES]) { v ->
                    onEdit { it.copy(maxSnoozes = v) }
                }
                NumberField(R.string.setting_ring_duration, form.ringMinutes, errors[SettingsField.RING_DURATION]) { v ->
                    onEdit { it.copy(ringMinutes = v) }
                }
                AlarmSoundField(form.alarmSoundUri) { uri -> onEdit { it.copy(alarmSoundUri = uri) } }
                FilledTonalButton(onClick = onTestAlarm, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_test_alarm))
                }
            }

            Group(R.string.settings_group_advanced) {
                NumberField(R.string.setting_poll_interval, form.pollIntervalMinutes, errors[SettingsField.POLL_INTERVAL]) { v ->
                    onEdit { it.copy(pollIntervalMinutes = v) }
                }
                TimeField(R.string.setting_rollover, form.rollover, errors[SettingsField.ROLLOVER]) { t ->
                    onEdit { it.copy(rollover = t) }
                }
                // Setup only opens by itself on first launch; this reaches it later, e.g. for the
                // optional rows (FR-9.1).
                OutlinedButton(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_open_setup))
                }
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_reset))
                }
                NavigationStatusInfo(navigationStatus)
                if (isDebuggable(LocalContext.current)) DetectionLog(detectionLog)
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text(stringResource(R.string.settings_reset_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun Group(@StringRes title: Int, content: @Composable () -> Unit) {
    AppCard {
        CardLabel(stringResource(title), color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** A tappable setting row: the label on the left, its current value emphasised on the right. */
@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium.merge(Tabular), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun errorText(error: FieldError?): String? = when (error) {
    null -> null
    FieldError.NotANumber -> stringResource(R.string.settings_error_number)
    is FieldError.Invalid -> error.message
}

@Composable
private fun NumberField(@StringRes label: Int, value: String, error: FieldError?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = errorText(error)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchField(@StringRes label: Int, @StringRes hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The whole row toggles; the switch itself only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun IntervalField(form: SettingsForm, error: FieldError?, onEdit: FormEdit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = form.doseIntervalHours,
                onValueChange = { v -> onEdit { it.copy(doseIntervalHours = v) } },
                label = { Text(stringResource(R.string.setting_dose_interval_hours)) },
                isError = error != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.doseIntervalMinutes,
                onValueChange = { v -> onEdit { it.copy(doseIntervalMinutes = v) } },
                label = { Text(stringResource(R.string.setting_dose_interval_minutes)) },
                isError = error != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        errorText(error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TimeField(@StringRes label: Int, value: LocalTime, error: FieldError?, onChange: (LocalTime) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Column {
        ValueRow(stringResource(label), formatTime(value)) { show = true }
        errorText(error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    if (show) {
        TimePickerDialog(
            title = stringResource(label),
            initial = value,
            onConfirm = {
                show = false
                onChange(it)
            },
            onDismiss = { show = false },
        )
    }
}

@Composable
private fun AlarmSoundField(uri: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            // "System default" comes back as the default alarm URI; store it as null (= default).
            onChange(picked?.takeIf { it != AndroidSettings.System.DEFAULT_ALARM_ALERT_URI }?.toString())
        }
    }
    val title = remember(uri) { ringtoneTitle(context, uri) }
    ValueRow(
        label = stringResource(R.string.settings_alarm_sound),
        value = title ?: stringResource(R.string.settings_alarm_sound_default),
        onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, AndroidSettings.System.DEFAULT_ALARM_ALERT_URI)
                .putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    uri?.toUri() ?: AndroidSettings.System.DEFAULT_ALARM_ALERT_URI,
                )
            launcher.launch(intent)
        },
    )
}

private fun ringtoneTitle(context: Context, uri: String?): String? =
    uri?.let { runCatching { RingtoneManager.getRingtone(context, it.toUri())?.getTitle(context) }.getOrNull() }

/** Debug builds only (A53): a release build never shows the detection log. */
private fun isDebuggable(context: Context): Boolean =
    (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

@Composable
private fun DetectionLog(log: StateFlow<DetectionLogEntry?>) {
    val entry by log.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_detection_log), style = MaterialTheme.typography.titleSmall)
        Text(
            text = entry?.describe() ?: stringResource(R.string.settings_detection_log_empty),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Live state of the Maps navigation check (FR-3.8). Shown in release builds too, so the rule
 * can be checked on the phone while Maps navigates (A58).
 */
@Composable
private fun NavigationStatusInfo(status: StateFlow<NavigationStatus>) {
    val current by status.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_navigation_status), style = MaterialTheme.typography.titleSmall)
        Text(
            text = current.describe(),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
