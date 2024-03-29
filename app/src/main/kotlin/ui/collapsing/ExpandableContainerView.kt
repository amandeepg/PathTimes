package ca.amandeep.path.ui.collapsing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

@Composable
fun ExpandableContainerView(
    onClickHeader: () -> Unit,
    headerContent: @Composable (BoxScope.() -> Unit),
    expandableContent: @Composable (AnimatedVisibilityScope.() -> Unit),
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    Column(modifier = modifier) {
        HeaderView(
            onClick = onClickHeader,
            content = headerContent,
        )
        ExpandableView(
            isExpanded = expanded,
            content = expandableContent,
        )
    }
}

@Composable
private fun HeaderView(
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .expandableClickable(onClick = onClick),
        content = content,
    )
}

@Composable
fun ExpandableView(
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = isExpanded,
        enter = remember {
            expandVertically(expandFrom = Alignment.Top) +
                fadeIn()
        },
        exit = remember {
            shrinkVertically(shrinkTowards = Alignment.Top) +
                fadeOut()
        },
        content = content,
    )
}

fun Modifier.expandableClickable(
    onClick: () -> Unit,
) = this.composed {
    clickable(
        indication = null, // Removes the ripple effect on tap
        interactionSource = remember { MutableInteractionSource() }, // Removes the ripple effect on tap
        onClick = onClick,
    )
}

@Composable
fun animateExpandingArrow(expanded: Boolean) = animateFloatAsState(
    animationSpec = tween(),
    targetValue = if (expanded) 0f else 180f,
    label = "Animate collapse icon",
)
