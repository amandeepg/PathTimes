package ca.amandeep.path.data

import ca.amandeep.path.Stations
import ca.amandeep.path.UpcomingTrains
import com.github.ajalt.timberkt.Timber.d
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class PathRemoteDataSource(
    private val pathApi: PathApiService,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun fetchStations(): Stations =
        withContext(ioDispatcher) {
            d { "fetchStations" }
            pathApi.listStations()
        }

    suspend fun fetchArrivals(station: String): UpcomingTrains =
        withContext(ioDispatcher) {
            d { "fetchArrivals: $station" }
            pathApi.getArrivals(station)
        }
}

