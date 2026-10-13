package nl.veerdonk.pillreminder.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.model.PillStock
import nl.veerdonk.pillreminder.platform.SetupStatus
import nl.veerdonk.pillreminder.ui.common.AppCard
import nl.veerdonk.pillreminder.ui.common.CardLabel
import nl.veerdonk.pillreminder.ui.common.IconBadge
import nl.veerdonk.pillreminder.ui.common.ScreenGap
import nl.veerdonk.pillreminder.ui.common.Tag
import nl.veerdonk.pillreminder.ui.common.TimePickerDialog
import nl.veerdonk.pillreminder.ui.common.formatTime
import nl.veerdonk.pillreminder.ui.setup.PermissionBanner
import nl.veerdonk.pillreminder.ui.theme.Tabular
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    setupStatus: SetupStatus,
    onOpenSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val model by viewModel.model.collectAsState()
    val pillsLeft by viewModel.pillsLeft.collectAsState()
    HomeContent(
        model = model,
        pillsLeft = pillsLeft,
        setupStatus = setupStatus,
        onOpenSetup = onOpenSetup,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        callbacks = HomeCallbacks(
            tookNow = viewModel::tookDoseNow,
            tookAt = viewModel::tookDoseAt,
            remindNow = viewModel::remindNow,
            skip = viewModel::skip,
            resumeDay = viewModel::resumeDay,
            setPillsLeft = viewModel::setPillsLeft,
        ),
    )
}

/** What the Home buttons do; [HomeScreen] wires them to [HomeViewModel]. */
internal class HomeCallbacks(
    val tookNow: (Int) -> Unit,
    val tookAt: (LocalTime) -> BackdateResult,
    val remindNow: () -> Unit,
    val skip: () -> Unit,
    val resumeDay: () -> Unit,
    val setPillsLeft: (Int?) -> Unit,
)

/** The stateless Home screen, so it can be previewed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    model: HomeModel?,
    pillsLeft: Int?,
    setupStatus: SetupStatus,
    onOpenSetup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    callbacks: HomeCallbacks,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = stringResource(R.string.home_history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ScreenGap),
        ) {
            PermissionBanner(status = setupStatus, onOpenSetup = onOpenSetup)
            model?.let { m ->
                val zone = ZoneId.systemDefault()
                NextActionCard(m, zone)
                ActionsCard(m, callbacks)
                TodayCard(m, zone)
                PillSupplyCard(pillsLeft, m.settings.dosesPerDay, callbacks.setPillsLeft)
            }
        }
    }
}

/** How the next-action card looks: calm while waiting, coral while a dose needs you, solid when all is done. */
private enum class HeroTone { WAITING, ATTENTION, DONE, ALL_TAKEN }

private fun heroTone(model: HomeModel): HeroTone = when (model.nextAction) {
    is NextAction.DetectionStartsAt, is NextAction.ReminderByFallback, is NextAction.ReminderAt -> HeroTone.WAITING
    is NextAction.DoneUntil ->
        if (model.rows.all { it.status is DoseRowStatus.Taken }) HeroTone.ALL_TAKEN else HeroTone.DONE
    else -> HeroTone.ATTENTION
}

