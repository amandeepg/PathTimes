package ca.amandeep.path.ui.stations

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import ca.amandeep.path.ui.main.MainViewModel
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.requireOptionalLocationItem

@Composable
fun StationsAndTrains(
    innerPadding: PaddingValues,
    mainViewModel: MainViewModel,
    userState: UserState,
) {
    val uiModel = mainViewModel.uiState.value

    if (uiModel == MainViewModel.UiModel.Error)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Error occurred while loading stations and trains. Try again.",
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }
    else
        Crossfade(targetState = uiModel == MainViewModel.UiModel.Loading) { isLoading ->
            when (isLoading) {
                true ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text("Loading …", color = MaterialTheme.colorScheme.secondary)
                    }
                false -> {
                    val requireOptionalLocationItem = requireOptionalLocationItem(
                        navigateToSettingsScreen = {
                            startActivity(it, Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", it.packageName, null)
                            ), null)
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        item { requireOptionalLocationItem() }
                        items((uiModel as MainViewModel.UiModel.Valid).stations) {
                            Station(it, userState)
                        }
                    }
                }
            }
        }
}
