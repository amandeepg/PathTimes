package ca.amandeep.path.ui.stations

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.amandeep.path.R
import ca.amandeep.path.data.model.Direction
import ca.amandeep.path.ui.theme.PATHTheme
import kotlinx.coroutines.launch

@Composable
fun DirectionWarning(
    isInNJ: Boolean,
    showOppositeDirection: Boolean,
    setShowingOppositeDirection: (Boolean) -> Unit,
    snackbarState: SnackbarHostState,
    setShowDirectionWarning: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        val direction = if (isInNJ) Direction.ToNY else Direction.ToNJ
        val location = if (isInNJ) Direction.ToNJ else Direction.ToNY
        Column(
            modifier = Modifier
                .padding(top = 8.dp, start = 5.dp, end = 4.dp)
                .alpha(0.6f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    modifier = Modifier
                        .padding(5.dp)
                        .size(18.dp),
                    contentDescription = stringResource(R.string.error_icon),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    style = MaterialTheme.typography.labelLarge,
                    text = if (showOppositeDirection) {
                        stringResource(
                            R.string.all_trains_shown_warning,
                            location.stateNameShort,
                            location.stateNameShort,
                        )
                    } else {
                        stringResource(
                            R.string.one_direction_shown_warning,
                            location.stateNameShort,
                            direction.stateNameShort,
                        )
                    },
                )
            }
            Row {
                TextButton(
                    onClick = { setShowingOppositeDirection(!showOppositeDirection) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (showOppositeDirection) {
                            stringResource(R.string.show_only_to_nynj, direction.stateNameShort)
                        } else {
                            stringResource(R.string.show_to_nynj_too, location.stateNameShort)
                        },
                        textAlign = TextAlign.Center,
                    )
                }

                val snackbarMessage = stringResource(R.string.change_dir_in_options)
                val snackbarActionLabel = stringResource(R.string.undo)
                TextButton(
                    onClick = {
                        setShowDirectionWarning(false)
                        coroutineScope.launch {
                            val snackbarResult = snackbarState.showSnackbar(
                                message = snackbarMessage,
                                actionLabel = snackbarActionLabel,
                            )
                            if (snackbarResult == SnackbarResult.ActionPerformed) {
                                setShowDirectionWarning(true)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.dismiss),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light", widthDp = 350)
@Preview(name = "Dark", widthDp = 350, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BothDirectionWarningSample() {
    PATHTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(5.dp),
        ) {
            DirectionWarning(
                isInNJ = true,
                showOppositeDirection = true,
                setShowingOppositeDirection = {},
                snackbarState = SnackbarHostState(),
                setShowDirectionWarning = {},
            )
        }
    }
}

@Preview(name = "Light", widthDp = 350)
@Preview(name = "Dark", widthDp = 350, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OneDirectionWarningSample() {
    PATHTheme {
        Box(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(5.dp),
        ) {
            DirectionWarning(
                isInNJ = true,
                showOppositeDirection = false,
                setShowingOppositeDirection = {},
                snackbarState = SnackbarHostState(),
                setShowDirectionWarning = {},
            )
        }
    }
}
