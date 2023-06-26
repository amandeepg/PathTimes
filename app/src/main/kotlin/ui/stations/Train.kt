@file:Suppress("PrivatePropertyName")

package ca.amandeep.path.ui.stations

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.amandeep.path.R
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.RouteStation
import ca.amandeep.path.data.model.UpcomingTrain
import ca.amandeep.path.data.model.relativeArrivalMins
import ca.amandeep.path.ui.HEADING_DARK_TEXT_COLOR
import ca.amandeep.path.ui.HEADING_LIGHT_TEXT_COLOR
import ca.amandeep.path.ui.HOB_33_COLOR
import ca.amandeep.path.ui.HOB_WTC_COLOR
import ca.amandeep.path.ui.JSQ_33_COLOR
import ca.amandeep.path.ui.NWK_WTC_COLOR
import ca.amandeep.path.ui.main.UiUpcomingTrain
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.ui.theme.surfaceColorAtElevation
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * A single train with heading and next arrival time.
 */
@Composable
fun Train(
    train: UiUpcomingTrain,
    now: Long,
    userState: UserState,
    modifier: Modifier = Modifier,
    autoRefreshingNow: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (userState.showOppositeDirection) {
            Box(
                modifier = Modifier
//                    .size(20.dp)
                    .align(Alignment.CenterVertically)
                    .offset(x = (-4).dp),
            ) {
                Icon(
                    modifier = Modifier
                        .size(20.dp)
                        .align(
                            when (train.upcomingTrain.direction) {
                                Direction.TO_NJ -> Alignment.CenterStart
                                Direction.TO_NY -> Alignment.CenterEnd
                            },
                        )
                        .offset(
                            x = when (train.upcomingTrain.direction) {
                                Direction.TO_NJ -> (-6).dp
                                Direction.TO_NY -> (6).dp
                            },
                        ),
                    imageVector = when (train.upcomingTrain.direction) {
                        Direction.TO_NJ -> Icons.Filled.ArrowBackIosNew
                        Direction.TO_NY -> Icons.Filled.ArrowForwardIos
                    },
                    contentDescription = stringResource(R.string.to) + train.upcomingTrain.direction.stateName,
                )
                Text(
                    modifier = when (train.upcomingTrain.direction) {
                        Direction.TO_NJ -> Modifier.align(Alignment.CenterEnd)
                        Direction.TO_NY -> Modifier.align(Alignment.CenterStart)
                    },
                    text = when (train.upcomingTrain.direction) {
                        Direction.TO_NJ -> "NJ"
                        Direction.TO_NY -> "NY"
                    },
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.width(5.dp))
        }
        TrainHeading(train.upcomingTrain, userState)
        Spacer(modifier = Modifier.weight(1f))

        val arrivalTime = train.upcomingTrain.relativeArrivalMins(now).roundToInt()
        val oldTime = autoRefreshingNow && abs(arrivalTime - train.arrivalInMinutesFromNow) >= 2
        Row {
            Crossfade(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 4.dp),
                targetState = oldTime,
                label = "Arrival time crossfade",
            ) { oldTime ->
                if (oldTime) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.Gray.copy(alpha = 0.3f),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
            }
            if (arrivalTime >= 0) {
                Text(
                    when (arrivalTime) {
                        0 -> stringResource(R.string.now)
                        else -> arrivalTime.toString()
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.Light)) {
                when {
                    arrivalTime <= 0 -> Unit
                    arrivalTime == 1 -> {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.mins_singular),
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            stringResource(R.string.mins_plural_part),
                            color = Color.Transparent,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }

                    else -> {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.mins_plural),
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
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
            shortName = userState.shortenNames || hasVia,
        )
        if (hasVia) {
            Spacer(Modifier.width(5.dp))
            SingleTrainHeading(
                train.route,
                train.direction,
                shortName = true,
                isVia = true,
            )
        }
    }
}

@Composable
private fun SingleTrainHeading(
    route: Route,
    direction: Direction,
    shortName: Boolean = false,
    isVia: Boolean = false,
) {
    val station = if (isVia && route.via != null) {
        route.via
    } else {
        when (direction) {
            Direction.TO_NJ -> route.njTerminus
            Direction.TO_NY -> route.nyTerminus
        }
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
        color = pillColor,
    ) {
        Text(
            text = name,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
        )
    }
}

@Preview(name = "Light", widthDp = 250)
@Preview(name = "Dark", widthDp = 250, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrainShortNamesPreview(
    @PreviewParameter(SampleTrainPreviewProvider::class) train: UiUpcomingTrain,
) {
    PATHTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp.div(5)))
                .padding(5.dp),
        ) {
            Train(
                train = train,
                now = System.currentTimeMillis(),
                userState = UserState(
                    shortenNames = true,
                    showOppositeDirection = true,
                    showElevatorAlerts = true,
                    isInNJ = true,
                ),
            )
        }
    }
}

@Preview(name = "Light", widthDp = 250)
@Preview(name = "Dark", widthDp = 250, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrainLongNamesPreview(
    @PreviewParameter(SampleTrainPreviewProvider::class) train: UiUpcomingTrain,
) {
    PATHTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp.div(5)))
                .padding(5.dp),
        ) {
            Train(
                train = train,
                now = System.currentTimeMillis(),
                userState = UserState(
                    shortenNames = false,
                    showOppositeDirection = true,
                    showElevatorAlerts = true,
                    isInNJ = true,
                ),
            )
        }
    }
}

class SampleTrainPreviewProvider : PreviewParameterProvider<UiUpcomingTrain> {
    override val values = sequenceOf(
        UiUpcomingTrain(
            UpcomingTrain(
                route = Route.JSQ_33,
                direction = Direction.TO_NY,
                projectedArrival = Date().apply { time += 0.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 0,
            isInOppositeDirection = false,
        ),
        UiUpcomingTrain(
            UpcomingTrain(
                route = Route.NWK_WTC,
                direction = Direction.TO_NJ,
                projectedArrival = Date().apply { time += 1.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 1,
            isInOppositeDirection = false,
        ),
        UiUpcomingTrain(
            UpcomingTrain(
                route = Route.HOB_WTC,
                direction = Direction.TO_NJ,
                projectedArrival = Date().apply { time += 33.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 33,
            isInOppositeDirection = false,
        ),
        UiUpcomingTrain(
            UpcomingTrain(
                route = Route.JSQ_33_HOB,
                direction = Direction.TO_NJ,
                projectedArrival = Date().apply { time += 5.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 5,
            isInOppositeDirection = false,
        ),
    )
}
