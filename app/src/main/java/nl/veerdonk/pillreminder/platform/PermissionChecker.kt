package nl.veerdonk.pillreminder.platform

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit

/**
 * Checks the permissions and settings listed in docs/03-technical-design.md §9, and knows the
 * system screen that fixes each. The notification row's runtime request is done by the UI,
 * which falls back to [fixIntent].
 */
interface PermissionChecker {
    fun check(): SetupStatus

    /** The system screen where the user can fix [item]. */
    fun fixIntent(item: SetupItem): Intent

    /** Opens this app's details page in system settings (OnePlus guide). */
    fun appDetailsIntent(): Intent

    /** Whether the user has ticked "I've done this" for the OnePlus extras (can't be detected). */
    var onePlusDone: Boolean

    /** Whether the Setup screen has been completed once (first-launch rule, FR-9.1). */
    var setupSeen: Boolean
}

class AndroidPermissionChecker(private val context: Context) : PermissionChecker {

    // A tiny file instead of the settings DataStore: this is UI state, not part of Settings.
    private val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    override var onePlusDone: Boolean
        get() = prefs.getBoolean(KEY_ONEPLUS_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONEPLUS_DONE, value) }

    override var setupSeen: Boolean
        get() = prefs.getBoolean(KEY_SETUP_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_SETUP_SEEN, value) }

    override fun check(): SetupStatus = SetupStatus(
        SetupItem.entries.filterTo(mutableSetOf(), ::isOk),
    )

    private fun isOk(item: SetupItem): Boolean = when (item) {
        SetupItem.NOTIFICATIONS -> notificationsOk()
        SetupItem.USAGE_ACCESS -> usageAccessOk()
        SetupItem.EXACT_ALARMS -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        SetupItem.FULL_SCREEN_INTENT -> fullScreenIntentOk()
        SetupItem.BATTERY_OPTIMISATION ->
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        SetupItem.MAPS_NAVIGATION ->
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        SetupItem.ONEPLUS_EXTRAS -> onePlusDone
    }

    /** Enabled overall, and neither of our channels switched off ("no importance"). */
    private fun notificationsOk(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return listOf(AndroidNotifier.CHANNEL_REMINDERS, AndroidNotifier.CHANNEL_ALARM).none { id ->
            manager.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
        }
    }

    // The call design §9 names; the newer overloads need a higher API level than minSdk 33.
    @Suppress("DEPRECATION")
    private fun usageAccessOk(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun fullScreenIntentOk(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    override fun fixIntent(item: SetupItem): Intent = when (item) {
        SetupItem.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        SetupItem.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        SetupItem.EXACT_ALARMS ->
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())
        SetupItem.FULL_SCREEN_INTENT ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri())
            } else {
                appDetailsIntent()
            }
        SetupItem.BATTERY_OPTIMISATION -> batteryIntent()
        SetupItem.MAPS_NAVIGATION ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, MapsNavigationListener::class.java).flattenToString(),
            )
        SetupItem.ONEPLUS_EXTRAS -> appDetailsIntent()
    }

    // Allowed: the app is sideloaded and never goes on Play (the manifest says the same).
    @SuppressLint("BatteryLife")
    private fun batteryIntent() = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())

    override fun appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

    private fun packageUri(): Uri = "package:${context.packageName}".toUri()

    companion object {
        const val PREFS_FILE = "setup_prefs"
        private const val KEY_ONEPLUS_DONE = "oneplus_done"
        private const val KEY_SETUP_SEEN = "setup_seen"
    }
}
