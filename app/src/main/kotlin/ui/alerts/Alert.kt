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
import ca.amandeep.path.R
import ca.amandeep.path.data.AlertData
import ca.amandeep.path.data.AlertDatas
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.data.model.displayName
import ca.amandeep.path.ui.HEADING_DARK_TEXT_COLOR
import ca.amandeep.path.ui.HEADING_LIGHT_TEXT_COLOR
import ca.amandeep.path.ui.HOB_33_COLOR
import ca.amandeep.path.ui.HOB_WTC_COLOR
import ca.amandeep.path.ui.JSQ_33_COLOR
import ca.amandeep.path.ui.NWK_WTC_COLOR
import ca.amandeep.path.ui.collapsing.ExpandableView
import ca.amandeep.path.ui.collapsing.animateExpandingArrow
import ca.amandeep.path.ui.collapsing.expandableClickable
import ca.amandeep.path.ui.main.AlertsUiModel
import ca.amandeep.path.ui.theme.PATHTheme
import kotlinx.collections.immutable.persistentListOf
import java.util.Locale

@Composable
fun Alert(
    alert: AlertData,
    alertTextStyle: TextStyle,
    timeTextStyle: TextStyle,
    setShowElevatorAlerts: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleTextStyle = alertTextStyle.copy(fontWeight = FontWeight.Medium)

    val singleAlert = when (alert) {
        is AlertData.Single -> alert
        is AlertData.Grouped -> alert.main
        else -> throw IllegalArgumentException()
    }

    Column(modifier) {
        if (alert is AlertData.Grouped) {
            when (alert.title) {
                is AlertData.Grouped.Title.RouteTitle -> {
                    FlowRow(
                        modifier = Modifier.padding(bottom = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        alert.title.routes.forEachIndexed { index, route ->
                            SingleRoute(route, titleTextStyle)
                            if (index != alert.title.routes.size - 1) {
                                Spacer(Modifier.width(2.dp))
                            }
                        }

                        if (alert.title.routes.size == 1) {
                            alert.GroupedTitleText(
                                modifier = Modifier
                                    .padding(start = 5.dp)
                                    .align(Alignment.CenterVertically),
                                titleTextStyle = titleTextStyle,
                            )
                        }
                    }
                    if (alert.title.routes.size > 1) {
                        alert.GroupedTitleText(
                            modifier = Modifier.padding(bottom = 3.dp),
                            titleTextStyle = titleTextStyle,
                        )
                    }
                }

                is AlertData.Grouped.Title.FreeformTitle ->
                    alert.GroupedTitleText(
                        titleTextStyle = titleTextStyle,
                    )

                else -> Unit
            }
        }
        if (singleAlert.text.isNotEmpty()) {
            Text(
                text = singleAlert.text,
                color = MaterialTheme.colorScheme.onBackground,
                style = if (alert is AlertData.Grouped) alertTextStyle else titleTextStyle,
            )
        }
        if (singleAlert.date != null) {
            @Composable
            fun DateText() = Text(
                text = DateUtils.getRelativeTimeSpanString(
                    singleAlert.date.time, // time
                    System.currentTimeMillis(), // now
                    DateUtils.MINUTE_IN_MILLIS, // minResolution
                ).toString().lowercase(Locale.US),
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
                    Text(
                        modifier = Modifier
                            .expandableClickable(onClick = { setExpanded(!expanded) })
                            .alpha(0.6f),
                        text = when (alert.title) {
                            is AlertData.Grouped.Title.RouteTitle ->
                                stringResource(
                                    R.string.view_older_route,
                                    alert.title.routes.joinToString { it.displayName },
                                )
                            else ->
                                stringResource(R.string.view_older)
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
                            )
                            .align(Alignment.CenterVertically)
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
                                setShowElevatorAlerts = setShowElevatorAlerts,
                            )
                            if (index != alert.history.size - 1) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            } else if (alert is AlertData.Single && alert.isElevator) {
                Row {
                    DateText()
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
            } else if (singleAlert.text.isNotEmpty() || alert is AlertData.Grouped) {
                DateText()
            }
        }
    }
}

@Composable
private fun AlertData.Grouped.GroupedTitleText(
    titleTextStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.text,
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
fun Alerts(
    alertsUiModel: AlertsUiModel,
    modifier: Modifier = Modifier,
    setShowElevatorAlerts: (Boolean) -> Unit,
) {
    Column(modifier) {
        alertsUiModel.alerts
            .forEachIndexed { index, alert ->
                Alert(
                    alert = alert,
                    alertTextStyle = MaterialTheme.typography.bodyMedium,
                    timeTextStyle = MaterialTheme.typography.labelSmall,
                    setShowElevatorAlerts = setShowElevatorAlerts,
                )
                if (index != alertsUiModel.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = DividerDefaults.color.copy(alpha = 0.5f),
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
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
            alertsUiModel = AlertDatas(
                alerts = persistentListOf(
                    SampleAlertsPreviewProvider.ALERT1,
                    SampleAlertsPreviewProvider.GROUPED_MANY_LINE_ALERT1,
                    SampleAlertsPreviewProvider.GROUPED_MANY_LINE_ALERT2,
                    SampleAlertsPreviewProvider.GROUPED_ALERT1,
                    SampleAlertsPreviewProvider.ALERT2,
                    SampleAlertsPreviewProvider.GROUPED_ALERT2,
                ),
            ),
            setShowElevatorAlerts = {},
        )
    }
}
