package nl.veerdonk.pillreminder

import android.content.Context

/**
 * Manual dependency container (no Hilt). Repositories, platform services and the
 * ReminderController are added here by later tasks (see docs/03-technical-design.md §2).
 */
class AppContainer(@Suppress("unused") private val appContext: Context)