/** The hero card: what happens next, the first thing to read on the screen (FR-6.2). */
@Composable
private fun NextActionCard(model: HomeModel, zone: ZoneId) {
    val colors = MaterialTheme.colorScheme
    val tone = heroTone(model)
    val (container, content) = when (tone) {
        HeroTone.WAITING -> colors.primaryContainer to colors.onPrimaryContainer
        HeroTone.ATTENTION -> colors.tertiaryContainer to colors.onTertiaryContainer
        HeroTone.DONE -> colors.surfaceContainerHigh to colors.onSurface
        HeroTone.ALL_TAKEN -> colors.primary to colors.onPrimary
    }
    val (badge, badgeContent) = when (tone) {
        HeroTone.WAITING -> colors.primary to colors.onPrimary
        HeroTone.ATTENTION -> colors.tertiary to colors.onTertiary
        HeroTone.DONE -> colors.secondaryContainer to colors.onSecondaryContainer
        HeroTone.ALL_TAKEN -> colors.onPrimary to colors.primary
    }
    val icon = when (tone) {
        HeroTone.WAITING -> R.drawable.ic_stat_pill
        HeroTone.ATTENTION -> R.drawable.ic_alarm
        HeroTone.DONE, HeroTone.ALL_TAKEN -> R.drawable.ic_check
    }
    AppCard(containerColor = container) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // The capsule only fills the middle of its canvas, so it is drawn larger.
            val iconSize = if (icon == R.drawable.ic_stat_pill) 36.dp else 24.dp
            IconBadge(icon, containerColor = badge, contentColor = badgeContent, size = 48.dp, iconSize = iconSize)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (tone == HeroTone.ALL_TAKEN) {
                    Text(stringResource(R.string.home_all_taken), style = MaterialTheme.typography.headlineSmall, color = content)
                    Text(nextActionText(model.nextAction, zone), style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.8f))
                } else {
                    Text(nextActionText(model.nextAction, zone), style = MaterialTheme.typography.headlineSmall.merge(Tabular), color = content)
                    model.detection?.let {
                        Text(
                            stringResource(R.string.home_detecting, formatTime(it.earliestAt, zone), formatTime(it.fallbackAt, zone)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun nextActionText(action: NextAction, zone: ZoneId): String = when (action) {
    is NextAction.DetectionStartsAt -> stringResource(R.string.next_detection_starts, formatTime(action.at, zone))
    is NextAction.ReminderByFallback -> stringResource(R.string.next_reminder_by_fallback, formatTime(action.fallbackAt, zone))
    is NextAction.ReminderAt -> stringResource(R.string.next_reminder_at, formatTime(action.at, zone))
    is NextAction.AlarmIn -> pluralStringResource(R.plurals.next_alarm_in, action.minutes.toInt(), action.minutes.toInt())
    is NextAction.AlarmAt -> stringResource(R.string.next_alarm_at, formatTime(action.at, zone))
    NextAction.Ringing -> stringResource(R.string.next_ringing)
    is NextAction.SnoozedUntil -> stringResource(R.string.next_snoozed_until, formatTime(action.until, zone))
    NextAction.HeldWhileDriving -> stringResource(R.string.next_held_driving)
    NextAction.NoMoreAlarms -> stringResource(R.string.next_no_more_alarms)
    is NextAction.DoneUntil -> stringResource(R.string.next_done_until, formatTime(action.nextDayAt, zone))
}

/** Today's doses as a vertical timeline (FR-6.1). */
@Composable
private fun TodayCard(model: HomeModel, zone: ZoneId) {
    AppCard {
        CardLabel(stringResource(R.string.home_today))
        Column {
            model.rows.forEachIndexed { i, row ->
                TimelineRow(
                    label = stringResource(R.string.home_dose_label, row.index, model.doseCount),
                    status = row.status,
                    statusText = doseStatusText(row.status, zone),
                    last = i == model.rows.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(label: String, status: DoseRowStatus, statusText: String, last: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StatusDot(status)
            if (!last) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (status is DoseRowStatus.Taken) colors.primary.copy(alpha = 0.4f) else colors.outlineVariant),
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp, bottom = if (last) 0.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Text(
                statusText,
                style = MaterialTheme.typography.titleMedium.merge(Tabular),
                color = when (status) {
                    is DoseRowStatus.Taken -> colors.onSurface
                    DoseRowStatus.Pending -> colors.tertiary
                    DoseRowStatus.Missed -> colors.error
                    is DoseRowStatus.Upcoming, DoseRowStatus.Skipped -> colors.onSurfaceVariant
                },
            )
        }
    }
}

/** The timeline marker of one dose: filled when taken, ringed while pending, hollow when upcoming. */
@Composable
private fun StatusDot(status: DoseRowStatus) {
    val colors = MaterialTheme.colorScheme
    val size = 24.dp
    when (status) {
        is DoseRowStatus.Taken -> IconBadge(R.drawable.ic_check, colors.primary, colors.onPrimary, size = size, iconSize = 16.dp)
        DoseRowStatus.Missed -> IconBadge(R.drawable.ic_close, colors.errorContainer, colors.error, size = size, iconSize = 16.dp)
        DoseRowStatus.Skipped -> IconBadge(R.drawable.ic_remove, colors.surfaceVariant, colors.onSurfaceVariant, size = size, iconSize = 16.dp)
        DoseRowStatus.Pending -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(10.dp).clip(CircleShape).background(colors.tertiary)) }
        is DoseRowStatus.Upcoming -> Box(
            modifier = Modifier
                .size(size)
                .border(2.dp, colors.outlineVariant, CircleShape),
        )
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
private fun ActionsCard(model: HomeModel, callbacks: HomeCallbacks) {
    val actions = model.actions
    var showPicker by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<BackdateResult.Invalid?>(null) }
    val zone = ZoneId.systemDefault()

    // Nothing to record or change (e.g. a finished day): no empty card.
    val any = actions.recordDose != null || actions.remindNow || actions.skip != null || actions.resumeDay
    if (any) {
        AppCard {
            CardLabel(stringResource(R.string.record_title))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.recordDose?.let { dose ->
                    Button(onClick = { callbacks.tookNow(dose) }, modifier = PrimaryButton) {
                        Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.record_took_now, dose), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalButton(
                        onClick = {
                            pickError = null
                            showPicker = true
                        },
                        modifier = SecondaryButton,
                    ) { Text(stringResource(R.string.record_took_at, dose)) }
                }
                if (actions.remindNow) {
                    FilledTonalButton(onClick = callbacks.remindNow, modifier = SecondaryButton) {
                        Text(stringResource(R.string.action_remind_now))
                    }
                }
                actions.skip?.let { kind ->
                    OutlinedButton(onClick = callbacks.skip, modifier = SecondaryButton) {
                        Text(
                            stringResource(
                                if (kind == SkipKind.NOT_TODAY) R.string.action_not_today else R.string.action_done_for_today,
                            ),
                        )
                    }
                }
                if (actions.resumeDay) {
                    Button(onClick = callbacks.resumeDay, modifier = PrimaryButton) {
                        Text(stringResource(R.string.action_resume_today), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showPicker && actions.recordDose != null) {
        TimePickerDialog(
            title = stringResource(R.string.record_pick_title, actions.recordDose),
            initial = LocalTime.now().withSecond(0).withNano(0),
            onConfirm = { time ->
                when (val result = callbacks.tookAt(time)) {
                    is BackdateResult.Valid -> showPicker = false
                    is BackdateResult.Invalid -> pickError = result
                }
            },
            onDismiss = { showPicker = false },
            errorText = pickError?.let { backdateErrorText(it, zone) },
        )
    }
}

private val PrimaryButton = Modifier
    .fillMaxWidth()
    .height(56.dp)

private val SecondaryButton = Modifier
    .fillMaxWidth()
    .height(48.dp)

@Composable
private fun backdateErrorText(error: BackdateResult.Invalid, zone: ZoneId): String {
    val limit: Instant = error.limit
    return when (error.error) {
        BackdateError.IN_FUTURE -> stringResource(R.string.record_error_future)
        BackdateError.BEFORE_PREVIOUS_DOSE -> stringResource(R.string.record_error_before_previous, formatTime(limit, zone))
        BackdateError.BEFORE_START_OF_DAY -> stringResource(R.string.record_error_before_day_start, formatTime(limit, zone))
    }
}

/** Pills on hand, counted down per dose taken, with the low-supply warning (FR-10). */
@Composable
private fun PillSupplyCard(pillsLeft: Int?, dosesPerDay: Int, onSet: (Int?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    AppCard {
        CardLabel(stringResource(R.string.pills_title))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.ic_stat_pill, colors.primaryContainer, colors.onPrimaryContainer, size = 48.dp, iconSize = 36.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (pillsLeft == null) {
                    Text(stringResource(R.string.pills_not_counting), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                } else {
                    val days = PillStock.daysLeft(pillsLeft, dosesPerDay)
                    Text(
                        pluralStringResource(R.plurals.pills_left, pillsLeft, pillsLeft),
                        style = MaterialTheme.typography.headlineSmall.merge(Tabular),
                    )
                    Text(
                        stringResource(R.string.pills_enough_for, pluralStringResource(R.plurals.pills_days, days, days)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (pillsLeft != null && PillStock.lowStock(pillsLeft, dosesPerDay) != null) {
            Tag(
                text = stringResource(R.string.pills_low),
                containerColor = colors.errorContainer,
                contentColor = colors.onErrorContainer,
                icon = R.drawable.ic_priority,
            )
        }
        TextButton(onClick = { editing = true }) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pills_set))
        }
    }
    if (editing) {
        PillCountDialog(
            initial = pillsLeft,
            onSave = { count ->
                editing = false
                onSet(count)
            },
            onDismiss = { editing = false },
        )
    }
}

/** Saves a valid count; "Stop counting" (only while counting) saves null. */
@Composable
private fun PillCountDialog(initial: Int?, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    val count = parsePillCount(text)
    val showError = text.isNotBlank() && count == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pills_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.pills_dialog_label)) },
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.pills_dialog_error, PillStock.MAX_PILLS)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(count) }, enabled = count != null) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.pills_stop)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
            }
        },
    )
}
