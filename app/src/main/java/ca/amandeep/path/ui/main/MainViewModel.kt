package ca.amandeep.path.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import ca.amandeep.path.Station
import ca.amandeep.path.UpcomingTrain
import ca.amandeep.path.data.LocationUseCase
import ca.amandeep.path.data.MainUseCase
import ca.amandeep.path.data.PathApiService
import ca.amandeep.path.data.PathRemoteDataSource
import ca.amandeep.path.data.PathRepository
import ca.amandeep.path.util.isInNJ
import com.github.ajalt.timberkt.d
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration.Companion.seconds

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationUseCase = LocationUseCase(application)
    private val mainUseCase = MainUseCase(
        locationUseCase = locationUseCase,
        pathRepository = PathRepository(
            pathRemoteDataSource = PathRemoteDataSource(
                pathApi = PathApiService.INSTANCE,
                ioDispatcher = Dispatchers.IO,
            )
        )
    )

    val uiState: Flow<UiModel> = mainUseCase
        .getArrivals(
            updateInterval = 30.seconds,
            uiUpdateInterval = 5.seconds,
        )
        // Convert the result to a UI model, assuming that no trains is an error which it probably is
        .map {
            if (it.arrivals.isEmpty()) {
                UiModel.Error
            } else {
                UiModel.Valid(it.metadata.lastUpdated, it.arrivals, hasError = false)
            }
        }
        // Retry all errors with a 1 second delay
        .retryWhen { cause, attempt ->
            emit(UiModel.Error)
            delay(1.seconds)
            d { "Retrying after error: $cause (attempt $attempt)" }
            true
        }
    val isInNJ: Flow<Boolean> = locationUseCase.getCoordinates().map { it.isInNJ }

    suspend fun refreshTrainsFromNetwork() {
        mainUseCase.refresh()
    }

    suspend fun locationPermissionsUpdated(currentPermissions: List<String>) {
        locationUseCase.permissionsUpdated(currentPermissions)
    }

    sealed interface UiModel {
        data class Valid(
            val lastUpdated: Long,
            val stations: List<Pair<Station, List<UiUpcomingTrain>>>,
            val hasError: Boolean,
        ) : UiModel

        object Error : UiModel
        object Loading : UiModel
    }
}

data class UiUpcomingTrain(
    val upcomingTrain: UpcomingTrain,
    val arrivalInMinutesFromNow: Int,
    val isDepartedTrain: Boolean = false,
    val isInOppositeDirection: Boolean = false,
)
