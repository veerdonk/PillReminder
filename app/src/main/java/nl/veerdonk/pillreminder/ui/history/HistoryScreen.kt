package nl.veerdonk.pillreminder.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.model.WakeSource
import nl.veerdonk.pillreminder.ui.common.AppCard
import nl.veerdonk.pillreminder.ui.common.BackButton
import nl.veerdonk.pillreminder.ui.common.IconBadge
import nl.veerdonk.pillreminder.ui.common.Tag
import nl.veerdonk.pillreminder.ui.common.formatDate
import nl.veerdonk.pillreminder.ui.common.formatTime
import java.time.ZoneId

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val rows by viewModel.rows.collectAsState()
    HistoryContent(rows, onBack)
}

/** The stateless History screen; [rows] is null while loading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryContent(rows: List<HistoryRow>?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        val list = rows
        if (list != null && list.isEmpty()) {
            EmptyHistory(Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list.orEmpty(), key = { it.dayId.toString() }) { row -> HistoryRowCard(row) }
            }
        }
    }
}

@Composable
private fun EmptyHistory(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(R.drawable.ic_history, colors.primaryContainer, colors.onPrimaryContainer, size = 80.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRowCard(row: HistoryRow) {
    val zone = ZoneId.systemDefault()
    val colors = MaterialTheme.colorScheme
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDate(row.dayId), style = MaterialTheme.typography.titleMedium)
                if (row.isToday) {
                    Tag(stringResource(R.string.history_today), colors.primaryContainer, colors.onPrimaryContainer)
                }
            }
            Text(wakeText(row, zone), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            row.chips.forEach { chip -> DoseTag(chip, zone) }
        }
        if (row.snoozes > 0) {
            Text(
                pluralStringResource(R.plurals.history_snoozes, row.snoozes, row.snoozes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** One dose of the day, colour-coded: green when taken, coral while pending, red when missed. */
@Composable
private fun DoseTag(chip: DoseChip, zone: ZoneId) {
    val colors = MaterialTheme.colorScheme
    when (chip) {
        is DoseChip.Taken -> {
            val description = stringResource(R.string.dose_status_taken, formatTime(chip.at, zone))
            Tag(
                formatTime(chip.at, zone),
                colors.primaryContainer,
                colors.onPrimaryContainer,
                icon = R.drawable.ic_check,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            )
        }
        is DoseChip.Pending -> Tag(stringResource(R.string.dose_status_pending), colors.tertiaryContainer, colors.onTertiaryContainer, icon = R.drawable.ic_alarm)
        is DoseChip.Missed -> Tag(stringResource(R.string.dose_status_missed), colors.errorContainer, colors.onErrorContainer, icon = R.drawable.ic_close)
        is DoseChip.Skipped -> Tag(stringResource(R.string.dose_status_skipped), colors.surfaceVariant, colors.onSurfaceVariant, icon = R.drawable.ic_remove)
        is DoseChip.Upcoming -> Tag(stringResource(R.string.dose_status_upcoming), colors.surfaceContainerHigh, colors.onSurfaceVariant)
    }
}

@Composable
private fun wakeText(row: HistoryRow, zone: ZoneId): String {
    val at = row.wakeAt ?: return stringResource(R.string.history_wake_none)
    val time = formatTime(at, zone)
    return when (row.wakeSource) {
        WakeSource.DETECTED -> stringResource(R.string.history_wake_detected, time)
        WakeSource.FALLBACK -> stringResource(R.string.history_wake_fallback, time)
        WakeSource.MANUAL -> stringResource(R.string.history_wake_manual, time)
        null -> stringResource(R.string.history_wake_at, time)
    }
}
