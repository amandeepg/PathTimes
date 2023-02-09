package ca.amandeep.path.data

import ca.amandeep.path.Station
import ca.amandeep.path.Stations
import ca.amandeep.path.UpcomingTrain
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
import java.util.*
import kotlin.time.Duration

/**
 * Repository for getting stations and arrivals information. Stations are cached,
 * but arrivals are periodically polled from the API.
 */
class PathRepository(
    private val pathRemoteDataSource: PathRemoteDataSource
) {

    private var stationsCache: Stations? = null
    private var refreshFlow = MutableSharedFlow<Unit>()

    /**
     * Get the list of stations from the cache, or fetch it from the API if it's not cached.
     */
    fun getStations(): Flow<Stations> = flow {
        stationsCache = (stationsCache ?: pathRemoteDataSource.fetchStations()).also {
            emit(it)
        }
    }

    /**
     * Get the list of arrivals for a station, periodically polling the API for updates.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getArrivals(updateInterval: Duration): Flow<Result> =
        // Merge tick flow to periodically poll the API, and the refresh flow to force a refresh
        merge(tickFlow(updateInterval), refreshFlow).flatMapLatest {
            getStations()
                // Gets arrivals for all stations
                .flatMapLatest { fetchArrivals(it.stations.orEmpty()) }
                .map {
                    Result(Result.Metadata(System.currentTimeMillis()), it).also {
                        d { "new wallTime: ${it.metadata.lastUpdated}" }
                    }
                }
        }

    // Workaround for the API not returning a list of stations with trains data
    private fun fetchArrivals(stations: List<Station>): Flow<Map<Station, List<UpcomingTrain>>> =
        stations.map { flow { emit(it to pathRemoteDataSource.fetchArrivals(it.station)) } }
            .zip { it.associate { it.first to it.second.upcomingTrains.orEmpty() } }
            .onEmpty { emit(emptyMap()) }

    /**
     * Refreshes the data from the remote data source.
     */
    suspend fun refresh() = refreshFlow.emit(Unit)

    data class Result(
        val metadata: Metadata,
        val arrivals: Map<Station, List<UpcomingTrain>>
    ) {
        data class Metadata(
            val lastUpdated: Long
        )
    }
}