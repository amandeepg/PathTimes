package ca.amandeep.path.data

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.content.PermissionChecker.checkSelfPermission
import ca.amandeep.path.data.model.Coordinates
import com.github.ajalt.timberkt.d
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@SuppressLint("MissingPermission")
class LocationUseCase constructor(
    private val context: Context
) {
    private var permissionsUpdatedFlow = MutableStateFlow<List<String>>(emptyList())
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Returns a flow of the user's current location, with no polling.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val location: Flow<Location> by lazy {
        permissionsUpdatedFlow.flatMapLatest {
            callbackFlow {
                if (
                    checkSelfPermission(context, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED ||
                    checkSelfPermission(context, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
                ) {
                    val callback: (Location?) -> Unit = {
                        d { "Location: $it" }
                        it?.let { this.trySend(it) }
                    }
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener(callback)
                    fusedLocationClient.getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener(callback)
                }
                awaitClose()
            }
        }.distinctUntilChanged()
    }

    /**
     * Returns a flow of the user's current coordinates, with no polling.
     */
    val coordinates: Flow<Coordinates> by lazy {
        location.map { Coordinates(it.latitude, it.longitude) }
    }

    suspend fun permissionsUpdated(currentPermissions: List<String>) {
        permissionsUpdatedFlow.emit(currentPermissions)
    }
}
