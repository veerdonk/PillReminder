package nl.veerdonk.pillreminder.ui.setup

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.PermissionChecker
import nl.veerdonk.pillreminder.platform.SetupItem

private class GuideStep(@StringRes val title: Int, @StringRes val body: Int)

// Steps 6-9 of docs/06-device-setup-oneplus.md; steps 1-5 are the buttons on the checklist.
private val GUIDE_STEPS = listOf(
    GuideStep(R.string.oneplus_step_autolaunch_title, R.string.oneplus_step_autolaunch),
    GuideStep(R.string.oneplus_step_lock_title, R.string.oneplus_step_lock),
    GuideStep(R.string.oneplus_step_sleep_title, R.string.oneplus_step_sleep),
    GuideStep(R.string.oneplus_step_dnd_title, R.string.oneplus_step_dnd),
)

/** The manual OnePlus steps, with a button to app settings and the "I've done this" checkbox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnePlusGuideScreen(checker: PermissionChecker, onBack: () -> Unit) {
    val context = LocalContext.current
    var done by remember { mutableStateOf(checker.onePlusDone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.oneplus_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.setup_navigate_back)) }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.oneplus_intro), style = MaterialTheme.typography.bodyLarge) }
            items(GUIDE_STEPS) { step ->
                Column {
                    Text(stringResource(step.title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(step.body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Text(stringResource(R.string.oneplus_verify), style = MaterialTheme.typography.bodyMedium) }
            item { Text(stringResource(R.string.oneplus_after_update), style = MaterialTheme.typography.bodyMedium) }
            item {
                Button(
                    onClick = { startSettings(context, checker, SetupItem.ONEPLUS_EXTRAS) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.oneplus_open_app_settings)) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = {
                            done = it
                            checker.onePlusDone = it
                        },
                    )
                    Text(stringResource(R.string.oneplus_done_checkbox), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
