package ca.amandeep.path.data

import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.data.model.UpcomingTrains
import com.github.ajalt.timberkt.Timber.d
import com.github.ajalt.timberkt.Timber.w
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import okhttp3.internal.immutableListOf
import path_api.v1.Direction
import path_api.v1.GetUpcomingTrainsRequest
import path_api.v1.GetUpcomingTrainsResponse
import path_api.v1.Route
import path_api.v1.Station
import path_api.v1.StationsClient
import java.util.Date
import kotlin.time.Duration.Companion.days
import ca.amandeep.path.data.model.Direction as ModelDirection
import ca.amandeep.path.data.model.Route as ModelRoute

class PathRemoteDataSource(
    private val pathGrpcApi: StationsClient,
    private val pathRestApi: PathRazzaRestApiService,
    private val alertsApi: PathAlertsApiService,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getAlerts(): AlertDatas =
        withContext(ioDispatcher) {
            d { "getAlerts" }
            alertsApi.getAlerts().alertDatas
        }

    suspend fun getStations(): Stations =
        withContext(ioDispatcher) {
            d { "getStations" }
            pathRestApi.getStations()
        }

    suspend fun getArrivals(station: String): UpcomingTrains =
        withContext(ioDispatcher) {
            d { "getArrivals: $station" }

            amb(
                async {
                    try {
                        getArrivalsFromGrpcApi(station).also { d { "getArrivals($station) from gRPC" } }
                    } catch (e: Exception) {
                        w(e) { "getArrivals($station) from gRPC failed" }
                        delay(1000.days)
                        UpcomingTrains(immutableListOf())
                    }
                },
                async {
                    try {
                        pathRestApi.getArrivals(station).also { d { "getArrivals($station) from REST" } }
                    } catch (e: Exception) {
                        w(e) { "getArrivals($station) from REST failed" }
                        delay(1000.days)
                        UpcomingTrains(immutableListOf())
                    }
                },
            )
        }

    private suspend fun getArrivalsFromGrpcApi(station: String) = UpcomingTrains(
        pathGrpcApi.GetUpcomingTrains()
            .execute(GetUpcomingTrainsRequest(station.toProtoStation())).upcoming_trains
            .mapNotNull { it.toUpcomingTrain() }
    )
}

suspend fun <T> amb(vararg jobs: Deferred<T>): T = select {
    fun cancelAll() = jobs.forEach { it.cancel() }

    for (deferred in jobs) {
        deferred.onAwait {
            cancelAll()
            it
        }
    }
}

private fun GetUpcomingTrainsResponse.UpcomingTrain.toUpcomingTrain(): UpcomingTrain? {
    if (route == Route.ROUTE_UNSPECIFIED || route == Route.NPT_HOB || route == Route.npt_hob) {
        d { "Invalid route $route" }
        return null
    }
    if (direction == Direction.DIRECTION_UNSPECIFIED) {
        d { "Invalid direction $direction" }
        return null
    }
    return UpcomingTrain(
        route = route.toModelRoute(),
        direction = direction.toModelDirection(),
        projectedArrival = Date.from(projected_arrival),
        lineColors = line_colors,
    )
}

private fun String.toProtoStation(): Station = when (this) {
    "NEWARK" -> Station.NEWARK
    "HARRISON" -> Station.HARRISON
    "JOURNAL_SQUARE" -> Station.JOURNAL_SQUARE
    "GROVE_STREET" -> Station.GROVE_STREET
    "EXCHANGE_PLACE" -> Station.EXCHANGE_PLACE
    "WORLD_TRADE_CENTER" -> Station.WORLD_TRADE_CENTER
    "NEWPORT" -> Station.NEWPORT
    "HOBOKEN" -> Station.HOBOKEN
    "CHRISTOPHER_STREET" -> Station.CHRISTOPHER_STREET
    "NINTH_STREET" -> Station.NINTH_STREET
    "FOURTEENTH_STREET" -> Station.FOURTEENTH_STREET
    "TWENTY_THIRD_STREET" -> Station.TWENTY_THIRD_STREET
    "THIRTY_THIRD_STREET" -> Station.THIRTY_THIRD_STREET
    else -> Station.STATION_UNSPECIFIED
}

private fun Direction.toModelDirection(): ModelDirection = when (this) {
    Direction.TO_NJ, Direction.to_nj -> ModelDirection.TO_NJ
    Direction.TO_NY, Direction.to_ny -> ModelDirection.TO_NY
    else -> ModelDirection.TO_NJ
}

private fun Route.toModelRoute(): ModelRoute = when (this) {
    Route.JSQ_33_HOB, Route.jsq_33_hob -> ModelRoute.JSQ_33_HOB
    Route.JSQ_33, Route.jsq_33 -> ModelRoute.JSQ_33
    Route.HOB_33, Route.hob_33 -> ModelRoute.HOB_33
    Route.HOB_WTC, Route.hob_wtc -> ModelRoute.HOB_WTC
    Route.NWK_WTC, Route.nwk_wtc -> ModelRoute.NWK_WTC
    else -> ModelRoute.JSQ_33_HOB
}
