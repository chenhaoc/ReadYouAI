package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val floatingButtonSize = 56.dp
private val floatingButtonEdgePadding = 0.dp
private const val dockedFloatingButtonScale = 0.5f

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

internal fun verticalButtonOffsetRangePx(
    containerHeightPx: Float,
    buttonHeightPx: Float,
    topInsetPx: Float,
    bottomInsetPx: Float,
    bottomPaddingPx: Float,
    edgePaddingPx: Float,
): ClosedFloatingPointRange<Float> {
    val minOffsetPx = topInsetPx + edgePaddingPx
    val maxOffsetPx =
        (containerHeightPx - buttonHeightPx - bottomInsetPx - bottomPaddingPx - edgePaddingPx)
            .coerceAtLeast(minOffsetPx)
    return minOffsetPx..maxOffsetPx
}

internal fun resolveVerticalOffsetFromRatio(
    verticalRatio: Float,
    minOffsetPx: Float,
    maxOffsetPx: Float,
): Float = minOffsetPx + (maxOffsetPx - minOffsetPx) * verticalRatio.coerceIn(0f, 1f)

internal fun resolveVerticalRatioFromOffset(
    offsetPx: Float,
    minOffsetPx: Float,
    maxOffsetPx: Float,
): Float {
    if (maxOffsetPx <= minOffsetPx) return 1f
    return ((offsetPx - minOffsetPx) / (maxOffsetPx - minOffsetPx)).coerceIn(0f, 1f)
}

@Composable
fun TtsFloatingPlayerButton(
    visible: Boolean,
    dockSide: TtsFloatingButtonDockSide,
    verticalRatio: Float,
    bottomPadding: Dp,
    onPositionChange: (TtsFloatingButtonDockSide, Float) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()
        val buttonWidthPx = with(density) { floatingButtonSize.toPx() }
        val buttonHeightPx = with(density) { floatingButtonSize.toPx() }
        val edgePaddingPx = with(density) { floatingButtonEdgePadding.toPx() }
        val bottomPaddingPx = with(density) { bottomPadding.toPx() }
        val topInsetPx =
            with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
        val bottomInsetPx =
            with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
        val maxHorizontalOffsetPx =
            (containerWidthPx - buttonWidthPx - edgePaddingPx).coerceAtLeast(edgePaddingPx)
        val verticalOffsetRange =
            verticalButtonOffsetRangePx(
                containerHeightPx = containerHeightPx,
                buttonHeightPx = buttonHeightPx,
                topInsetPx = topInsetPx,
                bottomInsetPx = bottomInsetPx,
                bottomPaddingPx = bottomPaddingPx,
                edgePaddingPx = edgePaddingPx,
            )

        var horizontalOffsetPx by remember { mutableFloatStateOf(0f) }
        var verticalOffsetPx by remember { mutableFloatStateOf(0f) }
        var dragging by remember { mutableStateOf(false) }
        val buttonScale by
            animateFloatAsState(
                targetValue = if (dragging) 1f else dockedFloatingButtonScale,
                animationSpec = spring(stiffness = 500f, dampingRatio = 0.85f),
                label = "ttsFloatingPlayerButtonScale",
            )
        val resolvedDockSide =
            resolveDockSideFromOffset(
                offsetPx = horizontalOffsetPx,
                containerWidthPx = containerWidthPx,
                buttonWidthPx = buttonWidthPx,
            )
        val verticalTransformOrigin =
            when {
                abs(verticalOffsetPx - verticalOffsetRange.start) <= 1f -> 0f
                abs(verticalOffsetPx - verticalOffsetRange.endInclusive) <= 1f -> 1f
                else -> 0.5f
            }

        LaunchedEffect(
            dockSide,
            verticalRatio,
            containerWidthPx,
            containerHeightPx,
            topInsetPx,
            bottomInsetPx,
            bottomPaddingPx,
        ) {
            if (!dragging) {
                horizontalOffsetPx =
                    anchoredButtonOffsetPx(
                        dockSide = dockSide,
                        containerWidthPx = containerWidthPx,
                        buttonWidthPx = buttonWidthPx,
                        edgePaddingPx = edgePaddingPx,
                    )
                verticalOffsetPx =
                    resolveVerticalOffsetFromRatio(
                        verticalRatio = verticalRatio,
                        minOffsetPx = verticalOffsetRange.start,
                        maxOffsetPx = verticalOffsetRange.endInclusive,
                    )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(
                                x = horizontalOffsetPx.roundToInt(),
                                y = verticalOffsetPx.roundToInt(),
                            )
                        }
                        .pointerInput(
                            dockSide,
                            verticalRatio,
                            containerWidthPx,
                            containerHeightPx,
                            topInsetPx,
                            bottomInsetPx,
                            bottomPaddingPx,
                        ) {
                            detectDragGestures(
                                onDragStart = { dragging = true },
                                onDragCancel = {
                                    dragging = false
                                    horizontalOffsetPx =
                                        anchoredButtonOffsetPx(
                                            dockSide = dockSide,
                                            containerWidthPx = containerWidthPx,
                                            buttonWidthPx = buttonWidthPx,
                                            edgePaddingPx = edgePaddingPx,
                                        )
                                    verticalOffsetPx =
                                        resolveVerticalOffsetFromRatio(
                                            verticalRatio = verticalRatio,
                                            minOffsetPx = verticalOffsetRange.start,
                                            maxOffsetPx = verticalOffsetRange.endInclusive,
                                        )
                                },
                                onDragEnd = {
                                    dragging = false
                                    val resolvedDockSide =
                                        resolveDockSideFromOffset(
                                            offsetPx = horizontalOffsetPx,
                                            containerWidthPx = containerWidthPx,
                                            buttonWidthPx = buttonWidthPx,
                                        )
                                    val resolvedVerticalRatio =
                                        resolveVerticalRatioFromOffset(
                                            offsetPx = verticalOffsetPx,
                                            minOffsetPx = verticalOffsetRange.start,
                                            maxOffsetPx = verticalOffsetRange.endInclusive,
                                        )
                                    horizontalOffsetPx =
                                        anchoredButtonOffsetPx(
                                            dockSide = resolvedDockSide,
                                            containerWidthPx = containerWidthPx,
                                            buttonWidthPx = buttonWidthPx,
                                            edgePaddingPx = edgePaddingPx,
                                        )
                                    verticalOffsetPx =
                                        resolveVerticalOffsetFromRatio(
                                            verticalRatio = resolvedVerticalRatio,
                                            minOffsetPx = verticalOffsetRange.start,
                                            maxOffsetPx = verticalOffsetRange.endInclusive,
                                        )
                                    onPositionChange(resolvedDockSide, resolvedVerticalRatio)
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                horizontalOffsetPx =
                                    (horizontalOffsetPx + dragAmount.x).coerceIn(
                                        edgePaddingPx,
                                        maxHorizontalOffsetPx,
                                    )
                                verticalOffsetPx =
                                    (verticalOffsetPx + dragAmount.y).coerceIn(
                                        verticalOffsetRange.start,
                                        verticalOffsetRange.endInclusive,
                                    )
                            }
                        }
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                        .size(floatingButtonSize)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                            transformOrigin =
                                TransformOrigin(
                                    pivotFractionX =
                                        when (resolvedDockSide) {
                                            TtsFloatingButtonDockSide.Left -> 0f
                                            TtsFloatingButtonDockSide.Right -> 1f
                                        },
                                    pivotFractionY = verticalTransformOrigin,
                                )
                        },
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
