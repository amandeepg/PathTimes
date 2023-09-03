@file:Suppress("PrivatePropertyName")

package ca.amandeep.path.ui.stations

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
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
import ca.amandeep.path.data.model.AlertData
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
import ca.amandeep.path.ui.alerts.Alert
import ca.amandeep.path.ui.alerts.HAS_ALERTS_COLOR
import ca.amandeep.path.ui.collapsing.ExpandableView
import ca.amandeep.path.ui.collapsing.animateExpandingArrow
import ca.amandeep.path.ui.collapsing.expandableClickable
import ca.amandeep.path.ui.main.UiUpcomingTrain
import ca.amandeep.path.ui.main.UserState
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.ui.theme.surfaceColorAtElevation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.text.Typography.nbsp
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
    isLastInStation: Boolean = false,
) {
    val (alertsExpanded, setAlertsExpanded) = remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrainMainRowContent(
                userState = userState,
                train = train,
                now = now,
                autoRefreshingNow = autoRefreshingNow,
                alertsExpanded = alertsExpanded,
                setAlertsExpanded = setAlertsExpanded,
            )
        }
        if (train.alerts.isNotEmpty()) {
            ExpandableView(
                isExpanded = alertsExpanded,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = HAS_ALERTS_COLOR,
                    modifier = Modifier
                        .padding(
                            top = 8.dp,
                            bottom = if (isLastInStation) 0.dp else 8.dp,
                        )
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        ExpandableTrainAlerts(train.alerts)
                    }
                }
            }
        }
        if (train.showDirectionHelpText) {
            Row(
                Modifier
                    .alpha(0.75f)
                    .padding(top = 2.dp, bottom = if (isLastInStation) 0.dp else 4.dp),
            ) {
                Icon(
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(90f)
                        .align(Alignment.Top)
                        .offset(x = (-3.5).dp),
                    imageVector = Icons.Rounded.SubdirectoryArrowLeft,
                    contentDescription = stringResource(R.string.arrow_pointing_to_direction_indicator),
                )
                Text(
                    modifier =
                    Modifier.align(Alignment.CenterVertically),
                    text = when (train.upcomingTrain.direction) {
                        Direction.TO_NY -> stringResource(R.string.east_bound_help_text, nbsp)
                        Direction.TO_NJ -> stringResource(R.string.west_bound_help_text, nbsp)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize.times(0.95f),
                        lineHeight = MaterialTheme.typography.labelSmall.lineHeight.times(0.9f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ExpandableTrainAlerts(alerts: ImmutableList<AlertData.Grouped>) {
    alerts.forEachIndexed { index, alert ->
        Alert(
            alert = alert,
            alertTextStyle = MaterialTheme.typography.bodyMedium,
            timeTextStyle = MaterialTheme.typography.labelSmall,
            setShowElevatorAlerts = {},
        )
        if (index != alerts.size - 1) {
            Divider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = DividerDefaults.color.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun RowScope.TrainMainRowContent(
    userState: UserState,
    train: UiUpcomingTrain,
    now: Long,
    autoRefreshingNow: Boolean,
    setAlertsExpanded: (Boolean) -> Unit,
    alertsExpanded: Boolean = false,
) {
    if (userState.showOppositeDirection) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .offset(x = (-4).dp),
        ) {
            Icon(
                modifier = Modifier
                    .size(22.dp)
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

    if (train.alerts.isNotEmpty()) {
        val arrowRotationDegree by animateExpandingArrow(alertsExpanded)
        Surface(
            shape = RoundedCornerShape(5.dp),
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 5.dp)
                .expandableClickable { setAlertsExpanded(!alertsExpanded) },
            color = HAS_ALERTS_COLOR,
            contentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 3.dp),
            ) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(20.dp)
                        .rotate(arrowRotationDegree),
                    imageVector = Icons.Filled.ExpandLess,
                    contentDescription = stringResource(R.string.expandable_arrow_content_description),
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(vertical = 3.dp)
                        .size(20.dp)
                        .offset(x = (-3).dp),
                    contentDescription = stringResource(R.string.alerts_on_this_route),
                )
            }
        }
    }
    TrainHeading(train.upcomingTrain, userState)
    Spacer(modifier = Modifier.Companion.weight(1f))

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
                _route = Route.JSQ_33,
                direction = Direction.TO_NY,
                projectedArrival = Date().apply { time += 0.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 0,
            isInOppositeDirection = false,
            showDirectionHelpText = true,
        ),
        UiUpcomingTrain(
            UpcomingTrain(
                _route = Route.NWK_WTC,
                direction = Direction.TO_NJ,
                projectedArrival = Date().apply { time += 1.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 1,
            isInOppositeDirection = false,
            alerts = persistentListOf(
                AlertData.Grouped(
                    title = AlertData.Grouped.Title.RouteTitle(
                        routes = persistentListOf(Route.NWK_WTC),
                        text = "delayed",
                    ),
                    main = AlertData.Single(text = "Where is this train going", date = null),
                ),
            ),
            showDirectionHelpText = true,
        ),
        UiUpcomingTrain(
            UpcomingTrain(
                _route = Route.HOB_WTC,
                direction = Direction.TO_NJ,
                projectedArrival = Date().apply { time += 33.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 33,
            isInOppositeDirection = false,
        ),
        UiUpcomingTrain(
            UpcomingTrain(
                _route = Route.JSQ_33_HOB,
                direction = Direction.TO_NJ,
                projectedArrival = Date().apply { time += 5.minutes.inWholeMilliseconds },
            ),
            arrivalInMinutesFromNow = 5,
            isInOppositeDirection = false,
        ),
    )
}
