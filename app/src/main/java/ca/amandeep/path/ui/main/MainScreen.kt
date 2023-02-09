@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package ca.amandeep.path.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.amandeep.path.R
import ca.amandeep.path.ui.ErrorScreen
import ca.amandeep.path.ui.stations.Stations
import ca.amandeep.path.util.ConnectionState
import ca.amandeep.path.util.observeConnectivity
import dev.burnoo.compose.rememberpreference.rememberBooleanPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(mainViewModel: MainViewModel) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            val elapsedMillis = measureTimeMillis { mainViewModel.refreshTrainsFromNetwork() }
            delay((500 - elapsedMillis).milliseconds)
            refreshing = false
        }
    }

    var anyLocationPermissionsGranted by remember { mutableStateOf(false) }

    val shortenNamesPref = rememberBooleanPreference(
        keyName = "shortenNames",
        initialValue = false,
        defaultValue = false
    )
    val showOppositeDirectionPref = rememberBooleanPreference(
        keyName = "showOppositeDirection",
        initialValue = true,
        defaultValue = true
    )
    val showOppositeDirection = remember {
        derivedStateOf { showOppositeDirectionPref.value || !anyLocationPermissionsGranted }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                actions = {
                    OverflowItems(
                        setRefreshing = { refreshing = it },
                        shortenNamesPref = shortenNamesPref,
                        showOppositeDirectionPref = showOppositeDirectionPref,
                        anyLocationPermissionsGranted = anyLocationPermissionsGranted,
                    )
                }
            )
        }
    ) { innerPadding ->
        val ptrState = rememberPullRefreshState(
            refreshing = refreshing,
            onRefresh = { refreshing = true },
        )

        val uiModel by mainViewModel.uiState
            .collectAsStateWithLifecycle(initialValue = MainViewModel.UiModel.Loading)
        val isInNJ by mainViewModel.isInNJ.collectAsStateWithLifecycle(initialValue = false)

        // If there's an error, show the last valid state, but with an error flag
        val (lastGoodState, setLastGoodState) = remember {
            mutableStateOf<MainViewModel.UiModel>(MainViewModel.UiModel.Loading)
        }
        setLastGoodState(
            if (uiModel is MainViewModel.UiModel.Error && lastGoodState is MainViewModel.UiModel.Valid)
                lastGoodState.copy(hasError = true)
            else uiModel
        )

        // If all trains are empty, force a refresh, and show a loading screen
        val allTrainsEmpty = lastGoodState is MainViewModel.UiModel.Valid &&
                lastGoodState.stations.all { it.second.all { it.isDepartedTrain } }
        LaunchedEffect(allTrainsEmpty) {
            if (allTrainsEmpty) {
                refreshing = true
                setLastGoodState(MainViewModel.UiModel.Loading)
            }
        }

        Box(Modifier.pullRefresh(ptrState)) {
            MainScreenContent(
                innerPadding = innerPadding,
                uiModel = lastGoodState,
                userState = UserState(
                    shortenNames = shortenNamesPref.value,
                    showOppositeDirection = showOppositeDirection.value,
                    isInNJ = isInNJ
                ),
                forceUpdate = { refreshing = true },
                locationPermissionsUpdated = {
                    anyLocationPermissionsGranted = it.isNotEmpty()
                    mainViewModel.locationPermissionsUpdated(it)
                }
            )
            PullRefreshIndicator(
                refreshing = refreshing,
                state = ptrState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun OverflowItems(
    setRefreshing: (Boolean) -> Unit,
    shortenNamesPref: MutableState<Boolean>,
    showOppositeDirectionPref: MutableState<Boolean>,
    anyLocationPermissionsGranted: Boolean,
) {
    IconButton(
        onClick = { setRefreshing(true) }
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "Refresh"
        )
    }

    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Default.MoreVert,
            contentDescription = "More"
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable {
                shortenNamesPref.value = !shortenNamesPref.value
            }
        ) {
            Checkbox(
                checked = shortenNamesPref.value,
                onCheckedChange = { shortenNamesPref.value = it },
            )
            Text(
                "Shorten station names",
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        if (anyLocationPermissionsGranted)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    showOppositeDirectionPref.value = !showOppositeDirectionPref.value
                }
            ) {
                Checkbox(
                    checked = showOppositeDirectionPref.value,
                    onCheckedChange = { showOppositeDirectionPref.value = it },
                )
                Text(
                    "Show opposite direction",
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
    }
}


@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun MainScreenContent(
    innerPadding: PaddingValues,
    uiModel: MainViewModel.UiModel,
    userState: UserState,
    forceUpdate: () -> Unit,
    locationPermissionsUpdated: suspend (List<String>) -> Unit,
) {
    val connectivityState by LocalContext.current.observeConnectivity()
        .collectAsStateWithLifecycle(initialValue = ConnectionState.Available)

    if (uiModel == MainViewModel.UiModel.Error)
        ErrorScreen(
            connectivityState = connectivityState,
            forceUpdate = forceUpdate
        )
    else
        Crossfade(targetState = uiModel == MainViewModel.UiModel.Loading) { isLoading ->
            when (isLoading) {
                true -> LoadingScreen()
                false -> Stations(
                    uiModel = uiModel as MainViewModel.UiModel.Valid,
                    locationPermissionsUpdated = locationPermissionsUpdated,
                    innerPadding = innerPadding,
                    connectivityState = connectivityState,
                    userState = userState
                )
            }
        }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(10.dp))
        Text("Loading …", color = MaterialTheme.colorScheme.secondary)
    }
}

data class UserState(
    val shortenNames: Boolean,
    val showOppositeDirection: Boolean,
    val isInNJ: Boolean
)