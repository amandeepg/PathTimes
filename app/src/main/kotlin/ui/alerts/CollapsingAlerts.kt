package ca.amandeep.path.ui.alerts

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ca.amandeep.path.R
import ca.amandeep.path.data.AlertData
import ca.amandeep.path.data.AlertDatas
import ca.amandeep.path.data.model.Route
import ca.amandeep.path.ui.HEADING_LIGHT_TEXT_COLOR
import ca.amandeep.path.ui.NWK_WTC_COLOR
import ca.amandeep.path.ui.collapsing.ExpandableContainerView
import ca.amandeep.path.ui.collapsing.animateExpandingArrow
import ca.amandeep.path.ui.main.AlertsUiModel
import ca.amandeep.path.ui.main.Result
import ca.amandeep.path.ui.main.asValid
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.util.ConnectionState
import kotlinx.collections.immutable.persistentListOf
import java.util.Date
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableAlerts(
    connectivityState: ConnectionState,
    alertsResult: Result<AlertsUiModel>,
    setExpanded: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    setShowElevatorAlerts: (Boolean) -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        ExpandableContainerView(
            modifier = Modifier
                .background(alertsResult.backgroundColor())
                .padding(4.dp),
            expanded = expanded,
            onClickHeader = { setExpanded(!expanded) },
            headerContent = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        alertsResults = alertsResult,
                        connectivityState = connectivityState,
                        expanded = expanded,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        AlertsTitle(alertsResult)
                        if (alertsResult is Result.Valid && !alertsResult.data.isEmpty()) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Badge(
                                modifier = Modifier.align(Alignment.CenterVertically),
                                containerColor = NWK_WTC_COLOR,
                                contentColor = HEADING_LIGHT_TEXT_COLOR,
                            ) {
                                Text(
                                    text = "${alertsResult.data.size}",
                                    color = HEADING_LIGHT_TEXT_COLOR,
                                )
                            }
                        }
                    }
                }
            },
            expandableContent = {
                Alerts(
                    modifier = Modifier.padding(horizontal = 5.dp),
                    alertsUiModel = alertsResult.asValid()?.data ?: AlertDatas(),
                    setShowElevatorAlerts = setShowElevatorAlerts,
                )
            },
        )
    }
}

@Composable
private fun AlertsTitle(alertsResults: Result<AlertsUiModel>) {
    Text(
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleMedium,
        text = when (alertsResults) {
            is Result.Error -> stringResource(R.string.couldn_t_load_path_alerts)
            is Result.Loading -> stringResource(R.string.loading_path_alerts)
            is Result.Valid ->
                if (alertsResults.data.isEmpty()) {
                    stringResource(R.string.no_path_alerts)
                } else {
                    stringResource(R.string.path_alerts_title)
                }
        },
    )
}

