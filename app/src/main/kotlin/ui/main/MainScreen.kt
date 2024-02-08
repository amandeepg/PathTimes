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
import ca.amandeep.path.data.model.AlertData
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
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
    val snackbarState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
            context.checkPermission(ACCESS_COARSE_LOCATION) || context.checkPermission(ACCESS_FINE_LOCATION),
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

    val (showElevatorAlertsPref, setShowElevatorAlertsPref) = rememberBooleanPreference(
        keyName = "showElevatorAlerts",
        initialValue = true,
        defaultValue = true,
    )
    val setShowElevatorAlertsWithUndo = run {
        val snackbarMessage = stringResource(R.string.change_dir_in_options)
        val snackbarActionLabel = stringResource(R.string.undo)
        return@run { newValue: Boolean ->
            setShowElevatorAlertsPref(newValue)
            if (!newValue) {
                coroutineScope.launch {
                    val snackbarResult = snackbarState.showSnackbar(
                        message = snackbarMessage,
                        actionLabel = snackbarActionLabel,
                    )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        setShowElevatorAlertsPref(true)
                    }
                }
            }
        }
    }

    val (showHelpGuidePref, setShowHelpGuidePref) = rememberBooleanPreference(
        keyName = "showHelpGuide",
        initialValue = true,
        defaultValue = true,
    )
    val setShowHelpGuideWithUndo = run {
        val snackbarMessage = stringResource(R.string.change_dir_in_options)
        val snackbarActionLabel = stringResource(R.string.undo)
        return@run { newValue: Boolean ->
            setShowHelpGuidePref(newValue)
            if (!newValue) {
                coroutineScope.launch {
                    val snackbarResult = snackbarState.showSnackbar(
                        message = snackbarMessage,
                        actionLabel = snackbarActionLabel,
                    )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        setShowHelpGuidePref(true)
                    }
                }
            }
        }
    }

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
                        showElevatorAlertsPref = showElevatorAlertsPref,
                        showHelpGuidePref = showHelpGuidePref,
                        setShowElevatorAlertsPref = setShowElevatorAlertsPref,
                        setShowHelpGuidePref = setShowHelpGuidePref,
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
                    showElevatorAlerts = showElevatorAlertsPref,
                    showHelpGuide = showHelpGuidePref,
                    isInNJ = isInNJ,
                ),
                forceUpdate = forceRefresh,
                locationPermissionsUpdated = {
                    anyLocationPermissionsGranted = it.isNotEmpty()
                    mainViewModel.locationPermissionsUpdated(it)
                },
                snackbarState = snackbarState,
                setShowingOppositeDirection = setShowOppositeDirectionPref,
                setShowElevatorAlerts = setShowElevatorAlertsWithUndo,
                setShowHelpGuide = setShowHelpGuideWithUndo,
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
    LaunchedEffect(allTrainsEmpty, forceUpdate) {
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
    } else if (currentStateAttribute is Result.Loading<T> && lastGoodStateAttribute is Result.Valid<T>) {
        lastGoodStateAttribute
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
    showElevatorAlertsPref: Boolean,
    showHelpGuidePref: Boolean,
    setShowElevatorAlertsPref: (Boolean) -> Unit,
    setShowHelpGuidePref: (Boolean) -> Unit,
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setShortenNamesPref(!shortenNamesPref) },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setShowOppositeDirectionPref(!showOppositeDirectionPref) },
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setShowElevatorAlertsPref(!showElevatorAlertsPref) },
        ) {
            Checkbox(
                checked = showElevatorAlertsPref,
                onCheckedChange = setShowElevatorAlertsPref,
            )
            Text(
                text = stringResource(R.string.show_elevator_alerts),
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setShowHelpGuidePref(!showHelpGuidePref) },
        ) {
            Checkbox(
                checked = showHelpGuidePref,
                onCheckedChange = setShowHelpGuidePref,
            )
            Text(
                text = stringResource(R.string.show_help_guide),
                modifier = Modifier.padding(end = 10.dp),
            )
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
    locationPermissionsUpdated: suspend (ImmutableList<String>) -> Unit,
    snackbarState: SnackbarHostState,
    anyLocationPermissionsGranted: Boolean,
    setShowingOppositeDirection: (Boolean) -> Unit,
    setShowElevatorAlerts: (Boolean) -> Unit,
    setShowHelpGuide: (Boolean) -> Unit,
) {
    val connectivityState by LocalContext.current.observeConnectivity()
        .collectAsStateWithLifecycle(initialValue = ConnectionState.Available)

    if (uiModel.arrivals is Result.Error) {
        ErrorScreen(
            connectivityState = connectivityState,
            forceUpdate = forceUpdate,
        )
    } else {
        Crossfade(
            targetState = uiModel.arrivals is Result.Loading,
            label = "loading crossfade",
        ) { isLoading ->
            when (isLoading || uiModel.arrivals !is Result.Valid) {
                true -> LoadingScreen()
                false -> LoadedScreen(
                    uiModel = uiModel,
                    locationPermissionsUpdated = locationPermissionsUpdated,
                    arrivals = uiModel.arrivals,
                    connectivityState = connectivityState,
                    anyLocationPermissionsGranted = anyLocationPermissionsGranted,
                    userState = userState,
                    setShowingOppositeDirection = setShowingOppositeDirection,
                    setShowElevatorAlerts = setShowElevatorAlerts,
                    setShowHelpGuide = setShowHelpGuide,
                    snackbarState = snackbarState,
                    now = now,
                )
            }
        }
    }
}

