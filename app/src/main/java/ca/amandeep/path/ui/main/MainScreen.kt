@file:OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalTime::class,
)

package ca.amandeep.path.ui.main

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.amandeep.path.R
import ca.amandeep.path.ui.ErrorBar
import ca.amandeep.path.ui.ErrorScreen
import ca.amandeep.path.ui.KeepUpdatedEffect
import ca.amandeep.path.ui.LastUpdatedInfoRow
import ca.amandeep.path.ui.alerts.ExpandableAlerts
import ca.amandeep.path.ui.rememberLastUpdatedState
import ca.amandeep.path.ui.requireOptionalLocationItem
import ca.amandeep.path.ui.stations.DirectionWarning
import ca.amandeep.path.ui.stations.Station
import ca.amandeep.path.util.ConnectionState
import ca.amandeep.path.util.checkPermission
import ca.amandeep.path.util.observeConnectivity
import dev.burnoo.compose.rememberpreference.rememberBooleanPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            val elapsedMillis = measureTimeMillis { mainViewModel.refreshTrainsFromNetwork() }
            delay((500 - elapsedMillis).milliseconds)
            refreshing = false
        }
    }
    val forceRefresh = { refreshing = true }

    var anyLocationPermissionsGranted by remember {
        mutableStateOf(
            context.checkPermission(ACCESS_COARSE_LOCATION) ||
                    context.checkPermission(ACCESS_FINE_LOCATION),
        )
    }

    val (shortenNamesPref, setShortenNamesPref) = rememberBooleanPreference(
        keyName = "shortenNames",
        initialValue = false,
        defaultValue = false,
    )
    val (showOppositeDirectionPref, setShowOppositeDirectionPref) = rememberBooleanPreference(
        keyName = "showOppositeDirection",
        initialValue = true,
        defaultValue = true,
    )
    val showOppositeDirection by remember(
        showOppositeDirectionPref,
        anyLocationPermissionsGranted,
    ) {
        derivedStateOf { showOppositeDirectionPref || !anyLocationPermissionsGranted }
    }

    val snackbarState = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                actions = {
                    OverflowItems(
                        forceRefresh = forceRefresh,
                        shortenNamesPref = shortenNamesPref,
                        setShortenNamesPref = setShortenNamesPref,
                        showOppositeDirectionPref = showOppositeDirectionPref,
                        setShowOppositeDirectionPref = setShowOppositeDirectionPref,
                        anyLocationPermissionsGranted = anyLocationPermissionsGranted,
                    )
                },
            )
        },
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

        var now by remember { mutableStateOf(System.currentTimeMillis()) }

        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(5.seconds)
            }
        }

        Box(
            Modifier
                .padding(innerPadding)
                .pullRefresh(ptrState),
        ) {
            MainScreenContent(
                uiModel = uiState,
                now = now,
                userState = UserState(
                    shortenNames = shortenNamesPref,
                    showOppositeDirection = showOppositeDirection,
                    isInNJ = isInNJ,
                ),
                forceUpdate = forceRefresh,
                locationPermissionsUpdated = {
                    anyLocationPermissionsGranted = it.isNotEmpty()
                    mainViewModel.locationPermissionsUpdated(it)
                },
                snackbarState = snackbarState,
                setShowingOppositeDirection = setShowOppositeDirectionPref,
                anyLocationPermissionsGranted = anyLocationPermissionsGranted,
            )
            PullRefreshIndicator(
                refreshing = refreshing,
                state = ptrState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun setAndComputeLastGoodState(
    uiStateFlow: Flow<MainUiModel>,
    forceUpdate: () -> Unit,
): MainUiModel {
    val uiModel by uiStateFlow.collectAsStateWithLifecycle(initialValue = MainUiModel())

    val (lastGoodState, setLastGoodState) = remember { mutableStateOf(MainUiModel()) }

    setLastGoodState(
        MainUiModel(
            arrivals = foldWithLastGoodState(uiModel, lastGoodState) { it.arrivals },
            alerts = foldWithLastGoodState(uiModel, lastGoodState) { it.alerts },
        ),
    )

    // If all trains are empty, force a refresh, and show a loading screen
    val allTrainsEmpty = lastGoodState.arrivals is Result.Valid &&
            lastGoodState.arrivals.data.all { it.second.all { it.isDepartedTrain } }
    LaunchedEffect(allTrainsEmpty) {
        if (allTrainsEmpty) {
            forceUpdate()
            setLastGoodState(
                MainUiModel(alerts = lastGoodState.alerts),
            )
        }
    }

    return lastGoodState
}

private fun <T : Any> foldWithLastGoodState(
    currentState: MainUiModel,
    lastGoodState: MainUiModel,
    attribute: (MainUiModel) -> Result<T>,
): Result<T> {
    val lastGoodStateAttribute = attribute(lastGoodState)
    val currentStateAttribute = attribute(currentState)
    return if (currentStateAttribute is Result.Error<T> && lastGoodStateAttribute is Result.Valid<T>) {
        lastGoodStateAttribute.copy(hasError = true)
    } else {
        currentStateAttribute
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
private fun RowScope.OverflowItems(
    forceRefresh: () -> Unit,
    shortenNamesPref: Boolean,
    setShortenNamesPref: (Boolean) -> Unit,
    showOppositeDirectionPref: Boolean,
    setShowOppositeDirectionPref: (Boolean) -> Unit,
    anyLocationPermissionsGranted: Boolean,
) {
    IconButton(onClick = forceRefresh) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.refresh_action),
        )
    }

    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more_item_actions),
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable {
                setShortenNamesPref(!shortenNamesPref)
            },
        ) {
            Checkbox(
                checked = shortenNamesPref,
                onCheckedChange = setShortenNamesPref,
            )
            Text(
                text = stringResource(R.string.shorten_names_action_text),
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        if (anyLocationPermissionsGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    setShowOppositeDirectionPref(!showOppositeDirectionPref)
                },
            ) {
                Checkbox(
                    checked = showOppositeDirectionPref,
                    onCheckedChange = setShowOppositeDirectionPref,
                )
                Text(
                    text = stringResource(R.string.show_opposite_direction_action_text),
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
private fun MainScreenContent(
    uiModel: MainUiModel,
    now: Long,
    userState: UserState,
    forceUpdate: () -> Unit,
    locationPermissionsUpdated: suspend (List<String>) -> Unit,
    snackbarState: SnackbarHostState,
    anyLocationPermissionsGranted: Boolean,
    setShowingOppositeDirection: (Boolean) -> Unit,
) {
    val connectivityState by LocalContext.current.observeConnectivity()
        .collectAsStateWithLifecycle(initialValue = ConnectionState.Available)

    if (uiModel.arrivals is Result.Error || uiModel.alerts is Result.Error) {
        ErrorScreen(
            connectivityState = connectivityState,
            forceUpdate = forceUpdate,
        )
    } else {
        Crossfade(
            targetState = uiModel.arrivals is Result.Loading,
            label = "loading crossfade",
        ) { isLoading ->
            when (isLoading) {
                true -> LoadingScreen()
                false -> {
                    uiModel.arrivals as Result.Valid

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
                    val lastUpdatedState = rememberLastUpdatedState(uiModel.arrivals.lastUpdated)
                    val (alertsExpanded, setAlertsExpanded) = remember { mutableStateOf(false) }
                    LazyColumn {
                        item { requireOptionalLocationItem() }
                        item {
                            ExpandableAlerts(
                                connectivityState = connectivityState,
                                alertsResult = uiModel.alerts,
                                expanded = alertsExpanded,
                                setExpanded = setAlertsExpanded,
                            )
                        }
                        item {
                            if (anyLocationPermissionsGranted)
                                DirectionWarning(
                                    isInNJ = userState.isInNJ,
                                    showOppositeDirection = userState.showOppositeDirection,
                                    setShowingOppositeDirection = setShowingOppositeDirection,
                                    snackbarState = snackbarState,
                                )
                        }
                        item {
                            AnimatedVisibility(
                                visible = uiModel.arrivals.hasError,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                ErrorBar(connectivityState = connectivityState)
                            }
                        }
                        items(uiModel.arrivals.data) {
                            Station(
                                station = it,
                                now = now,
                                userState = userState,
                            )
                        }
                        item {
                            lastUpdatedState.KeepUpdatedEffect(uiModel.arrivals.lastUpdated, 1.seconds)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LastUpdatedInfoRow(lastUpdatedState.value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(10.dp))
        Text(text = stringResource(R.string.loading), color = MaterialTheme.colorScheme.secondary)
    }
}

data class UserState(
    val shortenNames: Boolean,
    val showOppositeDirection: Boolean,
    val isInNJ: Boolean,
)
