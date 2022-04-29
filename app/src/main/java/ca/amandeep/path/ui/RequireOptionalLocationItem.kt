@file:OptIn(ExperimentalPermissionsApi::class)

package ca.amandeep.path.ui

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.util.Log
import androidx.annotation.CheckResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ca.amandeep.path.Coordinates
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionsRequired
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import dev.burnoo.compose.rememberpreference.rememberDoublePreference

@Composable
@CheckResult
fun requireOptionalLocationItem(
    navigateToSettingsScreen: (Context) -> Unit,
): @Composable (() -> Unit) {
    val ctx = LocalContext.current

    var doNotShowRationale by rememberSaveable { mutableStateOf(false) }
    var doNotShowSettingsRationale by rememberSaveable { mutableStateOf(false) }

    val coarseLocationState = rememberPermissionState(ACCESS_COARSE_LOCATION)
    val locationStates = rememberMultiplePermissionsState(LOCATION_PERMISSIONS)

    return {
        PermissionsRequired(
            locationStates,
            permissionsNotGrantedContent = {
                if (!doNotShowRationale) {
                    PermissionsUi(
                        rationaleText = if (coarseLocationState.hasPermission)
                            "Precise location is preferred over coarse location to show the closest station"
                        else
                            "Location is used to show you the closest stations and routes",
                        allowButtonText = "Allow",
                        allowButtonAction = { locationStates.launchMultiplePermissionRequest() },
                        denyButtonText = "Deny",
                        denyButtonAction = { doNotShowRationale = true }
                    )
                }
            },
            permissionsNotAvailableContent = {
                if (!doNotShowSettingsRationale) {
                    PermissionsUi(
                        rationaleText = (if (coarseLocationState.hasPermission)
                            "Precise location is preferred over coarse location to show the closest station"
                        else
                            "Location is used to show you the closest stations and routes.") +
                                "\nPlease grant access on the Settings screen",
                        allowButtonText = "Open Settings",
                        allowButtonAction = { navigateToSettingsScreen(ctx) },
                        denyButtonText = "Don't ask again",
                        denyButtonAction = { doNotShowSettingsRationale = true }
                    )
                }
            }
        ) {
        }
    }
}

@Composable
private fun PermissionsUi(
    rationaleText: String,
    allowButtonText: String,
    allowButtonAction: () -> Unit,
    denyButtonText: String,
    denyButtonAction: () -> Unit,
) {
    Column(modifier = Modifier.padding(10.dp)) {
        Text(
            rationaleText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = allowButtonAction
            ) {
                Text(allowButtonText)
            }
            TextButton(
                modifier = Modifier.weight(1f),
                onClick = denyButtonAction
            ) {
                Text(denyButtonText)
            }
        }
    }
}

private const val TAG = "RequireOptionalLocationItem"

@Composable
fun getCurrentLocation(): State<Coordinates> {
    var currentLatitude by rememberDoublePreference(
        keyName = "currentLatitude",
        initialValue = 0.0,
        defaultValue = 0.0
    )
    var currentLongitude by rememberDoublePreference(
        keyName = "currentLongitude",
        initialValue = 0.0,
        defaultValue = 0.0
    )
    UpdateLocation {
        currentLatitude = it.latitude
        currentLongitude = it.longitude
        Log.d(TAG, "get location ${it.latitude}, ${it.longitude}")
    }
    return derivedStateOf { Coordinates(currentLatitude, currentLongitude) }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun UpdateLocation(setCurrentLocation: (Coordinates) -> Unit) {
    fun setLocationMaybe(location: Location?) {
        if (location != null) {
            setCurrentLocation(Coordinates(location.latitude, location.longitude))
        }
    }

    val coarseLocationState = rememberPermissionState(ACCESS_COARSE_LOCATION)
    val fineLocationState = rememberPermissionState(ACCESS_FINE_LOCATION)

    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(
        coarseLocationState.hasPermission,
        fineLocationState.hasPermission
    ) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val locationServices = LocationServices.getFusedLocationProviderClient(ctx)
            locationServices.lastLocation
                .addOnSuccessListener { location: Location? -> setLocationMaybe(location) }
            if (
                checkSelfPermission(ctx, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED ||
                checkSelfPermission(ctx, ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
            ) {
                locationServices.getCurrentLocation(
                    LocationRequest.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { location: Location? -> setLocationMaybe(location) }
            }
        }
    }
}

val LOCATION_PERMISSIONS = listOf(
    ACCESS_COARSE_LOCATION,
    ACCESS_FINE_LOCATION
)
