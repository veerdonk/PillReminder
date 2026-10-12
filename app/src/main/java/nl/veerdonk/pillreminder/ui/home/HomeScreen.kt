package nl.veerdonk.pillreminder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.SetupStatus
import nl.veerdonk.pillreminder.ui.common.TimePickerDialog
import nl.veerdonk.pillreminder.ui.common.formatTime
import nl.veerdonk.pillreminder.ui.setup.PermissionBanner
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    setupStatus: SetupStatus,
    onOpenSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val model by viewModel.model.collectAsState()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.home_history)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.home_settings)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionBanner(status = setupStatus, onOpenSetup = onOpenSetup)
            model?.let { m ->
                val zone = ZoneId.systemDefault()
                StatusCard(m, zone)
                NextActionLine(m.nextAction, zone)
                ActionsCard(m, viewModel)
            }
        }
    }
}

@Composable
private fun StatusCard(model: HomeModel, zone: ZoneId) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_today), style = MaterialTheme.typography.titleMedium)
            model.detection?.let {
                Text(
                    stringResource(R.string.home_detecting, formatTime(it.earliestAt, zone), formatTime(it.fallbackAt, zone)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            model.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.home_dose_label, row.index, model.doseCount))
                    Text(doseStatusText(row.status, zone), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun doseStatusText(status: DoseRowStatus, zone: ZoneId): String = when (status) {
    is DoseRowStatus.Taken -> stringResource(R.string.dose_status_taken, formatTime(status.at, zone))
    DoseRowStatus.Pending -> stringResource(R.string.dose_status_pending)
    is DoseRowStatus.Upcoming ->
        status.approxAt?.let { stringResource(R.string.dose_status_upcoming_at, formatTime(it, zone)) }
            ?: stringResource(R.string.dose_status_upcoming)
    DoseRowStatus.Skipped -> stringResource(R.string.dose_status_skipped)
    DoseRowStatus.Missed -> stringResource(R.string.dose_status_missed)
}

@Composable
private fun NextActionLine(action: NextAction, zone: ZoneId) {
    val text = when (action) {
        is NextAction.DetectionStartsAt -> stringResource(R.string.next_detection_starts, formatTime(action.at, zone))
        is NextAction.ReminderByFallback -> stringResource(R.string.next_reminder_by_fallback, formatTime(action.fallbackAt, zone))
        is NextAction.ReminderAt -> stringResource(R.string.next_reminder_at, formatTime(action.at, zone))
        is NextAction.AlarmIn -> pluralStringResource(R.plurals.next_alarm_in, action.minutes.toInt(), action.minutes.toInt())
        is NextAction.AlarmAt -> stringResource(R.string.next_alarm_at, formatTime(action.at, zone))
        NextAction.Ringing -> stringResource(R.string.next_ringing)
        is NextAction.SnoozedUntil -> stringResource(R.string.next_snoozed_until, formatTime(action.until, zone))
        NextAction.NoMoreAlarms -> stringResource(R.string.next_no_more_alarms)
        is NextAction.DoneUntil -> stringResource(R.string.next_done_until, formatTime(action.nextDayAt, zone))
    }
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ActionsCard(model: HomeModel, viewModel: HomeViewModel) {
    val actions = model.actions
    var showPicker by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<BackdateResult.Invalid?>(null) }
    val zone = ZoneId.systemDefault()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.record_title), style = MaterialTheme.typography.titleMedium)
            actions.recordDose?.let { dose ->
                Button(onClick = { viewModel.tookDoseNow(dose) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.record_took_now, dose))
                }
                OutlinedButton(
                    onClick = {
                        pickError = null
                        showPicker = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.record_took_at, dose)) }
            }
            if (actions.remindNow) {
                OutlinedButton(onClick = viewModel::remindNow, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_remind_now))
                }
            }
            actions.skip?.let { kind ->
                OutlinedButton(onClick = viewModel::skip, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (kind == SkipKind.NOT_TODAY) R.string.action_not_today else R.string.action_done_for_today,
                        ),
                    )
                }
            }
            if (actions.resumeDay) {
                Button(onClick = viewModel::resumeDay, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_resume_today))
                }
            }
        }
    }

    if (showPicker && actions.recordDose != null) {
        TimePickerDialog(
            title = stringResource(R.string.record_pick_title, actions.recordDose),
            initial = LocalTime.now().withSecond(0).withNano(0),
            onConfirm = { time ->
                when (val result = viewModel.tookDoseAt(time)) {
                    is BackdateResult.Valid -> showPicker = false
                    is BackdateResult.Invalid -> pickError = result
                }
            },
            onDismiss = { showPicker = false },
            errorText = pickError?.let { backdateErrorText(it, zone) },
        )
    }
}

@Composable
private fun backdateErrorText(error: BackdateResult.Invalid, zone: ZoneId): String {
    val limit: Instant = error.limit
    return when (error.error) {
        BackdateError.IN_FUTURE -> stringResource(R.string.record_error_future)
        BackdateError.BEFORE_PREVIOUS_DOSE -> stringResource(R.string.record_error_before_previous, formatTime(limit, zone))
        BackdateError.BEFORE_START_OF_DAY -> stringResource(R.string.record_error_before_day_start, formatTime(limit, zone))
    }
}
