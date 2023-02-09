package ca.amandeep.path.data

import ca.amandeep.path.Coordinates
import ca.amandeep.path.Direction
import ca.amandeep.path.SortPlaces
import ca.amandeep.path.Station
import ca.amandeep.path.UpcomingTrain
import ca.amandeep.path.relativeArrivalMins
import ca.amandeep.path.ui.main.UiUpcomingTrain
import ca.amandeep.path.util.isInNJ
import ca.amandeep.path.util.repeat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.*
import kotlin.math.roundToInt
import kotlin.time.Duration

class MainUseCase(
    private val pathRepository: PathRepository,
    private val locationUseCase: LocationUseCase,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getArrivals(
        updateInterval: Duration,
        uiUpdateInterval: Duration
    ): Flow<Result> =
        locationUseCase.getCoordinates()
            .onStart {
                // Emit a default location if we don't have one yet
                // Start with the WTC
                emit(Coordinates(40.713056, -74.013333))
            }
            // Combine the location with the arrivals, so that we can sort the stations by distance
            .flatMapLatest { currentLocation ->
                // Get the list of stations sorted by distance from the current location
                val closestStationsFlow = pathRepository.getStations()
                    .map { it.stations?.sortedWith(SortPlaces(currentLocation)).orEmpty() }

                val arrivalsFlow = pathRepository.getArrivals(updateInterval)
                    .repeat(uiUpdateInterval)
                    .map { result ->
                        Pair(
                            result.metadata,
                            result.arrivals.mapValues { it.toUiTrains(currentLocation) }
                        )
                    }

                closestStationsFlow.combine(arrivalsFlow) { closestStations, (metadata, arrivals) ->
                    Result(
                        metadata = Result.Metadata(metadata.lastUpdated),
                        arrivals = closestStations
                            .map { it to arrivals[it] }
                            .filterNot { it.second == null }
                            .map { it.first to it.second!! }
                    )

                }
            }

    private fun Map.Entry<Station, List<UpcomingTrain>>.toUiTrains(
        currentLocation: Coordinates
    ): List<UiUpcomingTrain> = value
        .map { it.toUiTrain(currentLocation) }
        .sortedWith(
            // Sort the trains so that direction is grouped together
            // And within each direction, sort by arrival time
            compareBy(
                { it.directionFromCurrentLocation(currentLocation) },
                { it.arrivalInMinutesFromNow }
            )
        )

    private fun UpcomingTrain.toUiTrain(
        currentLocation: Coordinates
    ): UiUpcomingTrain {
        val minsFromNow = relativeArrivalMins(System.currentTimeMillis()).roundToInt()
        return UiUpcomingTrain(
            upcomingTrain = this,
            arrivalInMinutesFromNow = minsFromNow,
            isDepartedTrain = minsFromNow < 0,
            isInOppositeDirection = when (direction) {
                Direction.TO_NJ -> !currentLocation.isInNJ
                Direction.TO_NY -> currentLocation.isInNJ
            }
        )
    }

    // Return -1 if the train is going in the opposite direction, 1 otherwise
    private fun UiUpcomingTrain.directionFromCurrentLocation(coords: Coordinates): Int =
        when (upcomingTrain.direction) {
            Direction.TO_NJ -> if (coords.isInNJ) -1 else 1
            Direction.TO_NY -> if (coords.isInNJ) 1 else -1
        }

    /**
     * Refreshes the data from the remote data source.
     */
    suspend fun refresh() =  pathRepository.refresh()

    data class Result(
        val metadata: Metadata,
        val arrivals: List<Pair<Station, List<UiUpcomingTrain>>>
    ) {
        data class Metadata(
            val lastUpdated: Long
        )
    }
}
