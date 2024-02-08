package ca.amandeep.path.data

import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.Station
import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.util.tickFlow
import ca.amandeep.path.util.zip
import com.github.ajalt.timberkt.d
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEmpty
import kotlin.time.Duration

/**
 * Repository for getting stations and arrivals information. Stations are cached,
 * but arrivals are periodically polled from the API.
 */
class PathRepository(
    private val pathRemoteDataSource: PathRemoteDataSource,
    private val arrivalsUpdateInterval: Duration,
    private val alertsUpdateInterval: Duration,
) {
    private var stationsCache: Stations? = null
    private var refreshFlow = MutableSharedFlow<Unit>()

    /**
     * Get the list of stations from the cache, or fetch it from the API if it's not cached.
     */
    val stations: Flow<Stations> by lazy {
        flow {
            stationsCache = (stationsCache ?: pathRemoteDataSource.getStations()).also {
                emit(it)
            }
        }
    }

    /**
     * Get the list of arrivals for a station, periodically polling the API for updates.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val arrivals: Flow<ArrivalsResult>
        get() =
            // Merge tick flow to periodically poll the API, and the refresh flow to force a refresh
            merge(tickFlow(arrivalsUpdateInterval), refreshFlow).flatMapLatest {
                stations
                    // Gets arrivals for all stations
                    .flatMapLatest { fetchArrivals(it.stations.orEmpty()) }
                    .map {
                        ArrivalsResult(
                            metadata = Metadata(System.currentTimeMillis()),
                            arrivals = it.mapValues { it.value.toImmutableList() }.toImmutableMap(),
                        ).also {
                            d { "new arrivals wallTime: ${it.metadata.lastUpdated}" }
                        }
                    }
            }

    val alerts: Flow<AlertsResult>
        get() =
            // Merge tick flow to periodically poll the API, and the refresh flow to force a refresh
            merge(tickFlow(alertsUpdateInterval), refreshFlow).map {
                AlertsResult(
                    Metadata(System.currentTimeMillis()),
                    pathRemoteDataSource.getAlerts(),
                ).also {
                    d { "new alerts wallTime: ${it.metadata.lastUpdated}" }
                }
            }

    // Workaround for the API not returning a list of stations with trains data
    private fun fetchArrivals(stations: List<Station>): Flow<Map<Station, List<UpcomingTrain>>> =
        stations.map { flow { emit(it to pathRemoteDataSource.getArrivals(it.station)) } }
            // Zip all the flows together, so that we can emit a single map of stations to arrivals
            .zip { it.associate { it.first to it.second.upcomingTrains.orEmpty() } }
            // If the API returns an empty list, emit an empty map so there is an emission
            .onEmpty { emit(emptyMap()) }

    /**
     * Refreshes the data from the remote data source.
     */
    suspend fun refresh() = refreshFlow.emit(Unit)

    data class ArrivalsResult(
        val metadata: Metadata = Metadata(),
        val arrivals: ImmutableMap<Station, ImmutableList<UpcomingTrain>> = persistentMapOf(),
    )

    data class AlertsResult(
        val metadata: Metadata = Metadata(),
        val alerts: AlertDatas = AlertDatas(),
    )

    data class Metadata(
        val lastUpdated: Long = -1,
    )
}
