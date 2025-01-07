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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.InstantAnimationsRule
import app.cash.paparazzi.Paparazzi
import ca.amandeep.path.R
import ca.amandeep.path.data.AlertData
import ca.amandeep.path.data.AlertDatas
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.ui.LastUpdatedUiModel
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.util.ConnectionState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Date
import kotlin.time.Duration.Companion.minutes

val DATA: List<Any> = run {
    @Suppress("RemoveExplicitTypeArguments")
    val devices = listOfNotNull<List<Any>>(
//        listOf("Pixel 4", DeviceConfig.PIXEL_4),
//        listOf("Pixel 4a", DeviceConfig.PIXEL_4A),
//        listOf("Pixel 4 XL", DeviceConfig.PIXEL_4_XL),
//        listOf("Pixel 5", DeviceConfig.PIXEL_5),
//        listOf("Pixel 6", DeviceConfig.PIXEL_6),
//        listOf("Pixel 6 Pro", DeviceConfig.PIXEL_6_PRO),
//        listOf("Pixel 7", PIXEL_7),
//        listOf("Pixel 7a", PIXEL_7A),
//        listOf("Pixel 7 Pro", PIXEL_7_PRO),
//        listOf("Pixel 8", PIXEL_8),
//        listOf("Pixel 8a", PIXEL_8A),
        listOf("Pixel 8 Pro", PIXEL_8_PRO),
//        listOf("Pixel Fold", PIXEL_FOLD),
//        listOf("Pixel Fold Outer", PIXEL_FOLD_OUTER),
//        listOf("Galaxy Z Fold5", GALAXY_Z_FOLD_5),
//        listOf("Galaxy Z Fold5 Outer", GALAXY_Z_FOLD_5_OUTER),
//        listOf("Galaxy Z Flip5", GALAXY_Z_FLIP_5),
//        listOf("Galaxy Z Flip5 Outer", GALAXY_Z_FLIP_5_OUTER),
//        listOf("Galaxy S23", GALAXY_S_23),
//        listOf("Galaxy S23+", GALAXY_S_23_PLUS),
//        listOf("Galaxy S23 Ultra", GALAXY_S_23_ULTRA),
//        listOf("Galaxy S24", GALAXY_S_24),
//        listOf("Galaxy S24+", GALAXY_S_24_PLUS),
//        listOf("Galaxy S24 Ultra", GALAXY_S_24_ULTRA),
    ).andLandscape() + listOf<List<Any>>(
        //
    )

    @Suppress("ktlint:standard:no-multi-spaces", "ktlint:standard:comment-wrapping")
    listOf(
        /* isDarkMode */            listOf(true, false),
        /* alertsExpanded */        listOf(true, false, null),
        /* showDirectionWarning */  listOf(true, false).filterNot { it },
        /* showHelpGuide */         listOf(true, false),
        /* showOppositeDirection */ listOf(true, false),
        /* includeNotifs */         listOf(true, false),
        /* updatedWhen */           listOf(0, 60 * 7).filter { it == 0 },
        /* shortenNames */          listOf(true, false),
        devices,
    ).cartesianProduct().map {
        val device = it[8] as List<*>

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
            it[7] as Boolean,
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
    private val shortenNames: Boolean,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(
            name = "darkMode={0}," +
                "device={1}," +
                "alertsExp={3}," +
                "dirWarn={4}," +
                "helpGuide={5}," +
                "showOppoDir={6}," +
                "showNotifs={7}," +
                "updatedWhen={8}," +
                "shortNames={9},",
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
        useDeviceResolution = true,
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
                                    shortenNames = shortenNames,
                                    showOppositeDirection = showOppositeDirection,
                                    showElevatorAlerts = false,
                                    showHelpGuide = showHelpGuide,
                                    isInNJ = false,
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
                    routes = persistentListOf(Route.NWK_WTC),
                    text = "delayed",
                ),
                main = AlertData.Single(
                    text = "Work is ongoing to correct a signal problem at Exchange Place",
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
                    routes = persistentListOf(Route.HOB_33, Route.JSQ_33),
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

    private fun arrivalsData() = persistentListOf(
        StationName.WTC to listOf(
            mockTrain(
                route = Route.HOB_WTC,
                direction = Direction.ToNJ,
                minsToArrival = 0,
                showDirectionHelpText = true,
            ),
            mockTrain(
                route = Route.NWK_WTC,
                direction = Direction.ToNJ,
                minsToArrival = 4,
                alerts = (alertsData().alerts.getOrNull(0) as? AlertData.Grouped)?.let { persistentListOf(it) } ?: persistentListOf(),
                forceAlertsOpen = alertsExpanded == true,
            ),
            mockTrain(
                route = Route.HOB_WTC,
                direction = Direction.ToNJ,
                minsToArrival = 12,
            ),
        ).toTrainList(),
        StationName.CHR to listOf(
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNJ,
                minsToArrival = 2,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNJ,
                minsToArrival = 14,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNJ,
                minsToArrival = 5,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNJ,
                minsToArrival = 18,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNY,
                minsToArrival = 1,
                isInOppositeDirection = false,
                showDirectionHelpText = true,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNY,
                minsToArrival = 12,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNY,
                minsToArrival = 3,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNY,
                minsToArrival = 14,
                isInOppositeDirection = false,
            ),
        ).toTrainList(),
        StationName.S9 to listOf(
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNJ,
                minsToArrival = 0,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNJ,
                minsToArrival = 12,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNJ,
                minsToArrival = 3,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNJ,
                minsToArrival = 14,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNY,
                minsToArrival = 4,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNY,
                minsToArrival = 15,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNY,
                minsToArrival = 7,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNY,
                minsToArrival = 19,
                isInOppositeDirection = false,
            ),
        ).toTrainList(),
        StationName.S14 to listOf(
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNJ,
                minsToArrival = 9,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNJ,
                minsToArrival = 0,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNJ,
                minsToArrival = 10,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNY,
                minsToArrival = 1,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.JSQ_33,
                direction = Direction.ToNY,
                minsToArrival = 19,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNY,
                minsToArrival = 10,
                isInOppositeDirection = false,
            ),
            mockTrain(
                route = Route.HOB_33,
                direction = Direction.ToNY,
                minsToArrival = 22,
                isInOppositeDirection = false,
            ),
        ).toTrainList(),
    )

    private fun mockTrain(
        route: Route,
        direction: Direction,
        minsToArrival: Int,
        isInOppositeDirection: Boolean = true,
        showDirectionHelpText: Boolean = false,
        alerts: ImmutableList<AlertData.Grouped> = persistentListOf(),
        forceAlertsOpen: Boolean = false,
    ): UiUpcomingTrain? = UiUpcomingTrain(
        UpcomingTrain(
            route = route,
            direction = direction,
            minsToArrival = minsToArrival,
        ),
        arrivalInMinutesFromNow = minsToArrival,
        isInOppositeDirection = isInOppositeDirection,
        showDirectionHelpText = showDirectionHelpText,
        alerts = alerts,
        forceAlertsOpen = forceAlertsOpen,
    ).takeUnless { !showOppositeDirection && !isInOppositeDirection }

    private fun List<UiUpcomingTrain?>.toTrainList() = filterNotNull()
        .sortedWith(compareBy({ it.upcomingTrain.direction }, { it.arrivalInMinutesFromNow }))
        .toImmutableList()
}
