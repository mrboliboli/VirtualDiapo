package fr.virtualdiapo.player.projection

import kotlin.math.max
import kotlin.math.min

data class SlideTransform(
    val translationXFactor: Float,
    val translationYFactor: Float,
    val rotationZ: Float,
    val scale: Float,
    val alpha: Float,
)

object ProjectionTransition {
    const val TOTAL_DURATION_MS = 1_790L
    const val ENTRY_START_MS = 1_610L
    const val SOUND_CLICK_MS = 1_690L

    fun outgoing(elapsedMs: Long, direction: Int, fadeEnabled: Boolean): SlideTransform {
        val progress = fastOutLinearIn(fraction(elapsedMs, 0L, 140L))
        val alpha = if (fadeEnabled) {
            1f - fraction(elapsedMs, 40L, 130L)
        } else {
            1f
        }
        return SlideTransform(
            translationXFactor = -direction * 12f * progress,
            translationYFactor = 8f * progress,
            rotationZ = -direction * .16f * progress,
            scale = 1f - .008f * progress,
            alpha = alpha,
        )
    }

    fun incoming(elapsedMs: Long, direction: Int, fadeEnabled: Boolean): SlideTransform {
        val progress = linearOutSlowIn(fraction(elapsedMs, ENTRY_START_MS, TOTAL_DURATION_MS))
        val alpha = if (fadeEnabled) {
            fraction(elapsedMs, 1_650L, 1_770L)
        } else {
            1f
        }
        return SlideTransform(
            translationXFactor = direction * 12f * (1f - progress),
            translationYFactor = -8f * (1f - progress),
            rotationZ = direction * .16f * (1f - progress),
            scale = .992f + .008f * progress,
            alpha = alpha,
        )
    }

    private fun fraction(value: Long, start: Long, end: Long): Float =
        ((value - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    // Courbes cubiques Android FastOutLinearIn et LinearOutSlowIn.
    private fun fastOutLinearIn(value: Float): Float = cubicBezierY(value, .4f, 0f, 1f, 1f)

    private fun linearOutSlowIn(value: Float): Float = cubicBezierY(value, 0f, 0f, .2f, 1f)

    private fun cubicBezierY(value: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        var low = 0f
        var high = 1f
        repeat(12) {
            val time = (low + high) / 2f
            if (cubic(time, x1, x2) < value) low = time else high = time
        }
        return cubic((low + high) / 2f, y1, y2)
    }

    private fun cubic(time: Float, first: Float, second: Float): Float {
        val inverse = 1f - time
        return 3f * inverse * inverse * time * first +
            3f * inverse * time * time * second +
            time * time * time
    }
}

object PreloadWindow {
    const val PRELOAD_AHEAD = 3

    fun indices(currentIndex: Int, slideCount: Int): List<Int> {
        if (slideCount <= 0) return emptyList()
        val safeCurrent = currentIndex.coerceIn(0, slideCount - 1)
        val first = max(0, safeCurrent - 1)
        val last = min(slideCount - 1, safeCurrent + PRELOAD_AHEAD)
        return (first..last).toList()
    }

    fun initialIndices(slideCount: Int): List<Int> =
        (0 until min(slideCount, PRELOAD_AHEAD + 1)).toList()
}
