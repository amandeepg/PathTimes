package ca.amandeep.path.data

import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.util.tickFlow
import com.github.ajalt.timberkt.d
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

    /**
     * Get the list of arrivals for a station, periodically polling the API for updates.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val arrivals: Flow<ArrivalsResult>
        get() =
            // Merge tick flow to periodically poll the API, and the refresh flow to force a refresh
            merge(tickFlow(arrivalsUpdateInterval), refreshFlow).map {
                pathRemoteDataSource.getArrivals()
                    .let {
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

    /**
     * Refreshes the data from the remote data source.
     */
    suspend fun refresh() = refreshFlow.emit(Unit)

    data class ArrivalsResult(
        val metadata: Metadata = Metadata(),
        val arrivals: ImmutableMap<StationName, ImmutableList<UpcomingTrain>> = persistentMapOf(),
    )

    data class AlertsResult(
        val metadata: Metadata = Metadata(),
        val alerts: AlertDatas = AlertDatas(),
    )

    data class Metadata(
        val lastUpdated: Long = -1,
    )
}
