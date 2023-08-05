package ca.amandeep.path.ui.main

import androidx.compose.runtime.Immutable
import ca.amandeep.path.data.PathRepository
import ca.amandeep.path.data.model.AlertData
import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.Coordinates
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.Station
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.data.model.relativeArrivalMins
import ca.amandeep.path.util.isInNJ
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt

@Immutable
sealed interface Result<T : Any> {
    @Immutable
    data class Valid<T : Any>(
        val lastUpdated: Long,
        val data: T,
        val hasError: Boolean = false,
    ) : Result<T>

    @Immutable
    class Error<T : Any> : Result<T>

    @Immutable
    class Loading<T : Any> : Result<T>
}

data class MainUiModel(
    val arrivals: Result<ArrivalsUiModel> = Result.Loading(),
    val alerts: Result<AlertsUiModel> = Result.Loading(),
)

typealias ArrivalsUiModel = ImmutableList<Pair<Station, ImmutableList<UiUpcomingTrain>>>
typealias AlertsUiModel = AlertDatas

data class UiUpcomingTrain(
    val upcomingTrain: UpcomingTrain,
    val arrivalInMinutesFromNow: Int,
    val isDepartedTrain: Boolean = false,
    val isInOppositeDirection: Boolean = false,
    val alerts: ImmutableList<AlertData.Grouped> = persistentListOf(),
)

fun Iterable<UpcomingTrain>.toUiTrains(
    currentLocation: Coordinates,
    now: Long,
    alertsResult: PathRepository.AlertsResult,
): ImmutableList<UiUpcomingTrain> = this
    .map { it.toUiTrain(currentLocation, now, alertsResult) }
    .toImmutableList()

fun UpcomingTrain.toUiTrain(
    currentLocation: Coordinates,
    now: Long,
    alertsResult: PathRepository.AlertsResult,
): UiUpcomingTrain {
    val minsFromNow = relativeArrivalMins(now).roundToInt()
    return UiUpcomingTrain(
        upcomingTrain = this,
        arrivalInMinutesFromNow = minsFromNow,
        isDepartedTrain = minsFromNow < 0,
        isInOppositeDirection = when (direction) {
            Direction.TO_NJ -> !currentLocation.isInNJ
            Direction.TO_NY -> currentLocation.isInNJ
        },
        alerts = alertsResult.alerts.alerts
            .filterIsInstance<AlertData.Grouped>()
            .filter { it.title is AlertData.Grouped.Title.RouteTitle }
            .filter { route in (it.title as AlertData.Grouped.Title.RouteTitle).routes }
            .toImmutableList()
    )
}

/**
 * Sort the trains so that direction is grouped together, and within each direction, sort by arrival time
 */
fun Iterable<UiUpcomingTrain>.sortedByDirectionAndTime(
    currentLocation: Coordinates,
) = sortedWith(
    compareBy(
        { it.directionFromCurrentLocation(currentLocation) },
        { it.arrivalInMinutesFromNow },
    ),
)

// Return -1 if the train is going in the opposite direction, 1 otherwise
fun UiUpcomingTrain.directionFromCurrentLocation(coords: Coordinates): Int =
    when (upcomingTrain.direction) {
        Direction.TO_NJ -> if (coords.isInNJ) -1 else 1
        Direction.TO_NY -> if (coords.isInNJ) 1 else -1
    }
