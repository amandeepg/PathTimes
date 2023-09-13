package ca.amandeep.path.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ca.amandeep.path.data.LocationUseCase
import ca.amandeep.path.data.PathAlertsApiService
import ca.amandeep.path.data.PathRazzaApiService
import ca.amandeep.path.data.PathRemoteDataSource
import ca.amandeep.path.data.PathRepository
import ca.amandeep.path.data.model.Coordinates
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.SortPlaces
import ca.amandeep.path.data.model.Station
import ca.amandeep.path.data.model.Stations
import ca.amandeep.path.util.isInNJ
import ca.amandeep.path.util.mapToNotNullPairs
import ca.amandeep.path.util.repeat
import com.github.ajalt.timberkt.w
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

interface MainViewModel {
    val uiState: Flow<MainUiModel>
    val isInNJ: Flow<Boolean>

    suspend fun refreshTrainsFromNetwork()

    suspend fun locationPermissionsUpdated(currentPermissions: ImmutableList<String>)

    companion object {
        val ARRIVALS_NETWORK_UPDATE_INTERVAL = 30.seconds
        val ALERTS_NETWORK_UPDATE_INTERVAL = 2.minutes
        val UI_UPDATE_INTERVAL = 5.seconds
        val DEFAULT_WTC_COORDS = Coordinates(40.713056, -74.013333)
    }
}

class MainViewModelImpl(application: Application) : AndroidViewModel(application), MainViewModel {
    private val locationUseCase = LocationUseCase(application)
    private val pathRepository = PathRepository(
        pathRemoteDataSource = PathRemoteDataSource(
            pathApi = PathRazzaApiService.INSTANCE,
            everbridgeApiService = PathAlertsApiService.INSTANCE,
            ioDispatcher = Dispatchers.IO,
        ),
        arrivalsUpdateInterval = MainViewModel.ARRIVALS_NETWORK_UPDATE_INTERVAL,
        alertsUpdateInterval = MainViewModel.ALERTS_NETWORK_UPDATE_INTERVAL,
    )

    override val uiState: Flow<MainUiModel> by lazy {
        val currentLocationFlow = locationUseCase.coordinates.onStart {
            // Emit a default location if we don't have one yet
            // Start with the WTC
            emit(MainViewModel.DEFAULT_WTC_COORDS)
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
                delay((attempt * attempt).seconds + 1.seconds)
                w(cause) { "Retrying Stations chain after error: $cause (attempt $attempt)" }
                true
            }
        val arrivalsFlow = pathRepository
            .arrivals
            .repeat(MainViewModel.UI_UPDATE_INTERVAL)
            .map<PathRepository.ArrivalsResult, Result<PathRepository.ArrivalsResult>> {
                Result.Valid(
                    lastUpdated = it.metadata.lastUpdated,
                    data = it,
                )
            }
            .onStart { emit(Result.Loading()) }
            .retryWhen { cause, attempt ->
                emit(Result.Error())
                delay((attempt * attempt).seconds + 1.seconds)
                w(cause) { "Retrying ArrivalsResult chain after error: $cause (attempt $attempt)" }
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
                delay((attempt * attempt).seconds + 1.seconds)
                w(cause) { "Retrying AlertsResult chain after error: $cause (attempt $attempt)" }
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
            }
                .addHelpText()
                .toImmutableList()

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
            delay((attempt * attempt).seconds + 1.seconds)
            w(cause) { "Retrying entire chain after error: $cause (attempt $attempt)" }
            true
        }
    }
    override val isInNJ: Flow<Boolean> by lazy { locationUseCase.coordinates.map { it.isInNJ } }

    override suspend fun refreshTrainsFromNetwork() = pathRepository.refresh()

    override suspend fun locationPermissionsUpdated(currentPermissions: ImmutableList<String>) =
        locationUseCase.permissionsUpdated(currentPermissions)
}

private fun List<Pair<Station, ImmutableList<UiUpcomingTrain>>>.addHelpText(): List<Pair<Station, ImmutableList<UiUpcomingTrain>>> {
    val allTrains = flatMap { it.second }
    val firstNjTrain = allTrains.firstOrNull { it.upcomingTrain.direction == Direction.TO_NJ }
    val firstNycTrain = allTrains.firstOrNull { it.upcomingTrain.direction == Direction.TO_NY }

    return map {
        it.copy(
            second = it.second.map { train ->
                if (train == firstNjTrain || train == firstNycTrain) {
                    train.copy(showDirectionHelpText = true)
                } else {
                    train
                }
            }.toImmutableList(),
        )
    }
}
