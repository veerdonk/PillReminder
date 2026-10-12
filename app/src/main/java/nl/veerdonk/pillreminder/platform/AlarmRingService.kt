package nl.veerdonk.pillreminder.platform

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.veerdonk.pillreminder.PillReminderApp
import nl.veerdonk.pillreminder.R
import nl.veerdonk.pillreminder.core.model.Settings
import nl.veerdonk.pillreminder.ui.alarm.AlarmActivity
import java.time.Duration

/**
 * Foreground service that makes the alarm ring (docs/03-technical-design.md §8): looping sound
 * (unless the phone is silent or on Do Not Disturb), vibration, a wake lock, and the alarm
 * notification with its full-screen intent. It is started by [AndroidRingController].
 *
 * The engine ends a real ring through its `ringEndAt` wake-up; the service only has a safety
 * stop at `alarmRingDuration + 1 min`. A test alarm stops itself after [TEST_DURATION].
 */
class AlarmRingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ring: ActiveRing? = null
    private var feedbackJob: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = RingExtras.read(intent)
        if (requested == null) {
            // Only the app starts this service, always with a spec. Still, startForeground must
            // be called after startForegroundService, so show something before stopping.
            Log.e(TAG, "Started without a ring spec")
            ServiceCompat.startForeground(
                this,
                AndroidNotifier.ALARM_NOTIFICATION_ID,
                notificationFor(ActiveRing.test()),
                FOREGROUND_TYPE_SYSTEM_EXEMPTED,
            )
            stopRinging()
            return START_NOT_STICKY
        }
        val change = ringChange(ring, requested)
        // startForeground on every start, also when nothing changed: the system expects it after
        // each startForegroundService call. Re-posting an equal notification doesn't re-alert.
        ServiceCompat.startForeground(
            this,
            AndroidNotifier.ALARM_NOTIFICATION_ID,
            notificationFor(requested),
            FOREGROUND_TYPE_SYSTEM_EXEMPTED,
        )
        when (change) {
            RingChange.NONE -> Unit
            RingChange.UPDATE_NOTIFICATION -> ring = requested
            RingChange.START -> {
                stopFeedback()
                ring = requested
                feedbackJob = scope.launch { startFeedback(requested) }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val own = ring
        stopFeedback()
        scope.cancel()
        // Destroyed by the system rather than by stopRinging(): forget what we were ringing.
        if (own != null) RingState.current.compareAndSet(own, null)
        super.onDestroy()
    }

    private fun notificationFor(active: ActiveRing): Notification =
        if (active.test) testNotification(active) else (application as PillReminderApp).container.notifier.buildAlarmNotification(active.spec)

    /** A test alarm has no buttons: the real ones would act on a dose that doesn't exist. */
    private fun testNotification(active: ActiveRing): Notification {
        val screen = PendingIntent.getActivity(
            this,
            REQUEST_TEST_SCREEN,
            AlarmActivity.intent(this, active),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, AndroidNotifier.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(getString(R.string.alarm_test_title))
            .setContentText(getString(R.string.alarm_test_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .build()
    }

    private suspend fun startFeedback(active: ActiveRing) {
        val settings = (application as PillReminderApp).container.settingsRepository.current()
        val ringDuration = if (active.test) TEST_DURATION else settings.alarmRingDuration
        acquireWakeLock(ringDuration.plus(WAKE_LOCK_MARGIN))

        val audioManager = getSystemService(AudioManager::class.java)
        val notificationManager = getSystemService(NotificationManager::class.java)
        val quiet = !shouldPlaySound(audioManager.ringerMode, notificationManager.currentInterruptionFilter)
        if (!quiet) startSound(settings)
        startVibration()
        launchAlarmScreen(active)

        // Safety net for a real ring (the engine normally ends it); the exact end of a test.
        delay((if (active.test) TEST_DURATION else ringDuration.plus(SAFETY_MARGIN)).toMillis())
        RingState.current.compareAndSet(active, null)
        stopRinging()
    }

    private fun startSound(settings: Settings) {
        val candidates = listOfNotNull(
            settings.alarmSoundUri?.toUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        for (uri in candidates) {
            val mediaPlayer = MediaPlayer()
            try {
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                mediaPlayer.setDataSource(this, uri)
                mediaPlayer.isLooping = true
                mediaPlayer.prepare()
                mediaPlayer.start()
                player = mediaPlayer
                return
            } catch (e: Exception) {
                // E.g. a removed custom sound: try the next candidate.
                Log.w(TAG, "Can't play $uri", e)
                mediaPlayer.release()
            }
        }
    }

    private fun startVibration() {
        val vib = getSystemService(VibratorManager::class.java).defaultVibrator
        vibrator = vib
        vib.vibrate(
            VibrationEffect.createWaveform(VIBRATION_PATTERN, 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
    }

    private fun acquireWakeLock(timeout: Duration) {
        wakeLock?.release()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { acquire(timeout.toMillis()) }
    }

    /** For when the screen is already on and a heads-up would show instead of the full-screen intent. */
    private fun launchAlarmScreen(active: ActiveRing) {
        try {
            startActivity(AlarmActivity.intent(this, active))
        } catch (e: RuntimeException) {
            // Background activity starts can be refused; the full-screen intent is the main route.
            Log.w(TAG, "Can't launch the alarm screen", e)
        }
    }

    private fun stopFeedback() {
        feedbackJob?.cancel()
        feedbackJob = null
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
                // Never started; release below.
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopRinging() {
        stopFeedback()
        ring = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "AlarmRingService"
        private const val WAKE_LOCK_TAG = "PillReminder:AlarmRing"
        private const val REQUEST_TEST_SCREEN = 102

        // ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED (1 shl 10), which is API 34. The
        // manifest declares the same type, and Android 13 only checks the bits against it.
        private const val FOREGROUND_TYPE_SYSTEM_EXEMPTED = 1 shl 10

        /** Settings "Test alarm" ring time (design §8). */
        val TEST_DURATION: Duration = Duration.ofSeconds(10)
        private val WAKE_LOCK_MARGIN: Duration = Duration.ofSeconds(30)
        private val SAFETY_MARGIN: Duration = Duration.ofMinutes(1)

        /** 800 ms on, 600 ms off, repeating from index 0 (design §8). */
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 600)

        internal fun intent(context: Context, ring: ActiveRing): Intent =
            RingExtras.put(Intent(context, AlarmRingService::class.java), ring)
    }
}
