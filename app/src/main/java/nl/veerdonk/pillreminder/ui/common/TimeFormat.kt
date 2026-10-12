package nl.veerdonk.pillreminder.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** HH:mm in [zone], always 24-hour (NFR-5, assumption A18). */
fun formatTime(instant: Instant, zone: ZoneId): String = HH_MM.format(instant.atZone(zone))

fun formatTime(time: LocalTime): String = HH_MM.format(time)

/** "Mon 12 Oct 2026" in the device language. */
fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()).format(date)
