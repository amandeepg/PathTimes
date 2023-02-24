package ca.amandeep.path.ui

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ca.amandeep.path.R
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.util.ConnectionState

/**
 * Show an error bar, designed to show with old but valid data, with an icon and a message,
 * differing depending on if there is an internet connection or not.
 */
@Composable
fun ErrorBar(
    connectivityState: ConnectionState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.CenterVertically)
            .background(color = MaterialTheme.colorScheme.errorContainer)
            .padding(10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                id = when (connectivityState) {
                    ConnectionState.Available -> R.drawable.ic_sync_error
                    ConnectionState.Unavailable -> R.drawable.ic_wifi_off
                },
            ),
            modifier = Modifier
                .size(20.dp)
                .alignByBaseline(),
            contentDescription = stringResource(R.string.error_icon),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(10.dp))
        Crossfade(targetState = connectivityState) {
            val errorText = when (it) {
                ConnectionState.Available -> stringResource(R.string.load_trains_error_long)
                ConnectionState.Unavailable -> stringResource(R.string.no_internet_connection)
            }
            Text(
                text = errorText + stringResource(R.string.stale_data_explanation),
                fontSize = MaterialTheme.typography.labelSmall.fontSize,
                lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
@Preview(name = "Light", showBackground = true, widthDp = 300)
@Preview(name = "Dark", showBackground = true, widthDp = 300, uiMode = UI_MODE_NIGHT_YES)
private fun ErrorBarPreview(
    @PreviewParameter(SampleConnectionStateProvider::class) connectivityState: ConnectionState,
) {
    PATHTheme { ErrorBar(connectivityState) }
}

class SampleConnectionStateProvider : PreviewParameterProvider<ConnectionState> {
    override val values = sequenceOf(
        ConnectionState.Available,
        ConnectionState.Unavailable,
    )
}
