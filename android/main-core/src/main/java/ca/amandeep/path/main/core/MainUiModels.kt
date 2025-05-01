package ca.amandeep.path.main.core

import androidx.compose.runtime.Immutable
import ca.amandeep.path.data.AlertData
import ca.amandeep.path.data.AlertDatas
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.UpcomingTrain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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

typealias ArrivalsUiModel = ImmutableList<Pair<UiStation, ImmutableList<UiUpcomingTrain>>>
typealias AlertsUiModel = AlertDatas

data class UiUpcomingTrain(
    val upcomingTrain: UpcomingTrain,
    val direction: Direction,
    val arrivalInMinutesFromNow: Int,
    val isDepartedTrain: Boolean = false,
    val isInOppositeDirection: Boolean = false,
    val showDirectionHelpText: Boolean = false,
    val alerts: ImmutableList<AlertData.Grouped> = persistentListOf(),
    val forceAlertsOpen: Boolean = false,
)

data class UiStation(
    val stationName: StationName,
    val alerts: ImmutableList<AlertData.Grouped> = persistentListOf(),
    val forceAlertsOpen: Boolean = false,
)

data class UserState(
    val shortenNames: Boolean,
    val showOppositeDirection: Boolean,
    val showElevatorAlerts: Boolean,
    val showHelpGuide: Boolean,
    val isInNJ: Boolean,
)