@Composable
private fun LoadedScreen(
    uiModel: MainUiModel,
    locationPermissionsUpdated: suspend (ImmutableList<String>) -> Unit,
    arrivals: Result.Valid<ArrivalsUiModel>,
    connectivityState: ConnectionState,
    anyLocationPermissionsGranted: Boolean,
    userState: UserState,
    setShowingOppositeDirection: (Boolean) -> Unit,
    setShowElevatorAlerts: (Boolean) -> Unit,
    setShowHelpGuide: (Boolean) -> Unit,
    snackbarState: SnackbarHostState,
    now: Long,
) {
    val (showDirectionWarning, setShowDirectionWarning) = rememberBooleanPreference(
        keyName = "showDirectionWarning",
        initialValue = true,
        defaultValue = true,
    )

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
    val lastUpdatedState = rememberLastUpdatedState(arrivals.lastUpdated)
    lastUpdatedState.KeepUpdatedEffect(arrivals.lastUpdated, 1.seconds)

    val (alertsExpanded, setAlertsExpanded) = remember { mutableStateOf(false) }

    val autoRefreshingNow = connectivityState != ConnectionState.Unavailable

    val spacingModifier = Modifier.padding(vertical = 4.dp)

    LazyColumn(
        modifier = Modifier.padding(horizontal = 10.dp),
    ) {
        item { requireOptionalLocationItem(spacingModifier) }
        item {
            val alertsModel = when (uiModel.alerts) {
                is Result.Valid -> uiModel.alerts.copy(
                    data = uiModel.alerts.data.copy(
                        alerts = uiModel.alerts.data.alerts.filter {
                            when {
                                userState.showElevatorAlerts -> true
                                it is AlertData.Single -> !it.isElevator
                                else -> true
                            }
                        }.toImmutableList(),
                    ),
                )
                else -> uiModel.alerts
            }
            uiModel.alerts
            ExpandableAlerts(
                modifier = spacingModifier,
                connectivityState = connectivityState,
                alertsResult = alertsModel,
                expanded = alertsExpanded,
                setExpanded = setAlertsExpanded,
                setShowElevatorAlerts = setShowElevatorAlerts,
            )
        }
        if (anyLocationPermissionsGranted && showDirectionWarning) {
            item {
                DirectionWarning(
                    modifier = spacingModifier,
                    isInNJ = userState.isInNJ,
                    showOppositeDirection = userState.showOppositeDirection,
                    setShowingOppositeDirection = setShowingOppositeDirection,
                    snackbarState = snackbarState,
                    setShowDirectionWarning = setShowDirectionWarning,
                )
            }
        }
        item {
            AnimatedVisibility(
                visible = arrivals.hasError,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                ErrorBar(
                    modifier = spacingModifier,
                    connectivityState = connectivityState,
                )
            }
        }
        item {
            Spacer(modifier = spacingModifier)
        }
        item {
            AnimatedVisibility(
                visible = !autoRefreshingNow && lastUpdatedState.value.secondsAgo > TOP_LAST_UPDATED_THRESHOLD_SECS,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                LastUpdatedInfoRow(
                    modifier = spacingModifier,
                    lastUpdatedState = lastUpdatedState.value,
                )
            }
        }
        items(arrivals.data, contentType = { "station" }) {
            Station(
                modifier = spacingModifier,
                station = it,
                now = now,
                userState = userState,
                autoRefreshingNow = autoRefreshingNow,
                setShowHelpGuide = setShowHelpGuide,
            )
        }
        item {
            LastUpdatedInfoRow(
                modifier = spacingModifier,
                lastUpdatedState = lastUpdatedState.value,
            )
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
    val showElevatorAlerts: Boolean,
    val showHelpGuide: Boolean,
    val isInNJ: Boolean,
)

const val TOP_LAST_UPDATED_THRESHOLD_SECS: Long = 60 * 2
