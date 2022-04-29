package ca.amandeep.path.ui.stations

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.amandeep.path.Coordinates
import ca.amandeep.path.Direction
import ca.amandeep.path.Route
import ca.amandeep.path.RouteStation
import ca.amandeep.path.Station
import ca.amandeep.path.UpcomingTrain
import ca.amandeep.path.ui.main.MainViewModel
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.theme.Card3
import ca.amandeep.path.ui.theme.PATHTheme
import java.util.Date
import java.util.Locale
import kotlin.time.ExperimentalTime

private val PATH_BLUE = Color(0xff003da0)

@OptIn(ExperimentalTime::class)
@Composable
fun Station(
    station: Pair<Station, List<MainViewModel.UiUpcomingTrain>>,
    userState: UserState,
) {
    Card3(
        modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
        elevation = 10.dp,
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().background(PATH_BLUE),
            ) {
                Text(
                    text = station.first.name.uppercase(Locale.US),
                    color = HEADING_LIGHT_TEXT_COLOR,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(15.dp)
                )
            }
            Column(
                modifier = Modifier.padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val trains = station.second
                    .filterNot { it.pastTrain }
                    .filter {
                        if (userState.showOppositeDirection) true
                        else it.isInOppositeDirection
                    }
                if (trains.isEmpty()) {
                    val stateBound =
                        if (!userState.showOppositeDirection) when (userState.isInNJ) {
                            true -> Direction.TO_NJ
                            false -> Direction.TO_NY
                        }.stateName + "-bound " else ""
                    Text(
                        "No upcoming ${stateBound}trains at ${station.first.name}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                trains.forEach {
                    Train(it, userState)
                }
            }
        }
    }
}

@Composable
private fun Train(
    train: MainViewModel.UiUpcomingTrain,
    userState: UserState,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (userState.showOppositeDirection) {
            Icon(
                when (train.upcomingTrain.direction) {
                    Direction.TO_NJ -> Icons.Filled.ArrowBack
                    Direction.TO_NY -> Icons.Filled.ArrowForward
                },
                contentDescription = "To ${train.upcomingTrain.direction.stateName}",
            )
            Spacer(Modifier.width(5.dp))
        }
        TrainHeading(train.upcomingTrain, userState)
        Spacer(modifier = Modifier.weight(1f))

        Text(
            when (train.arrivalInMinutesFromNow) {
                0 -> "now"
                else -> train.arrivalInMinutesFromNow.toString()
            },
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            modifier = Modifier.alignByBaseline()
        )
        ProvideTextStyle(TextStyle(fontWeight = FontWeight.Light)) {
            when (train.arrivalInMinutesFromNow) {
                0 -> Unit
                1 -> {
                    Spacer(Modifier.width(6.dp))
                    Text("min")
                    Text("s", color = Color.Transparent)
                }
                else -> {
                    Spacer(Modifier.width(6.dp))
                    Text("mins")
                }
            }
        }
    }
}

@Composable
private fun TrainHeading(
    train: UpcomingTrain,
    userState: UserState,
) {
    Row {
        val hasVia = train.route.via != null
        SingleTrainHeading(
            train.route,
            train.direction,
            shortName = userState.shortenNames || hasVia
        )
        if (hasVia) {
            Spacer(Modifier.width(5.dp))
            SingleTrainHeading(
                train.route,
                train.direction,
                shortName = true,
                isVia = true
            )
        }
    }
}

private val JSQ_33_COLOR = Color(240, 171, 67)
private val HOB_33_COLOR = Color(43, 133, 187)
private val HOB_WTC_COLOR = Color(70, 156, 35)
private val NWK_WTC_COLOR = Color(213, 61, 46)

private val HEADING_LIGHT_TEXT_COLOR = Color(0xFFEBEBEB)
private val HEADING_DARK_TEXT_COLOR = Color(0xFF2C2C2C)

@Composable
private fun SingleTrainHeading(
    route: Route,
    direction: Direction,
    shortName: Boolean = false,
    isVia: Boolean = false,
) {
    val station = if (isVia && route.via != null)
        route.via
    else when (direction) {
        Direction.TO_NJ -> route.njTerminus
        Direction.TO_NY -> route.nyTerminus
    }
    val name = when (station) {
        RouteStation.JSQ -> if (shortName) "JSQ" else "Journal Square"
        RouteStation.NWK -> if (shortName) "NWK" else "Newark"
        RouteStation.WTC -> if (shortName) "WTC" else "World Trade Center"
        RouteStation.HOB -> if (shortName) "HOB" else "Hoboken"
        RouteStation.THIRTY_THIRD -> if (shortName) "33rd" else "33rd St"
    }
    val pillColor = when (route) {
        Route.JSQ_33 -> JSQ_33_COLOR
        Route.HOB_33 -> HOB_33_COLOR
        Route.HOB_WTC -> HOB_WTC_COLOR
        Route.NWK_WTC -> NWK_WTC_COLOR
        Route.JSQ_33_HOB -> if (isVia) HOB_33_COLOR else JSQ_33_COLOR
    }
    val textColor = when (route) {
        Route.JSQ_33 -> HEADING_DARK_TEXT_COLOR
        Route.HOB_33 -> HEADING_LIGHT_TEXT_COLOR
        Route.HOB_WTC -> HEADING_LIGHT_TEXT_COLOR
        Route.NWK_WTC -> HEADING_LIGHT_TEXT_COLOR
        Route.JSQ_33_HOB -> if (isVia) HEADING_LIGHT_TEXT_COLOR else HEADING_DARK_TEXT_COLOR
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = pillColor
    ) {
        Text(
            text = name,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
        )
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun StationPreviewDark() = StationPreview()

@Preview
@Composable
private fun StationPreview() {
    PATHTheme {
        Station(
            Station(
                station = "WTC",
                name = "World Trade Center",
                coordinates = Coordinates(0.0, 0.0)
            ) to listOf(
                MainViewModel.UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33,
                        direction = Direction.TO_NY,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 0,
                    isInOppositeDirection = false
                ),
                MainViewModel.UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.NWK_WTC,
                        direction = Direction.TO_NJ,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 1,
                    isInOppositeDirection = false
                ),
                MainViewModel.UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.HOB_WTC,
                        direction = Direction.TO_NJ,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 33,
                    isInOppositeDirection = false
                ),
                MainViewModel.UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33_HOB,
                        direction = Direction.TO_NJ,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 5,
                    isInOppositeDirection = false
                ),
            ),
            userState = UserState(
                shortenNames = derivedStateOf { true },
                showOppositeDirection = derivedStateOf { true },
                isInNJ = derivedStateOf { true }
            ),
        )
    }
}

@Composable
@Preview
fun EmptyStationPreview() {
    Station(
        station = Station(
            station = "HOB",
            name = "Hoboken",
            coordinates = Coordinates(0.0, 0.0)
        ) to emptyList(),
        userState = UserState(
            shortenNames = derivedStateOf { false },
            showOppositeDirection = derivedStateOf { true },
            isInNJ = derivedStateOf { true },
        ),
    )
}