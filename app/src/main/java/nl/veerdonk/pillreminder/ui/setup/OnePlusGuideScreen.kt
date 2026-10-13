package nl.veerdonk.pillreminder.ui.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.platform.PermissionChecker
import nl.veerdonk.pillreminder.platform.SetupItem
import nl.veerdonk.pillreminder.ui.common.AppCard
import nl.veerdonk.pillreminder.ui.common.BackButton

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

    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.oneplus_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.oneplus_intro), style = MaterialTheme.typography.bodyLarge) }
            itemsIndexed(GUIDE_STEPS) { i, step ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(step.title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(step.body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.oneplus_verify), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            item { Text(stringResource(R.string.oneplus_after_update), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            item {
                Button(
                    onClick = { startSettings(context, checker, SetupItem.ONEPLUS_EXTRAS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) { Text(stringResource(R.string.oneplus_open_app_settings), style = MaterialTheme.typography.titleMedium) }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = done,
                            role = Role.Checkbox,
                            onValueChange = {
                                done = it
                                checker.onePlusDone = it
                            },
                        ),
                ) {
                    // The whole row toggles; the checkbox itself only shows the state.
                    Checkbox(checked = done, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(stringResource(R.string.oneplus_done_checkbox), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
