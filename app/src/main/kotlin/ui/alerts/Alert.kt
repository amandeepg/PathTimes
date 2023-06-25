package ca.amandeep.path.ui.alerts

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.amandeep.path.R
import ca.amandeep.path.data.model.AlertData
import ca.amandeep.path.data.model.AlertDatas
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
import java.util.Locale

@Composable
fun Alert(
    alert: AlertData,
    alertTextStyle: TextStyle,
    timeTextStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val singleAlert = when (alert) {
        is AlertData.Single -> alert
        is AlertData.Grouped -> alert.main
        else -> throw IllegalArgumentException()
    }

    Column(modifier) {
        if (alert is AlertData.Grouped) {
            when (alert.title) {
                is AlertData.Grouped.Title.RouteTitle ->
                    Row(modifier = Modifier.padding(bottom = 3.dp)) {
                        val route = alert.title.route
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
                                fontSize = alertTextStyle.fontSize,
                                fontWeight = FontWeight.Bold,
                                lineHeight = alertTextStyle.lineHeight,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(vertical = 0.5.dp, horizontal = 5.dp),
                            )
                        }

                        Text(
                            text = alert.title.text,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = alertTextStyle.fontSize,
                            fontWeight = alertTextStyle.fontWeight,
                            lineHeight = alertTextStyle.lineHeight,
                            modifier = Modifier
                                .padding(start = 5.dp)
                                .align(Alignment.CenterVertically),
                        )
                    }

                is AlertData.Grouped.Title.FreeformTitle ->
                    Text(
                        text = alert.title.text,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = alertTextStyle.fontSize,
                        fontWeight = alertTextStyle.fontWeight,
                        lineHeight = alertTextStyle.lineHeight,
                    )

                else -> Unit
            }
        }
        Text(
            text = singleAlert.text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = alertTextStyle.fontSize,
            fontWeight = alertTextStyle.fontWeight,
            lineHeight = alertTextStyle.lineHeight,
        )
        if (singleAlert.date != null) {
            @Composable
            fun DateText() = Text(
                text = DateUtils.getRelativeTimeSpanString(
                    /* time = */
                    singleAlert.date.time,
                    /* now = */
                    System.currentTimeMillis(),
                    /* minResolution = */
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString().lowercase(Locale.US),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = timeTextStyle.fontSize,
                fontWeight = timeTextStyle.fontWeight,
                lineHeight = timeTextStyle.lineHeight,
            )
            if (alert is AlertData.Grouped) {
                val (expanded, setExpanded) = remember { mutableStateOf(false) }
                val arrowRotationDegree by animateExpandingArrow(expanded)

                Row {
                    DateText()
                    Text(
                        text = " · ",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        fontSize = timeTextStyle.fontSize,
                        fontWeight = timeTextStyle.fontWeight,
                        lineHeight = timeTextStyle.lineHeight,
                    )
                    Text(
                        modifier = Modifier
                            .expandableClickable(onClick = { setExpanded(!expanded) })
                            .alpha(0.6f),
                        text = when (alert.title) {
                            is AlertData.Grouped.Title.RouteTitle ->
                                stringResource(
                                    R.string.view_older_route,
                                    alert.title.route.displayName,
                                )
                            else ->
                                stringResource(R.string.view_older)
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = timeTextStyle.fontSize,
                        fontWeight = timeTextStyle.fontWeight,
                        lineHeight = timeTextStyle.lineHeight,
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
                        painter = painterResource(id = R.drawable.ic_expand_less),
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
                            )
                            if (index != alert.history.size - 1) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            } else {
                DateText()
            }
        }
    }
}

@Composable
fun Alerts(
    alertsUiModel: AlertsUiModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        alertsUiModel.alerts.forEachIndexed { index, alert ->
            Alert(
                alert = alert,
                alertTextStyle = MaterialTheme.typography.bodyMedium,
                timeTextStyle = MaterialTheme.typography.labelSmall,
            )
            if (index != alertsUiModel.size - 1) {
                Divider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = Dp.Hairline,
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
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlertsPreview() {
    PATHTheme {
        Alerts(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(10.dp),
            alertsUiModel = AlertDatas(
                alerts = listOf(
                    SampleAlertsPreviewProvider.ALERT1,
                    SampleAlertsPreviewProvider.GROUPED_ALERT1,
                    SampleAlertsPreviewProvider.ALERT2,
                    SampleAlertsPreviewProvider.GROUPED_ALERT2,
                ),
            ),
        )
    }
}
