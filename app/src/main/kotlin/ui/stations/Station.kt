package ca.amandeep.path.ui.stations

import android.annotation.SuppressLint
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
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
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.ui.main.UiUpcomingTrain
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.theme.PATHTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

private val PATH_BLUE = Color(0xff003da0)
private val PATH_ON_BLUE = Color(0xeeeeeeee)

@Composable
fun Station(
    station: Pair<StationName, ImmutableList<UiUpcomingTrain>>,
    now: Long,
    userState: UserState,
    modifier: Modifier = Modifier,
    autoRefreshingNow: Boolean = false,
    setShowHelpGuide: (Boolean) -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PATH_BLUE),
            ) {
                Text(
                    text = station.first.longName.uppercase(Locale.US),
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
                                if (userState.isInNJ) Direction.ToNY else Direction.ToNJ
                            stringResource(
                                R.string.no_trains_bound,
                                direction.stateName,
                            )
                        } else {
                            stringResource(R.string.no_trains)
                        } + station.first.longName,
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
            StationName.WTC to persistentListOf(
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33,
                        direction = Direction.ToNJ,
                        minsToArrival = 0,
                    ),
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
                    arrivalInMinutesFromNow = 33,
                    isInOppositeDirection = false,
                ),
                UiUpcomingTrain(
                    UpcomingTrain(
                        route = Route.JSQ_33_HOB,
                        direction = Direction.ToNJ,
                        minsToArrival = 5,
                    ),
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
            station = StationName.HOB to persistentListOf(),
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
