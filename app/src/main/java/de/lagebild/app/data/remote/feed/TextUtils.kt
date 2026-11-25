package de.lagebild.app.data.remote.feed

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

object TextUtils {

    private val tagRegex = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")
    private val imgSrc = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    private val entities = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
        "&nbsp;" to " ", "&ndash;" to "–", "&mdash;" to "—", "&hellip;" to "…", "&bdquo;" to "„",
        "&ldquo;" to "“", "&rdquo;" to "”", "&lsquo;" to "‘", "&rsquo;" to "’", "&auml;" to "ä",
        "&ouml;" to "ö", "&uuml;" to "ü", "&Auml;" to "Ä", "&Ouml;" to "Ö", "&Uuml;" to "Ü", "&szlig;" to "ß",
    )
    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")

    /** Entfernt HTML, dekodiert Entities und kürzt auf einen Teaser. */
    fun cleanHtml(html: String?, maxLength: Int = 400): String? {
        if (html.isNullOrBlank()) return null
        var text = html.replace(tagRegex, " ")
        text = decodeEntities(text)
        text = text.replace(whitespace, " ").trim()
        if (text.isEmpty()) return null
        return if (text.length > maxLength) text.take(maxLength).substringBeforeLast(' ') + " …" else text
    }

    fun decodeEntities(input: String): String {
        var text = input
        entities.forEach { (k, v) -> text = text.replace(k, v) }
        return numericEntity.replace(text) { m ->
            val radix = if (m.groupValues[1].isNotEmpty()) 16 else 10
            m.groupValues[2].toIntOrNull(radix)?.let { String(Character.toChars(it)) } ?: m.value
        }
    }

    fun firstImage(html: String?): String? = html?.let { imgSrc.find(it)?.groupValues?.get(1) }

    private val rfc822Variants: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("EEE, d MMM yyyy HH:mm[:ss] [zzz][Z][X]").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("d MMM yyyy HH:mm[:ss] [zzz][Z][X]").toFormatter(Locale.ENGLISH),
    )

    /** Versteht RFC-822 (RSS) und ISO-8601 (Atom/JSON). Liefert Epoch-Millisekunden. */
    fun parseDate(raw: String?): Long? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s).toInstant().toEpochMilli() }
        // Nur Datum (z. B. Nature: "2026-11-24") → 12:00 Uhr UTC
        runCatching { return LocalDate.parse(s).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli() }
        for (f in rfc822Variants) {
            runCatching { return ZonedDateTime.parse(s, f).toInstant().toEpochMilli() }
        }
        // "GMT"/"UT" ohne Offset-Zeichen, "+0100"-Varianten u. Ä.
        val fixed = s.replace(" UT", " GMT").replace(Regex(" ([+-]\\d{2})(\\d{2})$"), " $1:$2")
        for (f in rfc822Variants) {
            runCatching { return ZonedDateTime.parse(fixed, f).toInstant().toEpochMilli() }
        }
        return null
    }
}
