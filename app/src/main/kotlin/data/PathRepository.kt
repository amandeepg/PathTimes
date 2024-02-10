package ca.amandeep.path.data

import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.Coordinates
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEmpty
import okhttp3.internal.immutableListOf
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
    private var refreshFlow = MutableSharedFlow<Unit>()

    val stations: Flow<Stations> = flowOf(STATIONS)

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

private val STATIONS = Stations(
    stations = immutableListOf(
        Station(
            station = "NEWARK",
            name = "Newark",
            coordinates = Coordinates(
                latitude = 40.73454,
                longitude = -74.16375,
            ),
        ),
        Station(
            station = "HARRISON",
            name = "Harrison",
            coordinates = Coordinates(
                latitude = 40.73942,
                longitude = -74.15587,
            ),
        ),
        Station(
            station = "JOURNAL_SQUARE",
            name = "Journal Square",
            coordinates = Coordinates(
                latitude = 40.73301,
                longitude = -74.06289,
            ),
        ),
        Station(
            station = "GROVE_STREET",
            name = "Grove Street",
            coordinates = Coordinates(
                latitude = 40.71966,
                longitude = -74.04245,
            ),
        ),
        Station(
            station = "EXCHANGE_PLACE",
            name = "Exchange Place",
            coordinates = Coordinates(
                latitude = 40.71676,
                longitude = -74.03238,
            ),
        ),
        Station(
            station = "WORLD_TRADE_CENTER",
            name = "World Trade Center",
            coordinates = Coordinates(
                latitude = 40.71271,
                longitude = -74.01193,
            ),
        ),
        Station(
            station = "NEWPORT",
            name = "Newport",
            coordinates = Coordinates(
                latitude = 40.72699,
                longitude = -74.03383,
            ),
        ),
        Station(
            station = "HOBOKEN",
            name = "Hoboken",
            coordinates = Coordinates(
                latitude = 40.73586,
                longitude = -74.02922,
            ),
        ),
        Station(
            station = "CHRISTOPHER_STREET",
            name = "Christopher Street",
            coordinates = Coordinates(
                latitude = 40.73295,
                longitude = -74.00707,
            ),
        ),
        Station(
            station = "NINTH_STREET",
            name = "9th Street",
            coordinates = Coordinates(
                latitude = 40.73424,
                longitude = -73.9991,
            ),
        ),
        Station(
            station = "FOURTEENTH_STREET",
            name = "14th Street",
            coordinates = Coordinates(
                latitude = 40.73735,
                longitude = -73.99684,
            ),
        ),
        Station(
            station = "TWENTY_THIRD_STREET",
            name = "23rd Street",
            coordinates = Coordinates(
                latitude = 40.7429,
                longitude = -73.99278,
            ),
        ),
    ),
)
