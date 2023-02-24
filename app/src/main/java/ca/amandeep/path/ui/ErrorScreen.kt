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
                    contentDescription = "No internet icon",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(30.dp))
                Text(
                    text = "No internet connection",
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                    lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
            ConnectionState.Available -> {
                Text(
                    "Couldn't load next trains",
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                    lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Try again later or try the official app for now",
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(25.dp))
                FilledTonalButton(onClick = forceUpdate) {
                    Text("Try again")
                }
                TextButton(onClick = {
                    launchPackageOrMarketPage(context, OFFICIAL_PATH_APP_PACKAGE)
                },) {
                    Text("Try the official app")
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
