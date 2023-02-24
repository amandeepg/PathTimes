package ca.amandeep.path.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import ca.amandeep.path.ui.theme.PATHTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration

/**
 * State for a composable that displays the time since [lastUpdated] in a human readable format.
 */
@Composable
fun rememberLastUpdatedState(lastUpdated: Long): MutableState<LastUpdatedUiModel> =
    remember { mutableStateOf(computeLastUpdatedModel(lastUpdated)) }

data class LastUpdatedUiModel(val unit: String, val value: Long)

/**
 * Updates the [MutableState] with the time since [lastUpdated] in a human readable format.
 */
@Composable
fun MutableState<LastUpdatedUiModel>.KeepUpdatedEffect(
    lastUpdated: Long,
    updateInterval: Duration,
) {
    LaunchedEffect(lastUpdated) {
        while (true) {
            value = computeLastUpdatedModel(lastUpdated)
            delay(updateInterval)
        }
    }
}

private fun computeLastUpdatedModel(lastUpdated: Long): LastUpdatedUiModel {
    val secondsAgo = (System.currentTimeMillis() - lastUpdated) / 1000
    val value = if (secondsAgo >= 60) secondsAgo / 60 else secondsAgo
    return LastUpdatedUiModel(
        unit = if (value == 1L) {
            if (secondsAgo >= 60) "minute" else "second"
        } else if (secondsAgo >= 60) "minutes" else "seconds",
        value = value,
    )
}

/**
 * Displays the time since [LastUpdatedUiModel] in a human readable format.
 */
@Composable
@OptIn(ExperimentalAnimationApi::class)
fun LastUpdatedInfoRow(
    lastUpdatedState: LastUpdatedUiModel,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun AnimatedText(text: String) = AnimatedContent(
        targetState = text,
        transitionSpec = verticalSwapAnimation,
    ) { Text(text = it) }

    ProvideTextStyle(
        TextStyle(
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
            color = MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Row(
            modifier = modifier
                .wrapContentWidth()
                .animateContentSize(animationSpec = tween(1000))
                .padding(5.dp),
        ) {
            Text("last updated ")
            if (lastUpdatedState.value == 0L) {
                AnimatedText("just now")
            } else {
                AnimatedText("${lastUpdatedState.value}")
                AnimatedText(" ${lastUpdatedState.unit}")
                Text(" ago")
            }
        }
    }
}

@Composable
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun LastUpdatedInfoRowPreview(
    @PreviewParameter(SampleLastUpdatedProvider::class) lastUpdatedState: LastUpdatedUiModel,
) {
    PATHTheme { LastUpdatedInfoRow(lastUpdatedState) }
}

class SampleLastUpdatedProvider : PreviewParameterProvider<LastUpdatedUiModel> {
    override val values = sequenceOf(
        LastUpdatedUiModel("seconds", 0L),
        LastUpdatedUiModel("minutes", 15L),
    )
}
