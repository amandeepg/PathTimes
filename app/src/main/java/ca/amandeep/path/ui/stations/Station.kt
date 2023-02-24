package ca.amandeep.path.ui.stations

import android.annotation.SuppressLint
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.amandeep.path.R
import ca.amandeep.path.data.model.Coordinates
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.Station
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.ui.main.UiUpcomingTrain
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.theme.Card3
import ca.amandeep.path.ui.theme.PATHTheme
import java.util.Date
import java.util.Locale

private val PATH_BLUE = Color(0xff003da0)
private val PATH_ON_BLUE = Color(0xeeeeeeee)

@Composable
fun Station(
    station: Pair<Station, List<UiUpcomingTrain>>,
    userState: UserState,
    modifier: Modifier = Modifier,
) {
    Card3(
        modifier = modifier.padding(horizontal = 15.dp, vertical = 9.dp),
        elevation = 10.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PATH_BLUE),
            ) {
                Text(
                    text = station.first.name.uppercase(Locale.US),
                    color = PATH_ON_BLUE,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(15.dp),
                )
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
                                if (userState.isInNJ) Direction.TO_NY else Direction.TO_NJ
                            stringResource(
                                R.string.no_trains_bound,
                                direction.stateName,
                            )
                        } else {
                            stringResource(R.string.no_trains)
                        } + station.first.name,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                trains.forEach {
                    Train(it, userState)
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
            Station(
                station = "WTC",
                name = "World Trade Center",
                coordinates = Coordinates(0.0, 0.0),
            ) to listOf(
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33,
                        direction = Direction.TO_NY,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 0,
                    isInOppositeDirection = false,
                ),
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.NWK_WTC,
                        direction = Direction.TO_NJ,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 1,
                    isInOppositeDirection = false,
                ),
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.HOB_WTC,
                        direction = Direction.TO_NJ,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 33,
                    isInOppositeDirection = false,
                ),
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33_HOB,
                        direction = Direction.TO_NJ,
                        projectedArrival = Date(),
                    ),
                    arrivalInMinutesFromNow = 5,
                    isInOppositeDirection = false,
                ),
            ),
            userState = UserState(
                shortenNames = true,
                showOppositeDirection = true,
                isInNJ = true,
            ),
        )
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
fun EmptyStationPreview() {
    PATHTheme {
        Station(
            station = Station(
                station = "HOB",
                name = "Hoboken",
                coordinates = Coordinates(0.0, 0.0),
            ) to emptyList(),
            userState = UserState(
                shortenNames = false,
                showOppositeDirection = true,
                isInNJ = true,
            ),
        )
    }
}
