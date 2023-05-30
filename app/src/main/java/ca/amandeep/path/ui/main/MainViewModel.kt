package ca.amandeep.path.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ca.amandeep.path.data.LocationUseCase
import ca.amandeep.path.data.PathEverbridgeApiService
import ca.amandeep.path.data.PathRazzaApiService
import ca.amandeep.path.data.PathRemoteDataSource
import ca.amandeep.path.data.PathRepository
import ca.amandeep.path.data.model.Coordinates
import ca.amandeep.path.data.model.SortPlaces
import ca.amandeep.path.util.isInNJ
import ca.amandeep.path.util.mapToNotNullPairs
import ca.amandeep.path.util.repeat
import com.github.ajalt.timberkt.d
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationUseCase = LocationUseCase(application)
    private val pathRepository = PathRepository(
        pathRemoteDataSource = PathRemoteDataSource(
            pathApi = PathRazzaApiService.INSTANCE,
            everbridgeApiService = PathEverbridgeApiService.INSTANCE,
            ioDispatcher = Dispatchers.IO,
        ),
        arrivalsUpdateInterval = ARRIVALS_NETWORK_UPDATE_INTERVAL,
        alertsUpdateInterval = ALERTS_NETWORK_UPDATE_INTERVAL,
    )

    val uiState: Flow<MainUiModel> by lazy {
        val currentLocationFlow = locationUseCase.coordinates.onStart {
            // Emit a default location if we don't have one yet
            // Start with the WTC
            emit(DEFAULT_WTC_COORDS)
        }
        val stationsFlow = pathRepository.stations
        val arrivalsFlow = pathRepository
            .arrivals
            .repeat(UI_UPDATE_INTERVAL)
            .onStart { emit(PathRepository.ArrivalsResult()) }
        val alertsFlow = pathRepository.alerts
            .onStart { emit(PathRepository.AlertsResult()) }

        combine(
            currentLocationFlow,
            stationsFlow,
            arrivalsFlow,
            alertsFlow,
        ) { currentLocation, stations, arrivalsResult, alertsResult ->
            val closestStations =
                stations.stations?.sortedWith(SortPlaces(currentLocation)).orEmpty()
            val closestArrivals = closestStations.mapToNotNullPairs {
                it to arrivalsResult.arrivals[it]
                    ?.toUiTrains(
                        currentLocation = currentLocation,
                        now = System.currentTimeMillis(),
                    )
                    ?.sortedByDirectionAndTime(currentLocation)
            }

            val arrivalsUiModel =
                if (arrivalsResult.metadata.lastUpdated < 0)
                    Result.Loading()
                else if (closestArrivals.isEmpty())
                    Result.Error()
                else Result.Valid(
                    lastUpdated = arrivalsResult.metadata.lastUpdated,
                    data = closestArrivals,
                    hasError = false,
                )
            val alertsUiModel =
                if (alertsResult.metadata.lastUpdated < 0)
                    Result.Loading()
                else Result.Valid(
                    lastUpdated = alertsResult.metadata.lastUpdated,
                    data = alertsResult.alerts,
                    hasError = false,
                )

            MainUiModel(
                arrivals = arrivalsUiModel,
                alerts = alertsUiModel,
            )
        }.retryWhen { cause, attempt ->
            // Retry all errors with a 1 second delay
            emit(
                MainUiModel(
                    arrivals = Result.Error(),
                    alerts = Result.Error(),
                ),
            )
            delay(1.seconds)
            d { "Retrying after error: $cause (attempt $attempt)" }
            true
        }
    }
    val isInNJ: Flow<Boolean> by lazy { locationUseCase.coordinates.map { it.isInNJ } }

    suspend fun refreshTrainsFromNetwork() = pathRepository.refresh()

    suspend fun locationPermissionsUpdated(currentPermissions: List<String>) =
        locationUseCase.permissionsUpdated(currentPermissions)

    companion object {
        private val ARRIVALS_NETWORK_UPDATE_INTERVAL = 30.seconds
        private val ALERTS_NETWORK_UPDATE_INTERVAL = 2.minutes
        private val UI_UPDATE_INTERVAL = 5.seconds
        private val DEFAULT_WTC_COORDS = Coordinates(40.713056, -74.013333)
    }
}
