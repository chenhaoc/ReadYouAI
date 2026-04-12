package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class TtsFloatingButtonDockSide {
    Left,
    Right,
}

internal fun anchoredButtonOffsetPx(
    dockSide: TtsFloatingButtonDockSide,
    containerWidthPx: Float,
    buttonWidthPx: Float,
    edgePaddingPx: Float,
): Float =
    when (dockSide) {
        TtsFloatingButtonDockSide.Left -> edgePaddingPx
        TtsFloatingButtonDockSide.Right ->
            (containerWidthPx - buttonWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx)
    }

internal fun resolveDockSideFromOffset(
    offsetPx: Float,
    containerWidthPx: Float,
    buttonWidthPx: Float,
): TtsFloatingButtonDockSide {
    val buttonCenter = offsetPx + buttonWidthPx / 2f
    return if (buttonCenter < containerWidthPx / 2f) {
        TtsFloatingButtonDockSide.Left
    } else {
        TtsFloatingButtonDockSide.Right
    }
}

@Composable
fun TtsFloatingPlayerButton(
    visible: Boolean,
    dockSide: TtsFloatingButtonDockSide,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onDockSideChange: (TtsFloatingButtonDockSide) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val buttonSize = 56.dp
        val edgePadding = 16.dp
        val density = LocalDensity.current
        val containerWidthPx = constraints.maxWidth.toFloat()
        val buttonWidthPx = with(density) { buttonSize.toPx() }
        val edgePaddingPx = with(density) { edgePadding.toPx() }

        var offsetPx by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }

        LaunchedEffect(dockSide, containerWidthPx) {
            if (!dragging) {
                offsetPx =
                    anchoredButtonOffsetPx(
                        dockSide = dockSide,
                        containerWidthPx = containerWidthPx,
                        buttonWidthPx = buttonWidthPx,
                        edgePaddingPx = edgePaddingPx,
                    )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset {
                            IntOffset(
                                x = offsetPx.roundToInt(),
                                y = -bottomPadding.roundToPx(),
                            )
                        }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state =
                                rememberDraggableState { delta ->
                                    dragging = true
                                    offsetPx =
                                        (offsetPx + delta).coerceIn(
                                            edgePaddingPx,
                                            (containerWidthPx - buttonWidthPx - edgePaddingPx)
                                                .coerceAtLeast(edgePaddingPx),
                                        )
                                },
                            onDragStopped = {
                                dragging = false
                                val resolvedDockSide =
                                    resolveDockSideFromOffset(
                                        offsetPx = offsetPx,
                                        containerWidthPx = containerWidthPx,
                                        buttonWidthPx = buttonWidthPx,
                                    )
                                onDockSideChange(resolvedDockSide)
                                offsetPx =
                                    anchoredButtonOffsetPx(
                                        dockSide = resolvedDockSide,
                                        containerWidthPx = containerWidthPx,
                                        buttonWidthPx = buttonWidthPx,
                                        edgePaddingPx = edgePaddingPx,
                                    )
                            },
                        )
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                        .size(buttonSize),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}
