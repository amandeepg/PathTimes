package ca.amandeep.path.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import app.cash.paparazzi.Paparazzi
import ca.amandeep.path.R
import ca.amandeep.path.data.model.AlertData
import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.Coordinates
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.Station
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.ui.LastUpdatedUiModel
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.util.ConnectionState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Date
import kotlin.time.Duration.Companion.minutes

val DATA: List<Any> = run {
    @Suppress("KotlinConstantConditions")
    val devices = listOfNotNull<List<Any>>(
//        listOf("Pixel 4", DeviceConfig.PIXEL_4),
//        listOf("Pixel 4a", DeviceConfig.PIXEL_4A),
//        listOf("Pixel 4 XL", DeviceConfig.PIXEL_4_XL),
//        listOf("Pixel 5", DeviceConfig.PIXEL_5),
//        listOf("Pixel 6", DeviceConfig.PIXEL_6),
//        listOf("Pixel 6 Pro", DeviceConfig.PIXEL_6_PRO),
        listOf("Pixel 7", PIXEL_7),
//        listOf("Pixel 7a", PIXEL_7A),
//        listOf("Pixel 7 Pro", PIXEL_7_PRO),
//        listOf("Pixel Fold", PIXEL_FOLD),
//        listOf("Pixel Fold Outer", PIXEL_FOLD_OUTER),
//        listOf("Galaxy Z Fold5", GALAXY_Z_FOLD_5),
//        listOf("Galaxy Z Fold5 Outer", GALAXY_Z_FOLD_5_OUTER),
//        listOf("Galaxy Z Flip5", GALAXY_Z_FLIP_5),
//        listOf("Galaxy Z Flip5 Outer", GALAXY_Z_FLIP_5_OUTER),
//        listOf("Galaxy S23", GALAXY_S_23),
//        listOf("Galaxy S23+", GALAXY_S_23_PLUS),
//        listOf("Galaxy S23 Ultra", GALAXY_S_23_ULTRA),
    ).andLandscape() + listOf<List<Any>>(
        //
    )

    @Suppress("ktlint:standard:no-multi-spaces", "ktlint:standard:comment-wrapping")
    listOf(
        /* isDarkMode */            listOf(true, false),
        /* alertsExpanded */        listOf(true, false, null),
        /* showDirectionWarning */  listOf(true, false).filterNot { it },
        /* showHelpGuide */         listOf(true, false).filterNot { it },
        /* showOppositeDirection */ listOf(true, false),
        /* includeNotifs */         listOf(true, false),
        /* updatedWhen */           listOf(0, 60 * 7).filter { it == 0 },
        devices,
    ).cartesianProduct().map {
        val device = it[7] as List<*>

        arrayOf(
            it[0] as Boolean,
            device[0],
            device[1],
            it[1] as Boolean?,
            it[2] as Boolean,
            it[3] as Boolean,
            it[4] as Boolean,
            it[5] as Boolean,
            it[6] as Int,
        )
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(Parameterized::class)
class MainScreenshotTest(
    private val isDarkMode: Boolean,
    @Suppress("unused") private val deviceName: String,
    private val device: DeviceConfig,
    private val alertsExpanded: Boolean?,
    private val showDirectionWarning: Boolean,
    private val showHelpGuide: Boolean,
    private val showOppositeDirection: Boolean,
    private val includeNotifs: Boolean,
    private val updatedWhen: Int,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(
            name = "isDarkMode={0}," +
                " deviceName={1}," +
                " alertsExpanded={3}," +
                " showDirectionWarning={4}," +
                " showHelpGuide={5}," +
                " showOppositeDirection={6}," +
                " showNotifs={7}," +
                " updatedWhen={8}",
        )
        fun data() = DATA
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = device,
        theme = when (isDarkMode) {
            true -> "android:ThemeOverlay.Material.Dark"
            false -> "android:Theme.Material.Light.NoActionBar"
        },
    )

    @get:Rule
    val instantAnimationsRule = InstantAnimationsRule()

    @Test
    fun screenshotMain() {
        paparazzi.snapshot {
            PATHTheme(
                darkTheme = isDarkMode,
                dynamicColor = false,
            ) {
                Box(
                    Modifier
                        .padding(5.dp)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Column {
                        StatusBar(includeNotifs = includeNotifs)
                        Scaffold(
                            topBar = {
                                TopBar {
                                    OverflowItems(
                                        forceRefresh = {},
                                        shortenNamesPref = false,
                                        setShortenNamesPref = {},
                                        showOppositeDirectionPref = false,
                                        setShowOppositeDirectionPref = {},
                                        showElevatorAlertsPref = false,
                                        showHelpGuidePref = false,
                                        setShowElevatorAlertsPref = {},
                                        setShowHelpGuidePref = {},
                                        anyLocationPermissionsGranted = false,
                                    )
                                }
                            },
                        ) {
                            LoadedScreen(
                                modifier = Modifier.padding(it),
                                requireOptionalLocationItem = {},
                                uiModel = MainUiModel(
                                    arrivals = Result.Valid(
                                        lastUpdated = System.currentTimeMillis(),
                                        data = (arrivalsData() + arrivalsData() + arrivalsData() + arrivalsData() + arrivalsData())
                                            .toImmutableList(),
                                    ),
                                    alerts = Result.Valid(
                                        lastUpdated = System.currentTimeMillis(),
                                        data = alertsData(),
                                    ),
                                ),
                                connectivityState = ConnectionState.Available,
                                anyLocationPermissionsGranted = true,
                                userState = UserState(
                                    shortenNames = true,
                                    showOppositeDirection = showOppositeDirection,
                                    showElevatorAlerts = false,
                                    showHelpGuide = showHelpGuide,
                                    isInNJ = true,
                                ),
                                setShowingOppositeDirection = {},
                                setShowElevatorAlerts = {},
                                snackbarState = SnackbarHostState(),
                                lastUpdatedState = LastUpdatedUiModel(
                                    unitDescriptionResId = R.string.seconds,
                                    units = updatedWhen.toLong(),
                                    isNow = updatedWhen == 0,
                                    secondsAgo = updatedWhen.toLong(),
                                ),
                                now = System.currentTimeMillis(),
                                setShowHelpGuide = {},
                                alertsExpanded = alertsExpanded == true,
                                showDirectionWarning = showDirectionWarning,
                                setAlertsExpanded = {},
                                setShowDirectionWarning = {},
                                windowSizeClass = WindowSizeClass.calculateFromSize(
                                    DpSize(
                                        width = (device.screenWidth / (device.xdpi / 160)).dp,
                                        height = (device.screenHeight / (device.ydpi / 160)).dp,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun alertsData(): AlertsUiModel = AlertDatas(
        persistentListOf(
            AlertData.Grouped(
                title = AlertData.Grouped.Title.RouteTitle(
                    routes = persistentListOf(Route.HOB_33, Route.JSQ_33),
                    text = "delayed",
                ),
                main = AlertData.Single(
                    text = "Work is ongoing to correct a signal problem at 33 St.",
                    date = Date(System.currentTimeMillis() - 14.minutes.inWholeMilliseconds),
                ),
                history = persistentListOf(
                    AlertData.Single(
                        "Something",
                        date = Date(),
                    ),
                ),
            ),
            AlertData.Grouped(
                title = AlertData.Grouped.Title.RouteTitle(
                    routes = persistentListOf(Route.NWK_WTC),
                    text = "resuming normal service",
                ),
                main = AlertData.Single(
                    text = "Crew resolved the earlier mechanical problem at JSQ",
                    date = Date(System.currentTimeMillis() - 124.minutes.inWholeMilliseconds),
                ),
                history = persistentListOf(
                    AlertData.Single(
                        "Something",
                        date = Date(),
                    ),
                ),
            ),
        ).takeUnless { alertsExpanded == null }.orEmpty().toImmutableList(),
    )

    @Composable
    private fun arrivalsData() = persistentListOf(
        Station(
            station = "WTC",
            name = "World Trade Center",
            coordinates = Coordinates(0.0, 0.0),
        ) to persistentListOf(
            UiUpcomingTrain(
                UpcomingTrain(
                    route = Route.JSQ_33,
                    direction = Direction.TO_NY,
                    projectedArrival = Date(System.currentTimeMillis() + 0.minutes.inWholeMilliseconds),
                ),
                arrivalInMinutesFromNow = 0,
                isInOppositeDirection = false,
                showDirectionHelpText = true,
            ),
            UiUpcomingTrain(
                UpcomingTrain(
                    route = Route.NWK_WTC,
                    direction = Direction.TO_NJ,
                    projectedArrival = Date(System.currentTimeMillis() + 1.minutes.inWholeMilliseconds),
                ),
                arrivalInMinutesFromNow = 1,
                isInOppositeDirection = false,
                showDirectionHelpText = true,
            ),
            UiUpcomingTrain(
                UpcomingTrain(
                    route = Route.HOB_WTC,
                    direction = Direction.TO_NJ,
                    projectedArrival = Date(System.currentTimeMillis() + 33.minutes.inWholeMilliseconds),
                ),
                arrivalInMinutesFromNow = 33,
                isInOppositeDirection = false,
            ),
            UiUpcomingTrain(
                UpcomingTrain(
                    route = Route.JSQ_33_HOB,
                    direction = Direction.TO_NJ,
                    projectedArrival = Date(System.currentTimeMillis() + 5.minutes.inWholeMilliseconds),
                ),
                arrivalInMinutesFromNow = 5,
                isInOppositeDirection = false,
            ),
        ),
    )
}
