package ca.amandeep.path.ui.alerts

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.amandeep.path.R
import ca.amandeep.path.data.model.AlertData
import ca.amandeep.path.ui.main.AlertsUiModel
import ca.amandeep.path.ui.theme.PATHTheme

@Composable
fun Alert(
    modifier: Modifier = Modifier,
    alert: AlertData,
) {
    Column(modifier) {
        Text(
            text = alert.incidentMessage.subject ?: stringResource(R.string.default_alert_title),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = MaterialTheme.typography.titleMedium.fontSize,
            fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = alert.incidentMessage.preMessage.orEmpty(),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
    }
}

@Composable
fun Alerts(
    modifier: Modifier = Modifier,
    alertsUiModel: AlertsUiModel,
) {
    Column(modifier) {
        for (alert in alertsUiModel) {
            Alert(
                modifier = Modifier.padding(vertical = 7.dp),
                alert = alert,
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
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(5.dp),
            alert = SampleAlertsPreviewProvider.ALERT1,
        )
    }
}
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlertsPreview() {
    PATHTheme {
        Alerts(
            modifier =
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(10.dp),
            alertsUiModel = listOf(
                SampleAlertsPreviewProvider.ALERT1,
                SampleAlertsPreviewProvider.ALERT2,
            ),
        )
    }
}
