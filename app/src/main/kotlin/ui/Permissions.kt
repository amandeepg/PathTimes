package ca.amandeep.path.ui

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.CheckResult
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.amandeep.path.R
import ca.amandeep.path.ui.theme.PATHTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionsRequired
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A composable that will show a [PermissionsRequired] composable if the user has not granted
 * the location permissions.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
@CheckResult
fun requireOptionalLocationItem(
    navigateToSettingsScreen: (Context) -> Unit,
    permissionsUpdated: suspend (ImmutableList<String>) -> Unit,
): @Composable ((Modifier) -> Unit) {
    val ctx = LocalContext.current

    var doNotShowRationale by rememberSaveable { mutableStateOf(false) }
    var doNotShowSettingsRationale by rememberSaveable { mutableStateOf(false) }

    val coarseLocationState = rememberPermissionState(ACCESS_COARSE_LOCATION)
    val locationStates = rememberMultiplePermissionsState(
        listOf(
            ACCESS_COARSE_LOCATION,
            ACCESS_FINE_LOCATION,
        ),
    )

    val permissionsGranted =
        locationStates.permissions
            .filter { it.hasPermission }
            .map { it.permission }
            .toImmutableList()
    LaunchedEffect(permissionsGranted, permissionsUpdated) {
        permissionsUpdated(permissionsGranted)
    }

    return { modifier ->
        // TODO Upgrade to the new accompanist permissions API
        PermissionsRequired(
            locationStates,
            permissionsNotGrantedContent = {
                if (!doNotShowRationale) {
                    PermissionsUi(
                        modifier = modifier,
                        rationaleText = if (coarseLocationState.hasPermission) {
                            stringResource(R.string.precise_location_rationale)
                        } else {
                            stringResource(R.string.location_rationale)
                        },
                        allowButtonText = stringResource(R.string.allow),
                        allowButtonAction = { locationStates.launchMultiplePermissionRequest() },
                        denyButtonText = stringResource(R.string.deny),
                        denyButtonAction = { doNotShowRationale = true },
                    )
                }
            },
            permissionsNotAvailableContent = {
                if (!doNotShowSettingsRationale) {
                    PermissionsUi(
                        modifier = modifier,
                        rationaleText = (
                            if (coarseLocationState.hasPermission) {
                                stringResource(R.string.precise_location_rationale)
                            } else {
                                stringResource(R.string.location_rationale)
                            }
                        ) +
                            "\n" + stringResource(R.string.grant_settings_access),
                        allowButtonText = stringResource(R.string.open_settings),
                        allowButtonAction = { navigateToSettingsScreen(ctx) },
                        denyButtonText = stringResource(R.string.dont_ask_again),
                        denyButtonAction = { doNotShowSettingsRationale = true },
                    )
                }
            },
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
    modifier: Modifier = Modifier,
    denyButtonAction: () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            rationaleText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FilledTonalButton(onClick = allowButtonAction) {
                Text(allowButtonText)
            }
            TextButton(onClick = denyButtonAction) {
                Text(denyButtonText)
            }
        }
    }
}

@Composable
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun PermissionsUiPreview() {
    PATHTheme {
        PermissionsUi(
            rationaleText = "Location is used to show you the closest stations and routes",
            allowButtonText = "Allow",
            allowButtonAction = {},
            denyButtonText = "Deny",
        ) {
        }
    }
}

@Composable
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun PermissionsUi2Preview() {
    PATHTheme {
        PermissionsUi(
            rationaleText = "Precise location is preferred over coarse location to show the closest station.\n" +
                "Please grant access on the Settings screen",
            allowButtonText = "Open Settings",
            allowButtonAction = {},
            denyButtonText = "Don't ask again",
        ) {
        }
    }
}
