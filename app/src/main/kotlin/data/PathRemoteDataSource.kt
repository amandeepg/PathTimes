package ca.amandeep.path.data

import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.data.model.UpcomingTrains
import com.github.ajalt.timberkt.Timber.d
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class PathRemoteDataSource(
    private val pathApi: PathRazzaApiService,
    private val everbridgeApiService: PathAlertsApiService,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getAlerts(): AlertDatas =
        withContext(ioDispatcher) {
            d { "getAlerts" }
            everbridgeApiService.getAlerts().alertDatas
        }

    suspend fun getStations(): Stations =
        withContext(ioDispatcher) {
            d { "getStations" }
            pathApi.getStations()
        }

    suspend fun getArrivals(station: String): UpcomingTrains =
        withContext(ioDispatcher) {
            d { "getArrivals: $station" }
            pathApi.getArrivals(station)
        }
}
