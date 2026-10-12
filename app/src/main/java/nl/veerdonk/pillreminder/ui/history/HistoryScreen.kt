package nl.veerdonk.pillreminder.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.model.WakeSource
import nl.veerdonk.pillreminder.ui.common.formatDate
import nl.veerdonk.pillreminder.ui.common.formatTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val rows by viewModel.rows.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.setup_navigate_back)) } },
            )
        },
    ) { innerPadding ->
        val list = rows
        if (list != null && list.isEmpty()) {
            Text(
                stringResource(R.string.history_empty),
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list.orEmpty(), key = { it.dayId.toString() }) { row -> HistoryRowCard(row) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRowCard(row: HistoryRow) {
    val zone = ZoneId.systemDefault()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(row.dayId), style = MaterialTheme.typography.titleMedium)
                if (row.isToday) Text(stringResource(R.string.history_today), style = MaterialTheme.typography.labelLarge)
            }
            Text(wakeText(row, zone), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.chips.forEach { chip -> AssistChip(onClick = {}, label = { Text(chipText(chip, zone)) }) }
            }
            if (row.snoozes > 0) {
                Text(
                    pluralStringResource(R.plurals.history_snoozes, row.snoozes, row.snoozes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
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

@Composable
private fun chipText(chip: DoseChip, zone: ZoneId): String = when (chip) {
    is DoseChip.Taken -> stringResource(R.string.history_chip_taken, formatTime(chip.at, zone))
    is DoseChip.Skipped -> stringResource(R.string.history_chip_skipped)
    is DoseChip.Missed -> stringResource(R.string.history_chip_missed)
    is DoseChip.Pending -> stringResource(R.string.history_chip_pending)
    is DoseChip.Upcoming -> stringResource(R.string.history_chip_upcoming)
}
