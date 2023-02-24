package ca.amandeep.path.data

import ca.amandeep.path.data.model.Station
import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.util.tickFlow
import ca.amandeep.path.util.zip
import com.github.ajalt.timberkt.d
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
    private val updateInterval: Duration,
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
    val arrivals: Flow<Result>
        get() {
            // Merge tick flow to periodically poll the API, and the refresh flow to force a refresh
            return merge(tickFlow(updateInterval), refreshFlow).flatMapLatest {
                stations
                    // Gets arrivals for all stations
                    .flatMapLatest { fetchArrivals(it.stations.orEmpty()) }
                    .map {
                        Result(Result.Metadata(System.currentTimeMillis()), it).also {
                            d { "new wallTime: ${it.metadata.lastUpdated}" }
                        }
                    }
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

    data class Result(
        val metadata: Metadata,
        val arrivals: Map<Station, List<UpcomingTrain>>,
    ) {
        data class Metadata(
            val lastUpdated: Long,
        )
    }
}
