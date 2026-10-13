package nl.veerdonk.pillreminder.ui.alarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.AppContainer
import nl.veerdonk.pillreminder.PillReminderApp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.engine.Engine
import nl.veerdonk.pillreminder.core.model.DoseSource
import nl.veerdonk.pillreminder.core.model.Event
import nl.veerdonk.pillreminder.core.model.RingSpec
import nl.veerdonk.pillreminder.platform.ActiveRing
import nl.veerdonk.pillreminder.platform.RingExtras
import nl.veerdonk.pillreminder.platform.RingState
import nl.veerdonk.pillreminder.ui.common.IconBadge
import nl.veerdonk.pillreminder.ui.common.Tag
import nl.veerdonk.pillreminder.ui.theme.PillReminderTheme
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The full-screen alarm (docs/03-technical-design.md §8, FR-3.2): shown over the lock screen
 * with the screen turned on. Big buttons: "Took it", "Snooze (X left)" while any are left, and
 * "Not today" (dose 1) or "I'm done for today". They call `controller.handle(...)` on the app
 * scope, with the dayId this screen was opened for as `expectedDayId`.
 *
 * The screen watches the day state and finishes itself as soon as the phase is no longer RINGING
 * for its dose, whoever ended the ring (a button here, the notification, the engine's timeout).
 * Back does nothing, so the only way out is a decision.
 *
 * A test alarm (Settings) shows the same screen; its buttons only close it.
 */
class AlarmActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as PillReminderApp).container

    /** The dose this screen is about; replaced when a newer alarm re-opens the activity. */
    private val target = MutableStateFlow<ActiveRing?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Back does nothing: the alarm is dismissed through a decision only.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        val first = RingExtras.read(intent)
        if (first == null) {
            finish()
            return
        }
        target.value = first

        val shown = MutableStateFlow<RingSpec?>(null)
        lifecycleScope.launch {
            combine(
                target,
                container.stateStore.state,
                container.settingsRepository.settings,
                RingState.current,
            ) { wanted, state, settings, active ->
                when {
                    wanted == null -> Display.Finish
                    // A test alarm lasts as long as its service rings (10 s, or until a button).
                    wanted.test -> if (active?.test == true) Display.Show(testSpec(settings.dosesPerDay, settings.maxSnoozes)) else Display.Finish
                    state == null -> Display.Finish
                    else -> {
                        val ringing = Engine.outputs(state, settings, container.clock.instant(), ZoneId.systemDefault()).ringing
                        displayFor(wanted.spec.dayId, wanted.spec.dose, ringing)
                    }
                }
            }.collect { display ->
                when (display) {
                    Display.Finish -> finish()
                    is Display.Show -> shown.value = display.spec
                }
            }
        }

        setContent {
            PillReminderTheme {
                val spec by shown.collectAsState()
                val isTest by remember { target.map { it?.test == true } }.collectAsState(initial = false)
                spec?.let { current ->
                    AlarmScreen(
                        spec = current,
                        test = isTest,
                        clock = container.clock,
                        onTook = { decide(Event.TookDose(current.dose, container.clock.instant(), DoseSource.ALARM)) },
                        onSnooze = { decide(Event.Snooze(current.dose)) },
                        onSkip = { decide(Event.SkipRest(current.dose)) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RingExtras.read(intent)?.let { target.value = it }
    }

    private fun decide(event: Event) {
        val wanted = target.value ?: return
        if (wanted.test) {
            container.testAlarm.stopTestAlarm()
            finish()
            return
        }
        // The state observer finishes the activity once the engine has moved on.
        container.appScope.launch { container.reminderController.handle(event, expectedDayId = wanted.spec.dayId) }
    }

    companion object {
        /** Opens (or re-opens) the alarm screen for [ring]. */
        internal fun intent(context: Context, ring: ActiveRing): Intent =
            RingExtras.put(Intent(context, AlarmActivity::class.java), ring)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** What the alarm screen should do for the current state. */
internal sealed interface Display {
    data object Finish : Display
    data class Show(val spec: RingSpec) : Display
}

/**
 * The alarm screen shows [ringing] only while the engine says it rings for exactly the dose
 * ([dayId], [dose]) the screen was opened for; otherwise it finishes.
 */
internal fun displayFor(dayId: LocalDate, dose: Int, ringing: RingSpec?): Display =
    if (ringing != null && ringing.dayId == dayId && ringing.dose == dose) Display.Show(ringing) else Display.Finish

/** What a test alarm shows: pill 1 with snoozes available, so all three buttons are visible. */
internal fun testSpec(dosesPerDay: Int, maxSnoozes: Int): RingSpec =
    RingSpec(
        dayId = ActiveRing.TEST_SPEC.dayId,
        dose = 1,
        doseCount = dosesPerDay,
        canSnooze = maxSnoozes > 0,
        snoozesLeft = maxSnoozes,
    )

@Composable
internal fun AlarmScreen(
    spec: RingSpec,
    test: Boolean,
    clock: Clock,
    onTook: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
) {
    var now by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.instant()
            delay(CLOCK_REFRESH_MS)
        }
    }
    val colors = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (test) {
                Tag(stringResource(R.string.alarm_test_title), colors.tertiaryContainer, colors.onTertiaryContainer)
                Spacer(Modifier.height(24.dp))
            }
            // The pill in a soft accent glow: the one thing to notice first.
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(colors.primary.copy(alpha = 0.28f), colors.primary.copy(alpha = 0f)))),
                contentAlignment = Alignment.Center,
            ) {
                IconBadge(R.drawable.ic_stat_pill, colors.primary, colors.onPrimary, size = 96.dp, iconSize = 72.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.alarm_pill_progress, spec.dose, spec.doseCount),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = CLOCK_FORMAT.format(now.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 104.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
            Button(onClick = onTook, modifier = BigButton, shape = MaterialTheme.shapes.large) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.alarm_took), style = MaterialTheme.typography.headlineSmall)
            }
            if (spec.canSnooze) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onSnooze, modifier = BigButton, shape = MaterialTheme.shapes.large) {
                    Text(
                        pluralStringResource(R.plurals.action_snooze, spec.snoozesLeft, spec.snoozesLeft),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSkip, modifier = BigButton, shape = MaterialTheme.shapes.large) {
                val label = if (spec.dose == 1) R.string.action_not_today else R.string.action_done_for_today
                Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private val BigButton = Modifier
    .fillMaxWidth()
    .height(80.dp)

private const val CLOCK_REFRESH_MS = 5_000L
private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
