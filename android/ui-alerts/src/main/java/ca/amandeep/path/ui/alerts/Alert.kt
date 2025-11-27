@file:OptIn(ExperimentalLayoutApi::class)

package ca.amandeep.path.ui.alerts

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.amandeep.path.data.AlertData
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.StationName
import ca.amandeep.path.data.model.displayName
import ca.amandeep.path.main.core.UserState
import ca.amandeep.path.strings.R
import ca.amandeep.ui.core.HEADING_DARK_TEXT_COLOR
import ca.amandeep.ui.core.HEADING_LIGHT_TEXT_COLOR
import ca.amandeep.ui.core.HOB_33_COLOR
import ca.amandeep.ui.core.HOB_WTC_COLOR
import ca.amandeep.ui.core.JSQ_33_COLOR
import ca.amandeep.ui.core.NWK_WTC_COLOR
import ca.amandeep.ui.core.collapsing.ExpandableView
import ca.amandeep.ui.core.collapsing.animateExpandingArrow
import ca.amandeep.ui.core.collapsing.expandableClickable
import ca.amandeep.ui.core.theme.PATHTheme
import ca.amandeep.ui.core.theme.PATH_BLUE
import ca.amandeep.ui.core.theme.PATH_ON_BLUE
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

@Composable
fun Alert(
    alert: AlertData,
    alertTextStyle: TextStyle,
    timeTextStyle: TextStyle,
    userState: UserState,
    modifier: Modifier = Modifier,
    setShowElevatorAlerts: (suspend (Boolean) -> Unit)? = null,
) {
    val titleTextStyle = alertTextStyle.copy(fontWeight = FontWeight.Medium)

    Column(modifier) {
        if (alert is AlertData.Grouped) {
            when (val alertTitle = alert.title) {
                is AlertData.Grouped.Title.RouteTitle -> {
                    FlowRow(
                        modifier = Modifier.padding(bottom = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        alertTitle.routes.forEachIndexed { index, route ->
                            SingleRoute(route, titleTextStyle)
                            if (index != alertTitle.routes.size - 1) {
                                Spacer(Modifier.width(2.dp))
                            }
                        }

                        if (alertTitle.routes.size == 1) {
                            alert.GroupedTitleText(
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .align(Alignment.CenterVertically),
                                titleTextStyle = titleTextStyle,
                            )
                        }
                    }
                    if (alertTitle.routes.size > 1) {
                        alert.GroupedTitleText(
                            modifier = Modifier.padding(bottom = 3.dp),
                            titleTextStyle = titleTextStyle,
                        )
                    }
                }

                is AlertData.Grouped.Title.StationTitle -> {
                    FlowRow(
                        modifier = Modifier.padding(bottom = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        alertTitle.stations.forEachIndexed { index, station ->
                            SingleStation(station, titleTextStyle)
                            if (index != alertTitle.stations.size - 1) {
                                Spacer(Modifier.width(2.dp))
                            }
                        }

                        if (alertTitle.stations.size == 1) {
                            alert.GroupedTitleText(
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .align(Alignment.CenterVertically),
                                titleTextStyle = titleTextStyle,
                            )
                        }
                    }
                    if (alertTitle.stations.size > 1) {
                        alert.GroupedTitleText(
                            modifier = Modifier.padding(bottom = 3.dp),
                            titleTextStyle = titleTextStyle,
                        )
                    }
                }

                is AlertData.Grouped.Title.FreeformTitle -> {
                    alert.GroupedTitleText(
                        titleTextStyle = titleTextStyle,
                    )
                }

                else -> {
                    Unit
                }
            }
        }
        val singleAlert = alert.asSingleAlert(userState)
        val singleAlertText = singleAlert.text
        if (!singleAlertText.isNullOrBlank()) {
            Text(
                text = singleAlertText,
                color = MaterialTheme.colorScheme.onBackground,
                style = if (alert is AlertData.Grouped) alertTextStyle else titleTextStyle,
            )
        }
        val singleAlertDate = singleAlert.date
        if (singleAlertDate != null) {
            @Composable
            fun DateText() = Text(
                text = DateUtils
                    .getRelativeTimeSpanString(
                        singleAlertDate.time, // time
                        System.currentTimeMillis(), // now
                        DateUtils.MINUTE_IN_MILLIS, // minResolution
                    ).toString()
                    .lowercase(Locale.US),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                style = timeTextStyle,
            )

            @Composable
            fun Dot() = Text(
                text = " · ",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                style = timeTextStyle,
            )
            if (alert is AlertData.Grouped && alert.history.isNotEmpty()) {
                val (expanded, setExpanded) = remember { mutableStateOf(false) }
                val arrowRotationDegree by animateExpandingArrow(expanded)

                Row {
                    DateText()
                    Dot()
                    val alertTitle = alert.title
                    Text(
                        modifier = Modifier
                            .expandableClickable(onClick = { setExpanded(!expanded) })
                            .alpha(0.6f),
                        text = when (alertTitle) {
                            is AlertData.Grouped.Title.RouteTitle -> {
                                stringResource(
                                    R.string.view_older_route,
                                    alertTitle.routes.joinToString { it.displayName },
                                )
                            }

                            else -> {
                                stringResource(R.string.view_older)
                            }
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = timeTextStyle,
                    )
                    Icon(
                        modifier = Modifier
                            .size(
                                with(LocalDensity.current) {
                                    timeTextStyle.fontSize.toDp()
                                },
                            ).align(Alignment.CenterVertically)
                            .rotate(arrowRotationDegree),
                        imageVector = Icons.Filled.ExpandLess,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = stringResource(R.string.expandable_arrow_content_description),
                    )
                }
                ExpandableView(
                    isExpanded = expanded,
                ) {
                    Column(
                        modifier = Modifier.padding(top = 7.dp),
                    ) {
                        alert.history.forEachIndexed { index, histAlert ->
                            Alert(
                                alert = histAlert,
                                alertTextStyle = alertTextStyle.let {
                                    it.copy(
                                        fontSize = it.fontSize * 0.85f,
                                        lineHeight = it.lineHeight * 0.8f,
                                    )
                                },
                                timeTextStyle = timeTextStyle
                                    .let { it.copy(fontSize = it.fontSize * 0.85f) },
                                userState = userState,
                                setShowElevatorAlerts = setShowElevatorAlerts,
                            )
                            if (index != alert.history.size - 1) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            } else if (alert.isElevator) {
                Row {
                    DateText()
                    if (setShowElevatorAlerts != null) {
                        Dot()
                        Text(
                            modifier = Modifier
                                .expandableClickable(onClick = { setShowElevatorAlerts(false) })
                                .alpha(0.6f),
                            text = stringResource(R.string.hide_elevator_alerts),
                            color = MaterialTheme.colorScheme.primary,
                            style = timeTextStyle,
                        )
                    }
                }
            } else if (!singleAlert.text.isNullOrBlank() || alert is AlertData.Grouped) {
                DateText()
            }
        }
    }
}

@Composable
private fun AlertData.asSingleAlert(
    userState: UserState,
): AlertData.Single {
    val singleAlert = when (this) {
        is AlertData.Single -> this

        is AlertData.GroupedRaw -> main

        is AlertData.GroupedWithLlm -> main.let { alertMain ->
            if (!userState.debugOptions.aiSummarizeAlerts) {
                return@let original.asSingleAlert(userState)
            }

            val modelStrSimple = modelName.lowercase().let {
                when {
                    "o3-mini" in it -> "o3m"
                    "us.meta.llama3" in it -> "l3"
                    "gpt-4o" in it -> "4o"
                    "haiku" in it -> "haiku"
                    "gemini-2.0-flash" in it -> "g2f"
                    "chat-v3" in it -> "v3"
                    "deepseek-r1" in it -> "r1"
                    "scout" in it -> "scout"
                    "maverick" in it -> "maverick"
                    "gemini-2.5-pro" in it -> "g25p"
                    "quasar-alpha" in it -> "qa"
                    else -> it
                }
            }
            alertMain.copy(
                text = listOfNotNull(
                    "✦",
                    "($modelStrSimple)".takeIf { userState.debugOptions.showModelName },
                    alertMain.text,
                ).joinToString(" "),
            )
        }

        else -> throw IllegalArgumentException()
    }
    return singleAlert
}

@Composable
private fun AlertData.Grouped.GroupedTitleText(
    titleTextStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (title?.text.isNullOrBlank()) {
        return
    }
    Text(
        text = title?.text!!,
        color = MaterialTheme.colorScheme.onBackground,
        style = titleTextStyle,
        modifier = modifier,
    )
}

@Composable
private fun RowScope.SingleRoute(
    route: Route,
    style: TextStyle,
) {
    val pillColor = when (route) {
        Route.JSQ_33 -> JSQ_33_COLOR
        Route.HOB_33 -> HOB_33_COLOR
        Route.HOB_WTC -> HOB_WTC_COLOR
        Route.NWK_WTC -> NWK_WTC_COLOR
        Route.JSQ_33_HOB -> JSQ_33_COLOR
    }
    val textColor = when (route) {
        Route.JSQ_33 -> HEADING_DARK_TEXT_COLOR
        Route.HOB_33 -> HEADING_LIGHT_TEXT_COLOR
        Route.HOB_WTC -> HEADING_LIGHT_TEXT_COLOR
        Route.NWK_WTC -> HEADING_LIGHT_TEXT_COLOR
        Route.JSQ_33_HOB -> HEADING_DARK_TEXT_COLOR
    }
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = pillColor,
    ) {
        Text(
            text = route.displayName,
            color = textColor,
            style = style,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(vertical = 0.5.dp, horizontal = 5.dp),
        )
    }
}

@Composable
private fun RowScope.SingleStation(
    station: StationName,
    style: TextStyle,
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = PATH_BLUE,
    ) {
        Text(
            text = station.longName,
            color = PATH_ON_BLUE,
            style = style,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(vertical = 0.5.dp, horizontal = 5.dp),
        )
    }
}

@Composable
fun Alerts(
    alerts: ImmutableList<AlertData>,
    userState: UserState,
    modifier: Modifier = Modifier,
    setShowElevatorAlerts: (suspend (Boolean) -> Unit)? = null,
) {
    Column(modifier) {
        alerts
            .forEachIndexed { index, alert ->
                Alert(
                    alert = alert,
                    alertTextStyle = MaterialTheme.typography.bodyMedium,
                    timeTextStyle = MaterialTheme.typography.labelSmall,
                    userState = userState,
                    setShowElevatorAlerts = setShowElevatorAlerts,
                )
                if (index != alerts.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = DividerDefaults.color.copy(alpha = 0.5f),
                    )
                }
            }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlertPreview() {
    PATHTheme {
        Alert(
            alert = SampleAlertsPreviewProvider.ALERT1,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(5.dp),
            alertTextStyle = MaterialTheme.typography.bodyMedium,
            timeTextStyle = MaterialTheme.typography.labelSmall,
            userState = UserState(),
            setShowElevatorAlerts = {},
        )
    }
}

@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
private fun AlertsPreview() {
    PATHTheme {
        Alerts(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(10.dp),
            alerts = persistentListOf(
                SampleAlertsPreviewProvider.ALERT1,
                SampleAlertsPreviewProvider.GROUPED_MANY_STATION_ALERT1,
                SampleAlertsPreviewProvider.GROUPED_MANY_LINE_ALERT1,
                SampleAlertsPreviewProvider.GROUPED_MANY_LINE_ALERT2,
                SampleAlertsPreviewProvider.GROUPED_ALERT1,
                SampleAlertsPreviewProvider.ALERT2,
                SampleAlertsPreviewProvider.GROUPED_ALERT2,
            ),
            userState = UserState(),
            setShowElevatorAlerts = {},
        )
    }
}
