package ca.amandeep.path.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ca.amandeep.path.data.LocationUseCase
import ca.amandeep.path.data.PathAlertsApiService
import ca.amandeep.path.data.PathRazzaApiService
import ca.amandeep.path.data.PathRemoteDataSource
import ca.amandeep.path.data.PathRepository
import ca.amandeep.path.data.model.Coordinates
import ca.amandeep.path.data.model.SortPlaces
import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.util.isInNJ
import ca.amandeep.path.util.mapToNotNullPairs
import ca.amandeep.path.util.repeat
import com.github.ajalt.timberkt.d
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationUseCase = LocationUseCase(application)
    private val pathRepository = PathRepository(
        pathRemoteDataSource = PathRemoteDataSource(
            pathApi = PathRazzaApiService.INSTANCE,
            everbridgeApiService = PathAlertsApiService.INSTANCE,
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
            .map<Stations, Result<Stations>> {
                Result.Valid(
                    lastUpdated = System.currentTimeMillis(),
                    data = it,
                )
            }
            .onStart { emit(Result.Loading()) }
            .retryWhen { cause, attempt ->
                emit(Result.Error())
                delay((attempt  * attempt).seconds + 1.seconds)
                d { "Retrying Stations chain after error: $cause (attempt $attempt)" }
                true
            }
        val arrivalsFlow = pathRepository
            .arrivals
            .repeat(UI_UPDATE_INTERVAL)
            .map<PathRepository.ArrivalsResult, Result<PathRepository.ArrivalsResult>> {
                Result.Valid(
                    lastUpdated = it.metadata.lastUpdated,
                    data = it,
                )
            }
            .onStart { emit(Result.Loading()) }
            .retryWhen { cause, attempt ->
                emit(Result.Error())
                delay((attempt  * attempt).seconds + 1.seconds)
                d { "Retrying ArrivalsResult chain after error: $cause (attempt $attempt)" }
                true
            }
        val alertsFlow = pathRepository.alerts
            .map<PathRepository.AlertsResult, Result<PathRepository.AlertsResult>> {
                Result.Valid(
                    lastUpdated = it.metadata.lastUpdated,
                    data = it,
                )
            }
            .onStart { emit(Result.Loading()) }
            .retryWhen { cause, attempt ->
                emit(Result.Error())
                delay((attempt  * attempt).seconds + 1.seconds)
                d { "Retrying AlertsResult chain after error: $cause (attempt $attempt)" }
                true
            }

        combine(
            currentLocationFlow,
            stationsFlow,
            arrivalsFlow,
            alertsFlow,
        ) { currentLocation, stations, arrivalsResult, alertsResult ->
            val arrivals = arrivalsResult.asValid()?.data?.arrivals.orEmpty()
            val closestStations = stations.asValid()?.data?.stations
                ?.sortedWith(SortPlaces(currentLocation)).orEmpty()
            val closestArrivals = closestStations.mapToNotNullPairs {
                it to arrivals[it]
                    ?.toUiTrains(
                        currentLocation = currentLocation,
                        now = System.currentTimeMillis(),
                        alerts = alertsResult.asValid()?.data?.alerts?.alerts ?: persistentListOf(),
                    )
                    ?.sortedByDirectionAndTime(currentLocation)
                    ?.toImmutableList()
            }.toImmutableList()

            val arrivalsUiModel =
                when (arrivalsResult) {
                    is Result.Valid -> when {
                        arrivalsResult.lastUpdated < 0 -> Result.Loading()
                        closestArrivals.isEmpty() -> Result.Error()
                        else -> Result.Valid(
                            lastUpdated = arrivalsResult.lastUpdated,
                            data = closestArrivals,
                        )
                    }
                    is Result.Error -> Result.Error()
                    is Result.Loading -> Result.Loading()
                }
            val alertsUiModel =
                when (alertsResult) {
                    is Result.Valid -> when {
                        alertsResult.lastUpdated < 0 -> Result.Loading()
                        alertsResult.data.alerts.hasError -> Result.Error()
                        else -> Result.Valid(
                            lastUpdated = alertsResult.lastUpdated,
                            data = alertsResult.data.alerts,
                        )
                    }
                    is Result.Error -> Result.Error()
                    is Result.Loading -> Result.Loading()
                }
            MainUiModel(
                arrivals = arrivalsUiModel,
                alerts = alertsUiModel,
            )
        }.retryWhen { cause, attempt ->
            emit(
                MainUiModel(
                    arrivals = Result.Error(),
                    alerts = Result.Error(),
                ),
            )
            delay((attempt  * attempt).seconds + 1.seconds)
            d { "Retrying entire chain after error: $cause (attempt $attempt)" }
            true
        }
    }
    val isInNJ: Flow<Boolean> by lazy { locationUseCase.coordinates.map { it.isInNJ } }

    suspend fun refreshTrainsFromNetwork() = pathRepository.refresh()

    suspend fun locationPermissionsUpdated(currentPermissions: ImmutableList<String>) =
        locationUseCase.permissionsUpdated(currentPermissions)

    companion object {
        private val ARRIVALS_NETWORK_UPDATE_INTERVAL = 30.seconds
        private val ALERTS_NETWORK_UPDATE_INTERVAL = 2.minutes
        private val UI_UPDATE_INTERVAL = 5.seconds
        private val DEFAULT_WTC_COORDS = Coordinates(40.713056, -74.013333)
    }
}
