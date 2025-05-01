package com.example.ui.stations

import android.annotation.SuppressLint
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.amandeep.path.data.AlertData
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.main.core.UiStation
import ca.amandeep.path.main.core.UiUpcomingTrain
import ca.amandeep.path.main.core.UserState
import ca.amandeep.path.strings.R
import ca.amandeep.path.util.darken
import ca.amandeep.path.util.lighten
import ca.amandeep.ui.core.collapsing.ExpandableView
import ca.amandeep.ui.core.collapsing.expandableClickable
import ca.amandeep.ui.core.theme.PATHTheme
import ca.amandeep.ui.core.theme.PATH_BLUE
import ca.amandeep.ui.core.theme.PATH_ON_BLUE
import com.example.ui.alerts.Alerts
import com.example.ui.alerts.ExpandedAlertArrowContent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

@Composable
fun Station(
    station: Pair<UiStation, ImmutableList<UiUpcomingTrain>>,
    now: Long,
    userState: UserState,
    modifier: Modifier = Modifier,
    autoRefreshingNow: Boolean = false,
    setShowHelpGuide: (Boolean) -> Unit,
) {
    val (alertsExpanded, setAlertsExpanded) = remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .background(PATH_BLUE)
                    .padding(horizontal = 15.dp),
            ) {
                val alertsBackgroundColor =
                    Color(if (isSystemInDarkTheme()) 0xff5d5224 else 0xfff4eab9)
                        .darken(if (isSystemInDarkTheme()) 0.0f else 0.15f)
                        .lighten(if (isSystemInDarkTheme()) 0.15f else 0.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (station.first.alerts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(end = 10.dp)
                                .expandableClickable { setAlertsExpanded(!alertsExpanded) },
                            color = alertsBackgroundColor,
                            contentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        ) {
                            ExpandedAlertArrowContent(
                                alertsExpanded = alertsExpanded,
                                contentDescription = stringResource(R.string.alerts_at_this_station),
                            )
                        }
                    }

                    Text(
                        text = station.first.stationName.longName.uppercase(Locale.US),
                        color = PATH_ON_BLUE,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(vertical = 15.dp),
                    )
                }
                if (station.first.alerts.isNotEmpty()) {
                    ExpandableView(
                        modifier = Modifier.offset(y = (-8).dp),
                        isExpanded = alertsExpanded || station.first.forceAlertsOpen,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = alertsBackgroundColor,
                            modifier = Modifier
                                .padding(bottom = 7.dp)
                                .fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Alerts(station.first.alerts)
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val trains = station.second
                    .filterNot { it.isDepartedTrain }
                    .filter {
                        if (userState.showOppositeDirection) {
                            true
                        } else {
                            it.isInOppositeDirection
                        }
                    }
                if (trains.isEmpty()) {
                    Text(
                        text = if (!userState.showOppositeDirection) {
                            val direction =
                                if (userState.isInNJ) Direction.ToNY else Direction.ToNJ
                            stringResource(
                                R.string.no_trains_bound,
                                direction.stateName,
                            )
                        } else {
                            stringResource(R.string.no_trains)
                        } + station.first.stationName.longName,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                trains.forEachIndexed { idx, train ->
                    Train(
                        train = train,
                        now = now,
                        userState = userState,
                        autoRefreshingNow = autoRefreshingNow,
                        isLastInStation = idx == trains.size - 1,
                        setShowHelpGuide = setShowHelpGuide,
                    )
                }
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun StationPreview() {
    PATHTheme {
        Station(
            UiStation(StationName.WTC) to persistentListOf(
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33,
                        direction = Direction.ToNJ,
                        minsToArrival = 0,
                    ),
                    direction = Direction.ToNJ,
                    arrivalInMinutesFromNow = 0,
                    isInOppositeDirection = false,
                    showDirectionHelpText = true,
                ),
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.NWK_WTC,
                        direction = Direction.ToNJ,
                        minsToArrival = 1,
                    ),
                    direction = Direction.ToNJ,
                    arrivalInMinutesFromNow = 1,
                    isInOppositeDirection = false,
                    showDirectionHelpText = true,
                ),
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.HOB_WTC,
                        direction = Direction.ToNJ,
                        minsToArrival = 33,
                    ),
                    direction = Direction.ToNJ,
                    arrivalInMinutesFromNow = 33,
                    isInOppositeDirection = false,
                ),
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33_HOB,
                        direction = Direction.ToNJ,
                        minsToArrival = 5,
                    ),
                    direction = Direction.ToNJ,
                    arrivalInMinutesFromNow = 5,
                    isInOppositeDirection = false,
                ),
            ),
            now = System.currentTimeMillis(),
            userState = UserState(
                shortenNames = true,
                showOppositeDirection = true,
                showElevatorAlerts = true,
                showHelpGuide = true,
                isInNJ = true,
            ),
            setShowHelpGuide = {},
        )
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
private fun EmptyStationPreview() {
    PATHTheme {
        Station(
            station = UiStation(
                StationName.HOB,
                alerts = persistentListOf(
                    AlertData.Grouped(
                        title = AlertData.Grouped.Title.StationTitle(
                            stations = persistentListOf(StationName.HOB),
                            text = "closed",
                        ),
                        main = AlertData.Single(text = "Station flooded.", date = null),
                    ),
                    AlertData.Grouped(
                        title = AlertData.Grouped.Title.StationTitle(
                            stations = persistentListOf(StationName.HOB),
                            text = "closed also",
                        ),
                        main = AlertData.Single(text = "Station flooded.", date = null),
                    ),
                ),
                forceAlertsOpen = true,
            ) to persistentListOf(),
            now = System.currentTimeMillis(),
            userState = UserState(
                shortenNames = false,
                showOppositeDirection = true,
                showElevatorAlerts = true,
                showHelpGuide = true,
                isInNJ = true,
            ),
            setShowHelpGuide = {},
        )
    }
}
