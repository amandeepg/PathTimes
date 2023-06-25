package ca.amandeep.path.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import ca.amandeep.path.R
import ca.amandeep.path.ui.theme.PATHTheme
import ca.amandeep.path.util.ConnectionState
import ca.amandeep.path.util.launchPackageOrMarketPage

private const val OFFICIAL_PATH_APP_PACKAGE = "gov.panynj.pathuatapp"

/**
 * Show a full screen error screen with an icon and a message, differing depending on if there is an
 * internet connection or not.
 */
@Composable
fun ErrorScreen(
    connectivityState: ConnectionState,
    forceUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (connectivityState) {
            ConnectionState.Unavailable -> {
                Icon(
                    modifier = Modifier.size(100.dp),
                    painter = painterResource(id = R.drawable.ic_wifi_off),
                    contentDescription = stringResource(R.string.no_internet_icon),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(30.dp))
                Text(
                    text = stringResource(R.string.no_internet_connection),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
            ConnectionState.Available -> {
                Text(
                    stringResource(R.string.load_trains_error),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    stringResource(R.string.try_again_later_body_text),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(25.dp))
                FilledTonalButton(onClick = forceUpdate) {
                    Text(stringResource(R.string.try_again_action))
                }
                TextButton(onClick = {
                    launchPackageOrMarketPage(context, OFFICIAL_PATH_APP_PACKAGE)
                }) {
                    Text(stringResource(R.string.try_official_app_action))
                }
            }
        }
    }
}

@Composable
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun ErrorScreenPreview(
    @PreviewParameter(SampleConnectionStateProvider::class) connectivityState: ConnectionState,
) {
    PATHTheme {
        ErrorScreen(
            connectivityState = connectivityState,
            forceUpdate = {},
        )
    }
}
