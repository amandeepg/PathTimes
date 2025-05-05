@file:OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalMaterial3Api::class,
)

package ca.amandeep.path.ui.main

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.amandeep.path.DeveloperStatus
import ca.amandeep.path.data.model.State
import ca.amandeep.path.main.core.ArrivalsUiModel
import ca.amandeep.path.main.core.MainUiModel
import ca.amandeep.path.main.core.Result
import ca.amandeep.path.main.core.UserState
import ca.amandeep.path.prefs.UserPreferencesRepo
import ca.amandeep.path.strings.R
import ca.amandeep.path.ui.alerts.ExpandableAlerts
import ca.amandeep.path.util.ConnectionState
import ca.amandeep.path.util.checkPermission
import ca.amandeep.path.util.observeConnectivity
import ca.amandeep.ui.core.ErrorBar
import ca.amandeep.ui.core.ErrorScreen
import ca.amandeep.ui.core.KeepUpdatedEffect
import ca.amandeep.ui.core.LastUpdatedInfoRow
import ca.amandeep.ui.core.LastUpdatedUiModel
import ca.amandeep.ui.core.rememberLastUpdatedState
import ca.amandeep.ui.core.requireOptionalLocationItem
import com.github.ajalt.timberkt.d
import com.path.ui.stations.DirectionWarning
import com.path.ui.stations.Station
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ui.main.OverflowItems
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    windowSizeClass: WindowSizeClass,
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
            context.checkPermission(ACCESS_COARSE_LOCATION) || context.checkPermission(
                ACCESS_FINE_LOCATION,
            ),
        )
    }

    val showDebugOptions by DeveloperStatus.developerModeFlow.collectAsStateWithLifecycle()

    val store = UserPreferencesRepo(context)

    val showModelNamePref by store.showModelName.collectAsStateWithLifecycle(initialValue = false)
    val aiSummarizeAlertsPref by store.aiSummarizeAlerts.collectAsStateWithLifecycle(initialValue = false)
    val shortenNamesPref by store.shortenNames.collectAsStateWithLifecycle(initialValue = false)
    val showOppositeDirectionPref by store.showOppositeDirection.collectAsStateWithLifecycle(initialValue = true)
    val showElevatorAlertsPref by store.showElevatorAlerts.collectAsStateWithLifecycle(initialValue = true)
    val showHelpGuidePref by store.showHelpGuide.collectAsStateWithLifecycle(initialValue = true)

    val setShowModelNamePref = store::updateShowModelName
    val setAiSummarizeAlertsPref = store::updateAiSummarizeAlerts
    val setShortenNamesPref = store::updateShortenNames
    val setShowOppositeDirectionPref = store::updateShowOppositeDirection
    val setShowElevatorAlertsPref = store::updateShowElevatorAlerts
    val setShowHelpGuidePref = store::updateShowHelpGuide

    val showOppositeDirection by remember(
        showOppositeDirectionPref,
        anyLocationPermissionsGranted,
    ) {
        derivedStateOf { showOppositeDirectionPref || !anyLocationPermissionsGranted }
    }

    val setShowElevatorAlertsWithUndo: (Boolean) -> Unit = run {
        val snackbarMessage = stringResource(R.string.change_dir_in_options)
        val snackbarActionLabel = stringResource(R.string.undo)
        return@run { newValue: Boolean ->
            coroutineScope.launch {
                setShowElevatorAlertsPref(newValue)
                if (!newValue) {
                    val snackbarResult = snackbarState.showSnackbar(
                        message = snackbarMessage,
                        actionLabel = snackbarActionLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        setShowElevatorAlertsPref(true)
                    }
                }
            }
        }
    }

    val setShowHelpGuideWithUndo: (Boolean) -> Unit = run {
        val snackbarMessage = stringResource(R.string.change_dir_in_options)
        val snackbarActionLabel = stringResource(R.string.undo)
        return@run { newValue: Boolean ->
            coroutineScope.launch {
                setShowHelpGuidePref(newValue)
                if (!newValue) {
                    val snackbarResult = snackbarState.showSnackbar(
                        message = snackbarMessage,
                        actionLabel = snackbarActionLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        setShowHelpGuidePref(true)
                    }
                }
            }
        }
    }

    val overflowItems: @Composable RowScope.() -> Unit = {
        OverflowItems(
            forceRefresh = forceRefresh,
            showDebugOptions = showDebugOptions,
            showModelNamePref = showModelNamePref,
            setShowModelNamePref = setShowModelNamePref,
            aiSummarizeAlertsPref = aiSummarizeAlertsPref,
            setAiSummarizeAlertsPref = setAiSummarizeAlertsPref,
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
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = { TopBar(overflowItems = overflowItems) },
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

        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

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
                    debugOptions = UserState.Debug(
                        showModelName = showModelNamePref,
                        aiSummarizeAlerts = aiSummarizeAlertsPref,
                    ),
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
                windowSizeClass = windowSizeClass,
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
@VisibleForTesting
fun TopBar(
    modifier: Modifier = Modifier,
    overflowItems: @Composable (RowScope.() -> Unit),
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = { Text(stringResource(id = R.string.app_name)) },
        actions = { overflowItems() },
    )
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
    val allTrainsEmpty = lastGoodState.arrivals.let {
        it is Result.Valid &&
            it.data.all { it.second.all { it.isDepartedTrain } }
    }
    LaunchedEffect(allTrainsEmpty, forceUpdate) {
        if (allTrainsEmpty) {
            forceUpdate()
            setLastGoodState(
                MainUiModel(alerts = lastGoodState.alerts),
            )
        }
    }

    d { "lastGoodState: $lastGoodState" }

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
    setShowingOppositeDirection: suspend (Boolean) -> Unit,
    setShowElevatorAlerts: suspend (Boolean) -> Unit,
    setShowHelpGuide: suspend (Boolean) -> Unit,
    windowSizeClass: WindowSizeClass,
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
            val uiModelArrivals = uiModel.arrivals
            when (isLoading || uiModelArrivals !is Result.Valid) {
                true -> LoadingScreen()
                false -> {
                    val lastUpdatedState = rememberLastUpdatedState(uiModelArrivals.lastUpdated)
                    lastUpdatedState.KeepUpdatedEffect(uiModelArrivals.lastUpdated, 1.seconds)
                    val requireOptionalLocationItem = requireOptionalLocationItem(
                        permissionsUpdated = locationPermissionsUpdated,
                        navigateToSettingsScreen = {
                            it.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", it.packageName, null),
                                ),
                                null,
                            )
                        },
                    )

                    val context = LocalContext.current
                    val store = UserPreferencesRepo(context)
                    val showDirectionWarning by store.showDirectionWarning.collectAsStateWithLifecycle(initialValue = true)
                    val setShowDirectionWarningPref = store::updateShowDirectionWarning

                    val (alertsExpanded, setAlertsExpanded) = remember { mutableStateOf(false) }
                    LoadedScreen(
                        connectivityState = connectivityState,
                        requireOptionalLocationItem = requireOptionalLocationItem,
                        uiModel = uiModel,
                        userState = userState,
                        setShowElevatorAlerts = setShowElevatorAlerts,
                        anyLocationPermissionsGranted = anyLocationPermissionsGranted,
                        setShowingOppositeDirection = setShowingOppositeDirection,
                        snackbarState = snackbarState,
                        lastUpdatedState = lastUpdatedState.value,
                        now = now,
                        setShowHelpGuide = setShowHelpGuide,
                        alertsExpanded = alertsExpanded,
                        showDirectionWarning = showDirectionWarning,
                        setAlertsExpanded = setAlertsExpanded,
                        setShowDirectionWarning = setShowDirectionWarningPref,
                        windowSizeClass = windowSizeClass,
                    )
                }
            }
        }
    }
}

