package ca.amandeep.path.ui.alerts

import android.content.res.Configuration
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.amandeep.path.data.model.AlertData
import ca.amandeep.path.ui.main.AlertsUiModel
import ca.amandeep.path.ui.theme.PATHTheme
import java.util.Locale

@Composable
fun Alert(
    modifier: Modifier = Modifier,
    alert: AlertData,
) {
    Column(modifier) {
        Text(
            text = alert.text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = MaterialTheme.typography.bodyMedium.fontWeight,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
        )
        if (alert.date != null) {
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    /* time = */ alert.date.time,
                    /* now = */ System.currentTimeMillis(),
                    /* minResolution = */ DateUtils.MINUTE_IN_MILLIS,
                ).toString().lowercase(Locale.US),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
            )
        }
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
            alerts = listOf(
                SampleAlertsPreviewProvider.ALERT1,
                SampleAlertsPreviewProvider.ALERT2,
            ),
        )
    }
}
