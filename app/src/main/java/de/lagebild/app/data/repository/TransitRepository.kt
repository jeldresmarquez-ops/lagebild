package de.lagebild.app.data.repository

import de.lagebild.app.data.remote.VbbApi
import de.lagebild.app.data.remote.VbbDeparture
import de.lagebild.app.domain.model.DisruptionKind
import de.lagebild.app.domain.model.TransitDisruption
import de.lagebild.app.domain.model.TransitStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class TransitState(
    val disruptions: List<TransitDisruption> = emptyList(),
    val updatedAt: Long = 0,
    val error: String? = null,
)

/**
 * Störungen für BVG/S-Bahn/VBB. Datenquelle: transport.rest (offene HAFAS-Schnittstelle des VBB).
 * Es gibt keine offizielle "Alle Störungen"-API; daher werden die Abfahrten deiner
 * Lieblingshaltestellen abgefragt und deren Störungshinweise, Ausfälle und große Verspätungen ausgewertet.
 */
@Singleton
class TransitRepository @Inject constructor(
    private val api: VbbApi,
) {
    private val _state = MutableStateFlow(TransitState())
    val state: StateFlow<TransitState> = _state.asStateFlow()

    suspend fun searchStations(query: String): List<TransitStation> =
        api.locations(query).filter { it.type == "stop" || it.type == "station" }
            .mapNotNull { l -> if (l.id != null && l.name != null) TransitStation(l.id, l.name) else null }

    suspend fun refresh(stations: List<TransitStation>, favoriteLines: Set<String>): TransitState {
        if (stations.isEmpty()) {
            return TransitState(updatedAt = System.currentTimeMillis()).also { _state.value = it }
        }
        val result = runCatching {
            coroutineScope {
                stations.map { s -> async { s to api.departures(s.id).departures } }.awaitAll()
            }
        }
        result.exceptionOrNull()?.let { e ->
            if (e is CancellationException) throw e
            val s = _state.value.copy(error = "Verkehrsdaten nicht erreichbar")
            _state.value = s
            return s
        }
        val disruptions = result.getOrThrow()
            .flatMap { (station, deps) -> extractDisruptions(station, deps, favoriteLines) }
            .groupBy { it.id }
            .map { (_, list) -> list.first().copy(lines = list.flatMap { it.lines }.distinct().sorted()) }
            .sortedBy { it.kind.ordinal }
        return TransitState(disruptions, System.currentTimeMillis()).also { _state.value = it }
    }

    companion object {
        private const val BIG_DELAY_SECONDS = 10 * 60

        fun extractDisruptions(
            station: TransitStation,
            departures: List<VbbDeparture>,
            favoriteLines: Set<String>,
        ): List<TransitDisruption> {
            val favorites = favoriteLines.map { it.uppercase() }.toSet()
            val relevant = departures.filter { d ->
                val line = d.line?.name?.uppercase() ?: return@filter false
                favorites.isEmpty() || line in favorites
            }
            val out = ArrayList<TransitDisruption>()

            // 1) Offizielle Störungsmeldungen (HIM-Meldungen, remark.type == "warning")
            relevant.flatMap { d -> d.remarks.filter { it.type == "warning" }.map { d.line!!.name!! to it } }
                .groupBy { (_, r) -> r.id ?: r.summary ?: r.text.orEmpty() }
                .forEach { (key, pairs) ->
                    val r = pairs.first().second
                    out += TransitDisruption(
                        id = "warn-$key",
                        kind = DisruptionKind.WARNING,
                        lines = pairs.map { it.first }.distinct(),
                        title = r.summary ?: r.text?.take(120) ?: "Störung",
                        text = r.text?.takeIf { it != r.summary },
                        stationName = station.name,
                    )
                }

            // 2) Ausfälle
            relevant.filter { it.cancelled }.groupBy { it.line!!.name!! }.forEach { (line, list) ->
                out += TransitDisruption(
                    id = "cancel-${station.id}-$line",
                    kind = DisruptionKind.CANCELLATION,
                    lines = listOf(line),
                    title = if (list.size == 1) "Eine Fahrt fällt aus" else "${list.size} Fahrten fallen aus",
                    text = list.mapNotNull { it.direction }.distinct().joinToString(prefix = "Richtung ").takeIf { it.length > 9 },
                    stationName = station.name,
                )
            }

            // 3) Große Verspätungen (ab 10 Minuten)
            relevant.filter { !it.cancelled && (it.delay ?: 0) >= BIG_DELAY_SECONDS }
                .groupBy { it.line!!.name!! }
                .forEach { (line, list) ->
                    val maxMin = list.maxOf { it.delay ?: 0 } / 60
                    out += TransitDisruption(
                        id = "delay-${station.id}-$line",
                        kind = DisruptionKind.DELAY,
                        lines = listOf(line),
                        title = "Verspätungen bis $maxMin Minuten",
                        text = null,
                        stationName = station.name,
                    )
                }
            return out
        }
    }
}
