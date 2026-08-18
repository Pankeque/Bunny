package com.bunny.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Discord-style overlapping panels navigation.
 *
 * A single horizontal axis drives three anchors:
 *   0f -> start panel fully open
 *   1f -> all panels closed (middle)
 *   2f -> end panel fully open
 *
 * The center panel stays on the base layer while the start/end panels
 * slide over it with a spring animation. Swiping horizontally anywhere on
 * the center panel opens the side panels; the back button closes them
 * before leaving the screen.
 */
class OverlappingPanelsState internal constructor(
    private val scope: CoroutineScope,
    private val density: Density,
    val startPanelWidth: Dp,
    val endPanelWidth: Dp,
    initialValue: Float = 1f
) {
    internal val progress = Animatable(initialValue.coerceIn(0f, 2f))

    private val startWidthPx: Float = with(density) { startPanelWidth.toPx() }
    private val endWidthPx: Float = with(density) { endPanelWidth.toPx() }

    val startPanelWidthPx: Int get() = startWidthPx.roundToInt()
    val endPanelWidthPx: Int get() = endWidthPx.roundToInt()

    /** How many pixels of the start panel are currently visible on screen. */
    val startVisiblePx: State<Int> = derivedStateOf {
        val p = progress.value
        if (p <= 1f) (startWidthPx * (1f - p)).roundToInt().coerceAtLeast(0) else 0
    }

    /** How many pixels of the end panel are currently visible on screen. */
    val endVisiblePx: State<Int> = derivedStateOf {
        val p = progress.value
        if (p >= 1f) (endWidthPx * (p - 1f)).roundToInt().coerceAtLeast(0) else 0
    }

    val isAnyPanelOpen: Boolean
        get() = progress.value != 1f

    fun onDrag(deltaPx: Float) {
        val current = progress.value
        val panelWidthPx = if (current <= 1f) startWidthPx else endWidthPx
        if (panelWidthPx <= 0f) return
        val next = current - deltaPx / panelWidthPx
        scope.launch { progress.snapTo(next.coerceIn(0f, 2f)) }
    }

    suspend fun settle(velocityX: Float) {
        val current = progress.value
        val target = when {
            current <= 1f -> {
                when {
                    velocityX < -1200f -> 1f
                    velocityX > 1200f -> 0f
                    current < 0.5f -> 0f
                    else -> 1f
                }
            }
            else -> {
                when {
                    velocityX > 1200f -> 1f
                    velocityX < -1200f -> 2f
                    current < 1.5f -> 1f
                    else -> 2f
                }
            }
        }
        animateTo(target)
    }

    suspend fun openStartPanel() = animateTo(0f)

    suspend fun openEndPanel() = animateTo(2f)

    suspend fun closePanels() = animateTo(1f)

    /** Back button: close the end panel first, then the start panel. */
    suspend fun handleBack() {
        when {
            progress.value >= 1.5f -> animateTo(1f)
            progress.value <= 0.5f -> animateTo(1f)
        }
    }

    private suspend fun animateTo(target: Float) {
        progress.animateTo(
            target,
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
}

@Composable
fun rememberOverlappingPanelsState(
    startPanelWidth: Dp,
    endPanelWidth: Dp,
    initialValue: Float = 1f
): OverlappingPanelsState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(startPanelWidth, endPanelWidth, density) {
        OverlappingPanelsState(scope, density, startPanelWidth, endPanelWidth, initialValue)
    }
    var savedAnchor by rememberSaveable { mutableStateOf(initialValue) }
    LaunchedEffect(state) {
        val restored = savedAnchor
        if (restored != state.progress.value) {
            state.progress.snapTo(restored.coerceIn(0f, 2f))
        }
        snapshotFlow { state.progress.value }
            .distinctUntilChanged()
            .collect { savedAnchor = it }
    }
    return state
}

/**
 * Host layout for the three overlapping panels.
 *
 * @param state state driving panel offsets, created with [rememberOverlappingPanelsState]
 * @param startPanel content of the start panel (slides in from the left)
 * @param centerPanel base layer that stays full-screen (channels + chat)
 * @param endPanel content of the end panel (slides in from the right)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlappingPanelsHost(
    state: OverlappingPanelsState,
    modifier: Modifier = Modifier,
    startPanel: @Composable () -> Unit,
    centerPanel: @Composable () -> Unit,
    endPanel: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val startVisible by state.startVisiblePx
    val endVisible by state.endVisiblePx

    BackHandler(enabled = state.isAnyPanelOpen) {
        scope.launch { state.handleBack() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Center layer: full-screen, and the only layer that responds to the
        // horizontal swipe that opens/closes the side panels. Children that
        // scroll vertically (channel list, message list) keep their gestures.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        state.onDrag(delta)
                    },
                    onDragStopped = { velocity ->
                        scope.launch { state.settle(velocity.x) }
                    }
                )
        ) {
            centerPanel()
        }

        // Start panel: sits flush left when open, fully off-screen when closed.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(state.startPanelWidth)
                .offset { IntOffset(-(state.startPanelWidthPx - startVisible), 0) }
                .zIndex(1f)
        ) {
            startPanel()
        }

        // End panel: sits flush right when open, fully off-screen when closed.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(state.endPanelWidth)
                .offset { IntOffset(state.endPanelWidthPx - endVisible, 0) }
                .zIndex(1f)
        ) {
            endPanel()
        }
    }
}
