package ca.amandeep.path.ui.alerts

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults.outlinedCardColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.amandeep.path.data.model.AlertData
import ca.amandeep.path.data.model.AlertDatas
import ca.amandeep.path.data.model.GroupedAlertData
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.ui.HEADING_DARK_TEXT_COLOR
import ca.amandeep.path.ui.HEADING_LIGHT_TEXT_COLOR
import ca.amandeep.path.ui.HOB_33_COLOR
import ca.amandeep.path.ui.HOB_WTC_COLOR
import ca.amandeep.path.ui.JSQ_33_COLOR
import ca.amandeep.path.ui.NWK_WTC_COLOR
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
    Column(modifier) {
        Text(
            text = alert.text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = alertTextStyle.fontSize,
            fontWeight = alertTextStyle.fontWeight,
            lineHeight = alertTextStyle.lineHeight,
        )
        if (alert.date != null) {
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    alert.date.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString().lowercase(Locale.US),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = timeTextStyle.fontSize,
                fontWeight = timeTextStyle.fontWeight,
                lineHeight = timeTextStyle.lineHeight,
            )
        }
    }
}

@Composable
fun GroupedAlert(
    groupedAlert: GroupedAlertData,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = outlinedCardColors(
            containerColor = Color.Gray.copy(alpha = 0.2f),
        ),
        border = BorderStroke(Dp.Hairline, Color.Gray.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
        ) {
            when (groupedAlert.title) {
                is GroupedAlertData.Title.RouteTitle ->
                    Row {
                        val route = groupedAlert.title.route
                        val name = when (route) {
                            Route.JSQ_33 -> "JSQ-33"
                            Route.HOB_33 -> "HOB-33"
                            Route.HOB_WTC -> "HOB-WTC"
                            Route.NWK_WTC -> "NWK-WTC"
                            Route.JSQ_33_HOB -> "JSQ-33 via HOB"
                        }
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
                                text = name,
                                color = textColor,
                                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                                fontWeight = MaterialTheme.typography.labelLarge.fontWeight,
                                lineHeight = MaterialTheme.typography.labelLarge.lineHeight,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(vertical = 2.dp, horizontal = 6.dp),
                            )
                        }

                        Text(
                            text = groupedAlert.title.text,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize,
                            fontWeight = MaterialTheme.typography.labelLarge.fontWeight,
                            lineHeight = MaterialTheme.typography.labelLarge.lineHeight,
                            modifier = Modifier
                                .padding(start = 7.dp)
                                .align(Alignment.CenterVertically),
                        )
                    }

                is GroupedAlertData.Title.FreeformTitle ->
                    Text(
                        text = groupedAlert.title.text,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        fontWeight = MaterialTheme.typography.labelLarge.fontWeight,
                        lineHeight = MaterialTheme.typography.labelLarge.lineHeight,
                    )

                else -> {}
            }
            Alert(
                modifier = Modifier.padding(vertical = 4.dp),
                alert = groupedAlert.alerts.first(),
                alertTextStyle = MaterialTheme.typography.bodyMedium,
                timeTextStyle = MaterialTheme.typography.labelSmall,
            )
            for (alert in groupedAlert.alerts.drop(1)) {
                Alert(
                    modifier = Modifier.padding(vertical = 2.dp),
                    alert = alert,
                    alertTextStyle = MaterialTheme.typography.bodySmall,
                    timeTextStyle = MaterialTheme.typography.labelSmall
                        .let { it.copy(fontSize = it.fontSize * 0.8f) },
                )
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
        for (alert in alertsUiModel.alerts) {
            Alert(
                modifier = Modifier.padding(vertical = 7.dp),
                alert = alert,
                alertTextStyle = MaterialTheme.typography.bodyMedium,
                timeTextStyle = MaterialTheme.typography.labelSmall,
            )
        }
        for (alert in alertsUiModel.groupedAlerts) {
            GroupedAlert(
                modifier = Modifier.padding(vertical = 7.dp),
                groupedAlert = alert,
            )
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
private fun GroupedAlertPreview() {
    PATHTheme {
        GroupedAlert(
            groupedAlert = SampleAlertsPreviewProvider.GROUPED_ALERT1,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(5.dp),
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
                groupedAlerts = listOf(
                    SampleAlertsPreviewProvider.GROUPED_ALERT1,
                    SampleAlertsPreviewProvider.GROUPED_ALERT2,
                ),
                alerts = listOf(
                    SampleAlertsPreviewProvider.ALERT1,
                    SampleAlertsPreviewProvider.ALERT2,
                ),
            ),
        )
    }
}