@Composable
private fun BoxScope.Icon(
    alertsResults: Result<AlertsUiModel>,
    connectivityState: ConnectionState,
    expanded: Boolean,
) {
    val arrowRotationDegree by animateExpandingArrow(expanded)

    Modifier.size(24.dp)
        .align(Alignment.CenterStart)
        .let { modifier ->
            when (alertsResults) {
                is Result.Loading -> CircularProgressIndicator(
                    modifier = modifier.padding(5.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    strokeWidth = ProgressIndicatorDefaults.CircularStrokeWidth / 2f,
                )

                is Result.Error -> Icon(
                    imageVector = when (connectivityState) {
                        ConnectionState.Available -> Icons.Filled.SyncProblem
                        ConnectionState.Unavailable -> Icons.Filled.SignalWifiOff
                    },
                    modifier = modifier.padding(2.dp),
                    contentDescription = stringResource(R.string.error_icon),
                    tint = MaterialTheme.colorScheme.onBackground,
                )

                is Result.Valid -> if (alertsResults.data.isEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        modifier = modifier,
                        contentDescription = stringResource(R.string.no_alerts_icon),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                } else {
                    ArrowIcon(
                        modifier = modifier,
                        degrees = arrowRotationDegree,
                    )
                }
            }
        }
}

val NO_ALERTS_COLOR = Color(0, 200, 83).copy(alpha = 0.5f)
val HAS_ALERTS_COLOR = Color(253, 216, 53).copy(alpha = 0.3f)

@Composable
private fun Result<AlertsUiModel>.backgroundColor() =
    when (this) {
        is Result.Error -> MaterialTheme.colorScheme.errorContainer
        is Result.Loading -> Color.Transparent
        is Result.Valid ->
            if (data.isEmpty()) {
                NO_ALERTS_COLOR
            } else {
                HAS_ALERTS_COLOR
            }
    }

@Composable
private fun ArrowIcon(
    degrees: Float,
    modifier: Modifier = Modifier,
) {
    Icon(
        modifier = modifier.rotate(degrees),
        imageVector = Icons.Filled.ExpandLess,
        tint = MaterialTheme.colorScheme.onBackground,
        contentDescription = stringResource(R.string.expandable_arrow_content_description),
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CollapsingAlertsPreview(
    @PreviewParameter(SampleAlertsPreviewProvider::class) alertsResult: Result<AlertsUiModel>,
) {
    PATHTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(10.dp),
        ) {
            val defaultExpanded = alertsResult.asValid()?.data?.alerts.orEmpty().size > 1
            val (expanded, setExpanded) = remember { mutableStateOf<Boolean?>(null) }
            ExpandableAlerts(
                connectivityState = ConnectionState.Unavailable,
                alertsResult = alertsResult,
                expanded = expanded ?: defaultExpanded,
                setExpanded = setExpanded,
                setShowElevatorAlerts = {},
            )
        }
    }
}

class SampleAlertsPreviewProvider : PreviewParameterProvider<Result<AlertsUiModel>> {
    companion object {
        val ALERT1 = AlertData.Single(
            date = Date().apply { time -= 7.minutes.inWholeMilliseconds },
            text = "JSQ-33 via HOB delayed. " +
                "Train experiencing network communication problems at JSQ. An update will be issued in approx. 15 mins.",
        )
        val ALERT2 = AlertData.Single(
            date = Date().apply { time -= 12.minutes.inWholeMilliseconds },
            text = "At JSQ, concourse elevator connecting platform with trks 1&2 out of service. " +
                "Please call 1-800-234-PATH for assistance or use the Pax Assistance Phone if no agent is available. " +
                "We regret this inconvenience.",
        )
        val GROUPED_ALERT1 = AlertData.Grouped(
            title = AlertData.Grouped.Title.RouteTitle(
                persistentListOf(Route.NWK_WTC, Route.HOB_WTC),
                "delayed",
            ),
            main = AlertData.Single(
                "Trains moving again.",
                date = Date().apply { time -= 4.minutes.inWholeMilliseconds },
            ),
            history = persistentListOf(
                AlertData.Single(
                    "Bird has been saved. Update in 15 mins.",
                    date = Date().apply { time -= 16.minutes.inWholeMilliseconds },
                ),
                AlertData.Single(
                    "Crew reported a bird. Update in 10 mins.",
                    date = Date().apply { time -= 24.minutes.inWholeMilliseconds },
                ),
            ),
        )
        val GROUPED_ALERT2 = AlertData.Grouped(
            title = AlertData.Grouped.Title.FreeformTitle("Bird incident"),
            main = AlertData.Single(
                "Trains moving again.",
                date = Date().apply { time -= 3.minutes.inWholeMilliseconds },
            ),
            history = persistentListOf(
                AlertData.Single(
                    "Bird has been saved. Update in 15 mins.",
                    date = Date().apply { time -= 17.minutes.inWholeMilliseconds },
                ),
                AlertData.Single(
                    "Crew reported a bird. Update in 10 mins.",
                    date = Date().apply { time -= 23.minutes.inWholeMilliseconds },
                ),
            ),
        )
    }

    override val values = sequenceOf(
        Result.Loading(),
        Result.Error(),
        Result.Valid(lastUpdated = 0, AlertDatas()),
        Result.Valid(lastUpdated = 0, AlertDatas(), hasError = true),
        Result.Valid(
            lastUpdated = System.currentTimeMillis(),
            data = AlertDatas(
                alerts = persistentListOf(ALERT1, GROUPED_ALERT1, ALERT2, GROUPED_ALERT2),
            ),
        ),
        Result.Valid(
            lastUpdated = System.currentTimeMillis(),
            data = AlertDatas(
                alerts = persistentListOf(ALERT1),
            ),
        ),
    )
}
