package ca.amandeep.path.ui.main

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.amandeep.path.Coordinates
import ca.amandeep.path.Direction
import ca.amandeep.path.PathApiService
import ca.amandeep.path.SortPlaces
import ca.amandeep.path.Station
import ca.amandeep.path.Stations
import ca.amandeep.path.UpcomingTrain
import ca.amandeep.path.isInNJ
import ca.amandeep.path.relativeArrivalMins
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt


class MainViewModel : ViewModel() {
    val uiState: MutableState<UiModel> = mutableStateOf(UiModel.Loading)

    private var currentLocation = Coordinates(0.0, 0.0)
    val isInNJ: MutableState<Boolean> = mutableStateOf(false)

    private var stationsFromNetwork: Stations? = null
    private var stationsByDistance: List<Station>? = null

    private var trainsFromNetwork: Map<Station, List<UpcomingTrain>>? = null
    private var trainsSorted: Map<Station, List<UiUpcomingTrain>>? = null

    fun refreshTrainsFromNetwork(): Job = viewModelScope.launch(Dispatchers.IO) {
        trainsFromNetwork = null
        loadAll()
    }

    fun refreshTrainsTimes(): Job = viewModelScope.launch(Dispatchers.IO) {
        trainsSorted = null
        loadAll()
    }

    private suspend fun loadAll() {
        var hasError = false

        stationsFromNetwork = stationsFromNetwork ?: run {
            try {
                getStationsFromNetwork().also {
                    stationsByDistance = null
                    trainsFromNetwork = null
                }
            } catch (e: Exception) {
                hasError = true
                null
            }
        }
        stationsByDistance = stationsByDistance ?: run {
            getStationsByDistance()
        }
        trainsFromNetwork = trainsFromNetwork ?: run {
            try {
                getTrainsFromNetwork()?.also {
                    trainsSorted = null
                }
            } catch (e: Exception) {
                hasError = true
                null
            }
        }
        trainsSorted = trainsSorted ?: run {
            getSortedTrains()
        }

        uiState.value = trainsSorted.let { trainsSorted ->
            stationsByDistance.let { stationsByDistance ->
                if (trainsSorted.isNullOrEmpty() || stationsByDistance.isNullOrEmpty()) {
                    UiModel.Error
                } else {
                    UiModel.Valid(
                        trainsSorted.toList().sortedBy { stationsByDistance.indexOf(it.first) },
                        hasError = hasError
                    )
                }
            }
        }
    }

    private fun getSortedTrains(): Map<Station, List<UiUpcomingTrain>>? {
        val currentTime = Calendar.getInstance().time.time
        return trainsFromNetwork?.mapValues {
            it.value
                .map {
                    val arrivalInMinutesFromNow = it.relativeArrivalMins(currentTime).roundToInt()
                    UiUpcomingTrain(
                        upcomingTrain = it,
                        arrivalInMinutesFromNow = arrivalInMinutesFromNow,
                        pastTrain = arrivalInMinutesFromNow < 0,
                        isInOppositeDirection = when (it.direction) {
                            Direction.TO_NJ -> !isInNJ.value
                            Direction.TO_NY -> isInNJ.value
                        }
                    )
                }
                .sortedWith(
                    compareBy(
                        {
                            when (it.upcomingTrain.direction) {
                                Direction.TO_NJ -> if (isInNJ.value) -1 else 1
                                Direction.TO_NY -> if (isInNJ.value) 1 else -1
                            }
                        },
                        { it.arrivalInMinutesFromNow }
                    )
                )
        }
    }

    private suspend fun getTrainsFromNetwork() = stationsFromNetwork
        ?.stations
        ?.map { it to viewModelScope.async { PathApiService.INSTANCE.getArrivals(it.station) } }
        ?.associate { it.first to it.second.await().upcomingTrains.orEmpty() }

    private fun getStationsByDistance() = stationsFromNetwork
        ?.stations
        ?.sortedWith(SortPlaces(currentLocation))

    private suspend fun getStationsFromNetwork(): Stations = PathApiService.INSTANCE.listStations()

    suspend fun setCurrentLocation(context: Context, newCurrentLocation: Coordinates) {
        if (newCurrentLocation != currentLocation) {
            currentLocation = newCurrentLocation
            isInNJ.value = currentLocation.isInNJ(context)

            stationsByDistance = null
            trainsSorted = null
            viewModelScope.launch(Dispatchers.IO) {
                loadAll()
            }
        }
    }

    sealed interface UiModel {
        data class Valid(
            val stations: List<Pair<Station, List<UiUpcomingTrain>>>,
            val hasError: Boolean,
        ) : UiModel

        object Error : UiModel
        object Loading : UiModel
    }

    data class UiUpcomingTrain(
        val upcomingTrain: UpcomingTrain,
        val arrivalInMinutesFromNow: Int,
        val pastTrain: Boolean = false,
        val isInOppositeDirection: Boolean = false,
    )
}
