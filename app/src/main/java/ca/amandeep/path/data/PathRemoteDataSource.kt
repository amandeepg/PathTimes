package ca.amandeep.path.data

import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.data.model.UpcomingTrains
import com.github.ajalt.timberkt.Timber.d
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class PathRemoteDataSource(
    private val pathApi: PathApiService,
    private val ioDispatcher: CoroutineDispatcher,
) {

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
