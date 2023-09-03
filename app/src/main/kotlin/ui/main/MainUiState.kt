package ca.amandeep.path.ui.main

import androidx.compose.runtime.Immutable
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
import java.time.Instant
import java.util.Date
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

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

fun <T : Any> Result<T>.asValid(): Result.Valid<T>? = this as? Result.Valid<T>

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
    val showDirectionHelpText: Boolean = false,
    val alerts: ImmutableList<AlertData.Grouped> = persistentListOf(),
)

fun Iterable<UpcomingTrain>.toUiTrains(
    currentLocation: Coordinates,
    now: Long,
    alerts: ImmutableList<AlertData>,
): ImmutableList<UiUpcomingTrain> = this
    .map { it.toUiTrain(currentLocation, now, alerts) }
    .toImmutableList()

fun UpcomingTrain.toUiTrain(
    currentLocation: Coordinates,
    now: Long,
    alerts: ImmutableList<AlertData>,
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
        alerts = alerts
            .filterIsInstance<AlertData.Grouped>()
            .filter { it.title is AlertData.Grouped.Title.RouteTitle }
            .filter {
                val title = it.title
                if (title is AlertData.Grouped.Title.RouteTitle) {
                    val matchingRoute = route in title.routes
                    if (matchingRoute) {
                        // If the alert is a "resuming" type, i.e. it's been resolved then we
                        // only want to see it if it's within an hour
                        val isResuming = title.text.startsWith("Resuming", ignoreCase = true)
                        if (isResuming) {
                            it.main.date?.lessThanDurationAgo(1.hours) ?: true
                        } else {
                            // If it's not a "resuming" type, i.e. it's ongoing then we want to
                            // see it
                            true
                        }
                    } else {
                        // If it doesn't match the route, it's not for this route
                        false
                    }
                } else {
                    // If it's not a route type, it's not for this route
                    false
                }
            }
            .toImmutableList(),
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

infix fun Date.lessThanDurationAgo(duration: Duration): Boolean {
    val currentDate = Instant.now()
    val timeDurationAgo = currentDate - duration.toJavaDuration()

    // Convert the input Date to Instant for comparison
    val inputInstant = toInstant()

    // Check if the input date falls between the current date and the end time
    return inputInstant.isBefore(currentDate) && inputInstant.isAfter(timeDurationAgo)
}
