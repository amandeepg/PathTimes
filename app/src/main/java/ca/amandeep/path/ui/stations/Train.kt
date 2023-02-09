package ca.amandeep.path.ui.stations


import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.amandeep.path.Direction
import ca.amandeep.path.Route
import ca.amandeep.path.RouteStation
import ca.amandeep.path.UpcomingTrain
import ca.amandeep.path.data.MainUseCase
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.ui.theme.surfaceColorAtElevation
import java.util.*

/**
 * A single train with heading and next arrival time.
 */
@Composable
fun Train(
    train: MainUseCase.Result.UiUpcomingTrain,
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

        Crossfade(targetState = train.arrivalInMinutesFromNow) {
            Row {
                Text(
                    when (it) {
                        0 -> "now"
                        else -> it.toString()
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    modifier = Modifier.alignByBaseline()
                )
                ProvideTextStyle(TextStyle(fontWeight = FontWeight.Light)) {
                    when (it) {
                        0 -> Unit
                        1 -> {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "min",
                                modifier = Modifier.alignByBaseline()
                            )
                            Text(
                                "s", color = Color.Transparent,
                                modifier = Modifier.alignByBaseline()
                            )
                        }
                        else -> {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "mins",
                                modifier = Modifier.alignByBaseline()
                            )
                        }
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

@Preview(name = "Light", widthDp = 250)
@Preview(name = "Dark", widthDp = 250, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrainShortNamesPreview(
    @PreviewParameter(SampleTrainPreviewProvider::class) train: MainUseCase.Result.UiUpcomingTrain,
) {
    PATHTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp.div(5)))
                .padding(5.dp)
        ) {
            Train(
                train = train,
                userState = UserState(
                    shortenNames = true,
                    showOppositeDirection = true,
                    isInNJ = true
                )
            )
        }
    }
}

@Preview(name = "Light", widthDp = 250)
@Preview(name = "Dark", widthDp = 250, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrainLongNamesPreview(
    @PreviewParameter(SampleTrainPreviewProvider::class) train: MainUseCase.Result.UiUpcomingTrain,
) {
    PATHTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp.div(5)))
                .padding(5.dp)
        ) {
            Train(
                train = train,
                userState = UserState(
                    shortenNames = false,
                    showOppositeDirection = true,
                    isInNJ = true
                )
            )
        }
    }
}

class SampleTrainPreviewProvider : PreviewParameterProvider<MainUseCase.Result.UiUpcomingTrain> {
    override val values = sequenceOf(
        MainUseCase.Result.UiUpcomingTrain(
            UpcomingTrain(
                route = Route.JSQ_33,
                direction = Direction.TO_NY,
                projectedArrival = Date(),
            ),
            arrivalInMinutesFromNow = 0,
            isInOppositeDirection = false
        ),
        MainUseCase.Result.UiUpcomingTrain(
            UpcomingTrain(
                route = Route.NWK_WTC,
                direction = Direction.TO_NJ,
                projectedArrival = Date(),
            ),
            arrivalInMinutesFromNow = 1,
            isInOppositeDirection = false
        ),
        MainUseCase.Result.UiUpcomingTrain(
            UpcomingTrain(
                route = Route.HOB_WTC,
                direction = Direction.TO_NJ,
                projectedArrival = Date(),
            ),
            arrivalInMinutesFromNow = 33,
            isInOppositeDirection = false
        ),
        MainUseCase.Result.UiUpcomingTrain(
            UpcomingTrain(
                route = Route.JSQ_33_HOB,
                direction = Direction.TO_NJ,
                projectedArrival = Date(),
            ),
            arrivalInMinutesFromNow = 5,
            isInOppositeDirection = false
        ),
    )
}