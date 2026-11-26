package com.roundmusic.player.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import com.roundmusic.player.data.waveform.WaveformLoader

val LocalWaveformLoader = staticCompositionLocalOf<WaveformLoader> {
    error("WaveformLoader was not provided")
}

/**
 * The waveform seek bar. Bars up to the current position are white, the rest are dim, and the bar at
 * the current position is the tallest. Tap or drag to seek.
 *
 * @param peaks real loudness values (0..1) from [WaveformLoader], or null while loading / unavailable
 *              (then short flat bars are drawn).
 * @param onScrub called while you drag, with the position as a fraction 0..1 (nothing is sought yet)
 * @param onScrubEnd called when you lift your finger or tap, with the final fraction: seek now
 * @param onScrubCancel called if the gesture is cancelled
 */
@Composable
fun WaveformBar(
    peaks: FloatArray?,
    progress: Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onScrubCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onScrubEnd((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub(last)
                    },
                    onDragEnd = { onScrubEnd(last) },
                    onDragCancel = { onScrubCancel() },
                    onHorizontalDrag = { change, _ ->
                        last = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub(last)
                    }
                )
            }
    ) {
        val count = WaveformLoader.BARS
        val slot = size.width / count
        val barWidth = slot * 0.5f
        val playedIndex = (progress.coerceIn(0f, 1f) * count).toInt().coerceAtMost(count - 1)
        val minHeight = 4.dp.toPx()

        for (i in 0 until count) {
            val level = if (peaks != null && peaks.size == count) peaks[i] else 0f
            val height = when {
                i == playedIndex -> size.height
                else -> (size.height * (0.12f + 0.88f * level)).coerceAtLeast(minHeight)
            }
            val x = slot * i + slot / 2f
            val top = (size.height - height) / 2f
            drawLine(
                color = if (i <= playedIndex) Color.White else Color.White.copy(alpha = 0.35f),
                start = Offset(x, top),
                end = Offset(x, top + height),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
