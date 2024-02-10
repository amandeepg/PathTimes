package ca.amandeep.path.data

import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.UpcomingTrain
import com.github.ajalt.timberkt.Timber.d
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class PathRemoteDataSource(
    private val pathRestApi: PathOfficialRestApiService,
    private val alertsApi: PathAlertsApiService,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getAlerts(): AlertDatas =
        withContext(ioDispatcher) {
            d { "getAlerts" }
            alertsApi.getAlerts().alertDatas
        }

    suspend fun getArrivals(): Map<StationName, List<UpcomingTrain>> =
        withContext(ioDispatcher) {
            d { "getArrivals" }
            pathRestApi.getArrivals().stations
                .associate { it.name to it.upcomingTrains.flatMap { it.trains } }
        }
}
