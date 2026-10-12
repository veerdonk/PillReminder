package nl.veerdonk.pillreminder.platform

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.core.wake.WakeCheck
import nl.veerdonk.pillreminder.core.wake.WakeRule
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * The last wake check, for the debug "Detection log" (T10 renders [describe] at the bottom of
 * Settings → Advanced). [check] is null when Usage Access was missing.
 */
data class DetectionLogEntry(val from: Instant, val to: Instant, val check: WakeCheck?) {
    val usageAccessMissing: Boolean get() = check == null

    /** One fact per line, for a monospace text block and for logcat. */
    fun describe(): String = if (check == null) {
        "Usage access missing -> not awake\nwindow $from .. $to"
    } else {
        "unlocks: ${check.unlocks}\n" +
            "foreground: ${formatMinutes(check.foregroundTime)} min\n" +
            "result: ${if (check.awake) "awake" else "not awake"}\n" +
            "window $from .. $to"
    }

    private fun formatMinutes(d: Duration): String = String.format(Locale.ROOT, "%.1f", d.toMillis() / 60_000.0)
}

/** [WakeDetector] on the usage log (design §6). */
class AndroidWakeDetector(private val context: Context) : WakeDetector {

    private val source = UsageEventSource(context)
    private val _lastCheck = MutableStateFlow<DetectionLogEntry?>(null)

    /** The most recent check (null before the first). */
    val lastCheck: StateFlow<DetectionLogEntry?> = _lastCheck

    override suspend fun isAwake(from: Instant, to: Instant, settings: Settings): Boolean {
        if (!hasUsageAccess()) {
            record(DetectionLogEntry(from, to, null))
            return false
        }
        val check = withContext(Dispatchers.IO) {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            WakeRule.evaluate(
                events = source.query(from, to),
                from = from,
                to = to,
                s = settings,
                // Without a lock screen there are no KEYGUARD_HIDDEN events.
                countScreenOnAsUnlock = !keyguard.isDeviceSecure,
                excludedPackages = excludedPackages(context.packageName, defaultLauncher()),
            )
        }
        record(DetectionLogEntry(from, to, check))
        return check.awake
    }

    private fun record(entry: DetectionLogEntry) {
        _lastCheck.value = entry
        Log.d(TAG, entry.describe().replace('\n', ' '))
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun defaultLauncher(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    companion object {
        const val TAG = "WakeDetect"
        const val SYSTEM_UI = "com.android.systemui"

        /** Packages whose foreground time doesn't count as use (design §6). */
        fun excludedPackages(ownPackage: String, launcher: String?): Set<String> =
            setOfNotNull(ownPackage, SYSTEM_UI, launcher)
    }
}
