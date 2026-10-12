package nl.veerdonk.pillreminder.core.serial

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Base for java.time types encoded as their ISO-8601 string form. */
abstract class IsoStringSerializer<T : Any>(
    serialName: String,
    private val parse: (String) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

/** e.g. `2026-10-12T05:25:00Z` */
object InstantSerializer : IsoStringSerializer<Instant>("nl.veerdonk.Instant", Instant::parse)

/** e.g. `2026-10-12` */
object LocalDateSerializer : IsoStringSerializer<LocalDate>("nl.veerdonk.LocalDate", LocalDate::parse)

/** e.g. `07:00` */
object LocalTimeSerializer : IsoStringSerializer<LocalTime>("nl.veerdonk.LocalTime", LocalTime::parse)

/** e.g. `PT3H50M` */
object DurationSerializer : IsoStringSerializer<Duration>("nl.veerdonk.Duration", Duration::parse)

/**
 * The JSON configuration used to persist core model objects. Unknown keys are ignored so a
 * newer file can still be read by an older build; defaults are written so the file is
 * self-describing.
 */
val CoreJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