@Composable
@VisibleForTesting
fun LoadedScreen(
    requireOptionalLocationItem: @Composable (Modifier) -> Unit,
    uiModel: MainUiModel,
    connectivityState: ConnectionState,
    anyLocationPermissionsGranted: Boolean,
    userState: UserState,
    setShowingOppositeDirection: suspend (Boolean) -> Unit,
    setShowElevatorAlerts: suspend (Boolean) -> Unit,
    snackbarState: SnackbarHostState,
    lastUpdatedState: LastUpdatedUiModel,
    now: Long,
    setShowHelpGuide: suspend (Boolean) -> Unit,
    alertsExpanded: Boolean,
    showDirectionWarning: Boolean,
    setAlertsExpanded: (Boolean) -> Unit,
    setShowDirectionWarning: suspend (Boolean) -> Unit,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    val arrivals = uiModel.arrivals as Result.Valid<ArrivalsUiModel>

    val autoRefreshingNow = connectivityState != ConnectionState.Unavailable

    val spacingModifier = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> Modifier.padding(vertical = 4.dp)
        WindowWidthSizeClass.Medium -> Modifier.padding(vertical = 4.dp)
        WindowWidthSizeClass.Expanded -> Modifier.padding(bottom = 8.dp)
        else -> Modifier.padding(vertical = 4.dp)
    }

    val columns = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 1
        WindowWidthSizeClass.Medium -> 2
        WindowWidthSizeClass.Expanded -> 2
        else -> 1
    }
    val horizontalArrangement = when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> 0.dp
        WindowWidthSizeClass.Medium -> 10.dp
        WindowWidthSizeClass.Expanded -> 10.dp
        else -> 0.dp
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(horizontalArrangement),
        modifier = modifier.padding(horizontal = 10.dp),
    ) {
        item(
            contentType = "top",
        ) {
            Column {
                requireOptionalLocationItem(spacingModifier)

                val alertsModel = when (val uiModelAlerts = uiModel.alerts) {
                    is Result.Valid -> uiModelAlerts.copy(
                        data = uiModelAlerts.data.copy(
                            alerts = uiModelAlerts.data.alerts.filter {
                                if (userState.showElevatorAlerts) {
                                    true
                                } else {
                                    !it.isElevator
                                }
                            }.toImmutableList(),
                        ),
                    )

                    else -> uiModelAlerts
                }
                ExpandableAlerts(
                    modifier = spacingModifier,
                    connectivityState = connectivityState,
                    alertsResult = alertsModel,
                    expanded = alertsExpanded,
                    setExpanded = setAlertsExpanded,
                    userState = userState,
                    setShowElevatorAlerts = setShowElevatorAlerts,
                )
                if (anyLocationPermissionsGranted && showDirectionWarning) {
                    DirectionWarning(
                        modifier = spacingModifier,
                        isInNJ = userState.isInNJ,
                        showOppositeDirection = userState.showOppositeDirection,
                        setShowingOppositeDirection = setShowingOppositeDirection,
                        snackbarState = snackbarState,
                        setShowDirectionWarning = setShowDirectionWarning,
                    )
                }
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
                when (windowSizeClass.widthSizeClass) {
                    WindowWidthSizeClass.Compact -> Unit
                    WindowWidthSizeClass.Medium -> Spacer(modifier = spacingModifier)
                    WindowWidthSizeClass.Expanded -> Spacer(modifier = spacingModifier)
                    else -> Unit
                }
                AnimatedVisibility(
                    visible = !autoRefreshingNow && lastUpdatedState.secondsAgo > TOP_LAST_UPDATED_THRESHOLD_SECS,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    LastUpdatedInfoRow(
                        modifier = spacingModifier,
                        lastUpdatedState = lastUpdatedState,
                    )
                }
            }
        }
        val arrivalsData = if (userState.showOppositeDirection) {
            arrivals.data
        } else {
            arrivals.data.filter {
                it.first.stationName.state == (if (userState.isInNJ) State.NJ else State.NY)
            }
        }
        items(
            count = arrivalsData.size,
            contentType = { "station" },
        ) {
            Station(
                modifier = spacingModifier,
                station = arrivalsData[it],
                now = now,
                userState = userState,
                autoRefreshingNow = autoRefreshingNow,
                setShowHelpGuide = setShowHelpGuide,
            )
        }
        item(
            span = StaggeredGridItemSpan.FullLine,
            contentType = "lastUpdated",
        ) {
            LastUpdatedInfoRow(
                modifier = spacingModifier,
                lastUpdatedState = lastUpdatedState,
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

const val TOP_LAST_UPDATED_THRESHOLD_SECS: Long = 60 * 2
