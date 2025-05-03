package ui.main


import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.amandeep.path.strings.R
import ca.amandeep.path.ui.main.TopBar
import ca.amandeep.ui.core.theme.PATHTheme

@Suppress("UnusedReceiverParameter", "ktlint:compose:modifier-missing-check")
@Composable
fun RowScope.OverflowItems(
    forceRefresh: () -> Unit,
    showDebugOptions: Boolean,
    showModelNamePref: Boolean,
    setShowModelNamePref: (Boolean) -> Unit,
    aiSummarizeAlertsPref: Boolean,
    setAiSummarizeAlertsPref: (Boolean) -> Unit,
    shortenNamesPref: Boolean,
    setShortenNamesPref: (Boolean) -> Unit,
    showOppositeDirectionPref: Boolean,
    setShowOppositeDirectionPref: (Boolean) -> Unit,
    showElevatorAlertsPref: Boolean,
    showHelpGuidePref: Boolean,
    setShowElevatorAlertsPref: (Boolean) -> Unit,
    setShowHelpGuidePref: (Boolean) -> Unit,
    anyLocationPermissionsGranted: Boolean,
) {
    IconButton(onClick = forceRefresh) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.refresh_action),
        )
    }

    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more_item_actions),
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setShortenNamesPref(!shortenNamesPref) },
        ) {
            Checkbox(
                checked = shortenNamesPref,
                onCheckedChange = setShortenNamesPref,
            )
            Text(
                text = stringResource(R.string.shorten_names_action_text),
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        if (anyLocationPermissionsGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setShowOppositeDirectionPref(!showOppositeDirectionPref) },
            ) {
                Checkbox(
                    checked = showOppositeDirectionPref,
                    onCheckedChange = setShowOppositeDirectionPref,
                )
                Text(
                    text = stringResource(R.string.show_opposite_direction_action_text),
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setShowElevatorAlertsPref(!showElevatorAlertsPref) },
        ) {
            Checkbox(
                checked = showElevatorAlertsPref,
                onCheckedChange = setShowElevatorAlertsPref,
            )
            Text(
                text = stringResource(R.string.show_elevator_alerts),
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { setShowHelpGuidePref(!showHelpGuidePref) },
        ) {
            Checkbox(
                checked = showHelpGuidePref,
                onCheckedChange = setShowHelpGuidePref,
            )
            Text(
                text = stringResource(R.string.show_help_guide),
                modifier = Modifier.padding(end = 10.dp),
            )
        }

        if (showDebugOptions) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setAiSummarizeAlertsPref(!aiSummarizeAlertsPref) },
            ) {
                Checkbox(
                    checked = aiSummarizeAlertsPref,
                    onCheckedChange = setAiSummarizeAlertsPref,
                )
                Text(
                    text = "(Debug) Alert AI summaries",
                    modifier = Modifier.padding(end = 10.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setShowModelNamePref(!showModelNamePref) },
            ) {
                Checkbox(
                    checked = showModelNamePref,
                    onCheckedChange = setShowModelNamePref,
                )
                Text(
                    text = "(Debug) Show LLM model name",
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
        }
    }
}

@Composable
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun OverflowItemsPreview() {
    PATHTheme {
        TopBar {
            OverflowItems(
                forceRefresh = {},
                showDebugOptions = true,
                showModelNamePref = false,
                setShowModelNamePref = {},
                aiSummarizeAlertsPref = false,
                setAiSummarizeAlertsPref = {},
                shortenNamesPref = false,
                setShortenNamesPref = {},
                showOppositeDirectionPref = false,
                setShowOppositeDirectionPref = {},
                showElevatorAlertsPref = false,
                showHelpGuidePref = false,
                setShowElevatorAlertsPref = {},
                setShowHelpGuidePref = {},
                anyLocationPermissionsGranted = true,
            )
        }
    }
}
