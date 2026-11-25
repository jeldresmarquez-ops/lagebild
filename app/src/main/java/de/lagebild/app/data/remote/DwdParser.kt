package de.lagebild.app.data.remote

import de.lagebild.app.domain.model.WarnLevel
import de.lagebild.app.domain.model.Warning
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Deutscher Wetterdienst, offene Warn-Daten (https://dwd.api.bund.dev).
 * Die Datei ist JSONP: `warnWetter.loadWarnings({...});`
 */
object DwdParser {

    const val URL = "https://www.dwd.de/DWD/warnungen/warnapp/json/warnings.json"

    /** Warncell-IDs Berlins beginnen mit 111 (Land Berlin = 111000000). */
    const val BERLIN_PREFIX = "111"

    @Serializable
    data class Root(val time: Long = 0, val warnings: Map<String, List<DwdWarning>> = emptyMap())

    @Serializable
    data class DwdWarning(
        val type: Int = 0,
        val level: Int = 1,
        val start: Long? = null,
        val end: Long? = null,
        val regionName: String? = null,
        val event: String? = null,
        val headline: String? = null,
        val description: String? = null,
        val instruction: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(jsonp: String, cellPrefix: String = BERLIN_PREFIX): List<Warning> {
        val start = jsonp.indexOf('(')
        val end = jsonp.lastIndexOf(')')
        val body = if (start >= 0 && end > start) jsonp.substring(start + 1, end) else jsonp
        val root = json.decodeFromString(Root.serializer(), body)
        return root.warnings
            .filterKeys { it.startsWith(cellPrefix) }
            .flatMap { (cell, list) ->
                list.map { w ->
                    Warning(
                        id = "dwd-$cell-${w.type}-${w.start}-${w.level}",
                        provider = "Deutscher Wetterdienst",
                        title = w.headline ?: w.event ?: "Wetterwarnung",
                        description = w.description,
                        instruction = w.instruction?.takeIf { it.isNotBlank() },
                        level = levelOf(w.level),
                        start = w.start,
                        end = w.end,
                        sentAt = w.start ?: root.time,
                    )
                }
            }
            .distinctBy { it.title + it.start }
    }

    /** DWD-Stufen: 1 Wetterwarnung (gelb), 2 markant (orange), 3 Unwetter (rot), 4 extrem (violett). */
    fun levelOf(dwdLevel: Int): WarnLevel = when {
        dwdLevel >= 4 -> WarnLevel.EXTREME
        dwdLevel == 3 -> WarnLevel.SEVERE
        dwdLevel == 2 -> WarnLevel.MODERATE
        else -> WarnLevel.MINOR
    }
}
