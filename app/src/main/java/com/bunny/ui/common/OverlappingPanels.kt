package com.bunny.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Panel { START, END }

/**
 * Discord-style overlapping panels navigation.
 *
 * The start and end panels are tracked independently: each progress value is
 * 0f (fully open) or 1f (fully closed). Swiping horizontally routes the drag
 * to whichever panel is open, or opens the matching panel from the closed
 * position. A scrim appears over the center layer whenever a panel is open;
 * tapping it closes the panel.
 *
 * In [OverlappingPanelsHost.wide] mode the start panel is pinned open and the
 * center layer is padded to sit next to it (master-detail); the end panel
 * still slides in over the right side.
 */
class OverlappingPanelsState internal constructor(
    private val scope: CoroutineScope,
    private val density: Density,
    val startPanelWidth: Dp,
    val endPanelWidth: Dp,
    startInitiallyOpen: Boolean = false,
    endInitiallyOpen: Boolean = false
) {
    internal val startProgress = Animatable(if (startInitiallyOpen) 0f else 1f)
    internal val endProgress = Animatable(if (endInitiallyOpen) 0f else 1f)

    private val startWidthPx: Float = with(density) { startPanelWidth.toPx() }
    private val endWidthPx: Float = with(density) { endPanelWidth.toPx() }

    /** When true the start panel is pinned open and never animates away. */
    var startPinned by mutableStateOf(false)

    private var dragPanel by mutableStateOf<Panel?>(null)
    private var dragFraction by mutableStateOf(1f)

    val startPanelWidthPx: Int get() = startWidthPx.roundToInt()
    val endPanelWidthPx: Int get() = endWidthPx.roundToInt()

    val isStartPanelOpen: Boolean get() = startProgress.value < 1f
    val isEndPanelOpen: Boolean get() = endProgress.value < 1f
    val isAnyPanelOpen: Boolean get() = isStartPanelOpen || isEndPanelOpen

    /** How many pixels of the start panel are currently visible on screen. */
    val startVisiblePx: State<Int> = derivedStateOf {
        val f = if (startPinned) 0f else if (dragPanel == Panel.START) dragFraction else startProgress.value
        (startWidthPx * (1f - f)).roundToInt().coerceAtLeast(0)
    }

    /** How many pixels of the end panel are currently visible on screen. */
    val endVisiblePx: State<Int> = derivedStateOf {
        val f = if (dragPanel == Panel.END) dragFraction else endProgress.value
        (endWidthPx * (1f - f)).roundToInt().coerceAtLeast(0)
    }

    /** Routes an incoming horizontal drag delta to the active panel. */
    fun onDrag(deltaPx: Float) {
        val existing = dragPanel
        val panel: Panel = existing ?: run {
            val chosen = when {
                startProgress.value < 1f -> Panel.START
                endProgress.value < 1f -> Panel.END
                deltaPx > 0f -> Panel.START
                else -> Panel.END
            }
            dragPanel = chosen
            dragFraction = when (chosen) {
                Panel.START -> startProgress.value
                Panel.END -> endProgress.value
            }
            chosen
        }
        val panelWidthPx = if (panel == Panel.START) startWidthPx else endWidthPx
        if (panelWidthPx <= 0f) return
        dragFraction = (dragFraction - deltaPx / panelWidthPx).coerceIn(0f, 1f)
    }

    /** Animates the dragged panel to the nearest anchor when the gesture ends. */
    fun onDragEnd() {
        val panel = dragPanel ?: return
        dragPanel = null
        val target = if (dragFraction < 0.5f) 0f else 1f
        val fraction = dragFraction
        scope.launch {
            if (panel == Panel.START) {
                startProgress.snapTo(fraction)
                startProgress.animateTo(target)
            } else {
                endProgress.snapTo(fraction)
                endProgress.animateTo(target)
            }
        }
    }

    suspend fun openStartPanel() {
        startProgress.animateTo(0f)
        endProgress.animateTo(1f)
    }

    suspend fun openEndPanel() {
        if (!startPinned) startProgress.animateTo(1f)
        endProgress.animateTo(0f)
    }

    suspend fun closePanels() {
        startProgress.animateTo(1f)
        endProgress.animateTo(1f)
    }

    /** Instantly positions the panels (used on orientation changes). */
    suspend fun snapTo(startOpen: Boolean, endOpen: Boolean) {
        startProgress.snapTo(if (startOpen) 0f else 1f)
        endProgress.snapTo(if (endOpen) 0f else 1f)
    }

    /** Back button: close the end panel first, then the start panel. */
    suspend fun handleBack() {
        when {
            endProgress.value < 1f -> endProgress.animateTo(1f)
            startProgress.value < 1f && !startPinned -> startProgress.animateTo(1f)
        }
    }
}

@Composable
fun rememberOverlappingPanelsState(
    startPanelWidth: Dp,
    endPanelWidth: Dp,
    startInitiallyOpen: Boolean = false,
    endInitiallyOpen: Boolean = false
): OverlappingPanelsState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(startPanelWidth, endPanelWidth, density) {
        OverlappingPanelsState(scope, density, startPanelWidth, endPanelWidth, startInitiallyOpen, endInitiallyOpen)
    }
    var savedStart by rememberSaveable { mutableStateOf(if (startInitiallyOpen) 0f else 1f) }
    var savedEnd by rememberSaveable { mutableStateOf(if (endInitiallyOpen) 0f else 1f) }
    LaunchedEffect(state) {
        state.startProgress.snapTo(savedStart.coerceIn(0f, 1f))
        state.endProgress.snapTo(savedEnd.coerceIn(0f, 1f))
        kotlinx.coroutines.coroutineScope {
            launch {
                snapshotFlow { state.startProgress.value }
                    .distinctUntilChanged()
                    .collect { savedStart = it }
            }
            launch {
                snapshotFlow { state.endProgress.value }
                    .distinctUntilChanged()
                    .collect { savedEnd = it }
            }
        }
    }
    return state
}

/**
 * Host layout for the three panels.
 *
 * @param state state driving panel offsets, created with [rememberOverlappingPanelsState]
 * @param modifier modifier for the host root
 * @param wide when true the start panel is pinned open and the center layer is
 *   padded to sit next to it (master-detail); dragging is disabled
 * @param startPanel content of the start panel (server rail + channel list)
 * @param centerPanel full-screen base layer (chat)
 * @param endPanel content of the end panel (slides in from the right)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlappingPanelsHost(
    state: OverlappingPanelsState,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    startPanel: @Composable () -> Unit,
    centerPanel: @Composable () -> Unit,
    endPanel: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val startVisible by state.startVisiblePx
    val endVisible by state.endVisiblePx
    val scrimVisible = if (wide) state.isEndPanelOpen else state.isAnyPanelOpen

    BackHandler(enabled = if (wide) state.isEndPanelOpen else state.isAnyPanelOpen) {
        scope.launch { state.handleBack() }
    }

    val rootModifier = if (wide) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    state.onDrag(delta)
                },
                onDragStopped = {
                    scope.launch { state.onDragEnd() }
                }
            )
    }

    Box(rootModifier) {
        // Center layer: full-screen base content (chat). In wide mode it is
        // padded so the pinned start panel never covers it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (wide) state.startPanelWidth else 0.dp)
        ) {
            centerPanel()
        }

        // Scrim: dims the center and closes open panels on tap.
        if (scrimVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { scope.launch { state.closePanels() } }
            )
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
