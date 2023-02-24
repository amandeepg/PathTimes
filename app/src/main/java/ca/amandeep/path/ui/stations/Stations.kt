package ca.amandeep.path.ui.stations

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat.startActivity
import ca.amandeep.path.ui.ErrorBar
import ca.amandeep.path.ui.KeepUpdatedEffect
import ca.amandeep.path.ui.LastUpdatedInfoRow
import ca.amandeep.path.ui.main.MainUiModel
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.rememberLastUpdatedState
import ca.amandeep.path.ui.requireOptionalLocationItem
import ca.amandeep.path.util.ConnectionState
import kotlin.time.Duration.Companion.seconds

@Composable
fun Stations(
    uiModel: MainUiModel.Valid,
    locationPermissionsUpdated: suspend (List<String>) -> Unit,
    connectivityState: ConnectionState,
    userState: UserState,
    modifier: Modifier = Modifier,
) {
    val requireOptionalLocationItem = requireOptionalLocationItem(
        permissionsUpdated = locationPermissionsUpdated,
        navigateToSettingsScreen = {
            startActivity(
                it,
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", it.packageName, null),
                ),
                null,
            )
        },
    )

    val lastUpdatedState = rememberLastUpdatedState(uiModel.lastUpdated)

    LazyColumn(modifier = modifier) {
        item {
            lastUpdatedState.KeepUpdatedEffect(uiModel.lastUpdated, 1.seconds)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LastUpdatedInfoRow(lastUpdatedState.value)
            }
        }
        item { requireOptionalLocationItem() }
        item {
            AnimatedVisibility(
                visible = uiModel.hasError,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                ErrorBar(connectivityState)
            }
        }
        items(uiModel.stations) {
            Station(it, userState)
        }
    }
}
