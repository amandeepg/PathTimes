@file:OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalTime::class
)

package ca.amandeep.path.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.flow.Flow
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

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
    val forceRefresh = { refreshing = true }

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
                        forceRefresh = forceRefresh,
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
            onRefresh = forceRefresh,
        )

        val isInNJ by mainViewModel.isInNJ.collectAsStateWithLifecycle(initialValue = false)

        // If there's an error, show the last valid state, but with an error flag
        val uiState = setAndComputeLastGoodState(
            uiStateFlow = mainViewModel.uiState,
            forceUpdate = forceRefresh,
        )

        Box(
            Modifier
                .padding(innerPadding)
                .pullRefresh(ptrState)
        ) {
            MainScreenContent(
                uiModel = uiState,
                userState = UserState(
                    shortenNames = shortenNamesPref.value,
                    showOppositeDirection = showOppositeDirection.value,
                    isInNJ = isInNJ
                ),
                forceUpdate = forceRefresh,
                locationPermissionsUpdated = {
                    anyLocationPermissionsGranted = it.isNotEmpty()
                    mainViewModel.locationPermissionsUpdated(it)
                },
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
private fun setAndComputeLastGoodState(
    uiStateFlow: Flow<MainUiModel>,
    forceUpdate: () -> Unit
): MainUiModel {
    val uiModel by uiStateFlow
        .collectAsStateWithLifecycle(initialValue = MainUiModel.Loading)

    val (lastGoodState, setLastGoodState) = remember {
        mutableStateOf<MainUiModel>(MainUiModel.Loading)
    }

    setLastGoodState(
        if (uiModel is MainUiModel.Error && lastGoodState is MainUiModel.Valid)
            lastGoodState.copy(hasError = true)
        else uiModel
    )

    // If all trains are empty, force a refresh, and show a loading screen
    val allTrainsEmpty = lastGoodState is MainUiModel.Valid &&
        lastGoodState.stations.all { it.second.all { it.isDepartedTrain } }
    LaunchedEffect(allTrainsEmpty) {
        if (allTrainsEmpty) {
            forceUpdate()
            setLastGoodState(MainUiModel.Loading)
        }
    }

    return lastGoodState
}

@Composable
private fun OverflowItems(
    forceRefresh: () -> Unit,
    shortenNamesPref: MutableState<Boolean>,
    showOppositeDirectionPref: MutableState<Boolean>,
    anyLocationPermissionsGranted: Boolean,
) {
    IconButton(onClick = forceRefresh) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Refresh"
        )
    }

    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
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
                text = "Shorten station names",
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
                    text = "Show opposite direction",
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun MainScreenContent(
    uiModel: MainUiModel,
    userState: UserState,
    forceUpdate: () -> Unit,
    locationPermissionsUpdated: suspend (List<String>) -> Unit,
) {
    val connectivityState by LocalContext.current.observeConnectivity()
        .collectAsStateWithLifecycle(initialValue = ConnectionState.Available)

    if (uiModel == MainUiModel.Error)
        ErrorScreen(
            connectivityState = connectivityState,
            forceUpdate = forceUpdate
        )
    else
        Crossfade(targetState = uiModel == MainUiModel.Loading) { isLoading ->
            when (isLoading) {
                true -> LoadingScreen()
                false -> Stations(
                    uiModel = uiModel as MainUiModel.Valid,
                    locationPermissionsUpdated = locationPermissionsUpdated,
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
        Text(text = "Loading …", color = MaterialTheme.colorScheme.secondary)
    }
}

data class UserState(
    val shortenNames: Boolean,
    val showOppositeDirection: Boolean,
    val isInNJ: Boolean
)
