package fr.virtualdiapo.player.projection

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class SlideTransform(
    val translationXFactor: Float,
    val photoAlpha: Float,
)

object ProjectionTransition {
    const val MOVEMENT_START_MS = 0L
    const val SOUND_CLICK_MS = 260L
    const val TOTAL_DURATION_MS = 520L
    private const val TRAVEL_WIDTH_FACTOR = 1.08f

    fun outgoing(elapsedMs: Long, direction: Int, fadeEnabled: Boolean): SlideTransform {
        val progress = movementProgress(elapsedMs)
        val photoAlpha = if (fadeEnabled) {
            1f - fraction(progress, .88f, 1f)
        } else {
            1f
        }
        return SlideTransform(
            translationXFactor = -direction * TRAVEL_WIDTH_FACTOR * progress,
            photoAlpha = photoAlpha,
        )
    }

    fun incoming(elapsedMs: Long, direction: Int, fadeEnabled: Boolean): SlideTransform {
        val progress = movementProgress(elapsedMs)
        val photoAlpha = if (fadeEnabled) {
            fraction(progress, 0f, .12f)
        } else {
            1f
        }
        return SlideTransform(
            translationXFactor = direction * TRAVEL_WIDTH_FACTOR * (1f - progress),
            photoAlpha = photoAlpha,
        )
    }

    fun movementProgress(elapsedMs: Long): Float = smoothInOut(
        fraction(elapsedMs, MOVEMENT_START_MS, TOTAL_DURATION_MS),
    )

    private fun fraction(value: Long, start: Long, end: Long): Float =
        ((value - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    private fun fraction(value: Float, start: Float, end: Float): Float =
        ((value - start) / (end - start)).coerceIn(0f, 1f)

    private fun smoothInOut(value: Float): Float = cubicBezierY(value, .45f, 0f, .55f, 1f)

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

object ProjectionNavigation {
    fun acceptsKeyDown(isKeyDown: Boolean, repeatCount: Int): Boolean =
        isKeyDown && repeatCount == 0
}

object ProjectionBeamGeometry {
    private const val DIAGONAL_RADIUS_FACTOR = .78f
    private const val HALO_RADIUS_FACTOR = .26f

    fun radius(width: Float, height: Float): Float =
        DIAGONAL_RADIUS_FACTOR * hypot(width, height)

    fun haloRadius(width: Float, height: Float): Float =
        HALO_RADIUS_FACTOR * minOf(width, height)
}

data class ProjectionDustParticle(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val coreAlpha: Float,
    val haloAlpha: Float,
    val amplitudeXDp: Float,
    val amplitudeYDp: Float,
    val phaseX: Float,
    val phaseY: Float,
)

object ProjectionDust {
    private const val REFERENCE_AREA = 1_920f * 1_080f
    private const val SEED = 0x56444941

    fun count(width: Float, height: Float): Int =
        (14f * width * height / REFERENCE_AREA)
            .roundToInt()
            .coerceIn(10, 20)

    fun generate(width: Float, height: Float): List<ProjectionDustParticle> {
        if (width <= 0f || height <= 0f) return emptyList()
        val random = StableRandom(SEED)
        val count = count(width, height)
        val minimumDistance = .04f * minOf(width, height)
        val largeCount = (count * .05f).toInt()
        val mediumCount = (count * .20f).roundToInt()
        val sizes = MutableList(count) { index ->
            when {
                index < largeCount -> 1.6f
                index < largeCount + mediumCount -> random.between(1f, 1.35f)
                else -> random.between(.55f, .90f)
            }
        }
        random.shuffle(sizes)
        val particles = mutableListOf<ProjectionDustParticle>()
        val centralCount = (count * .70f).roundToInt()
        repeat(count) { index ->
            val central = index < centralCount
            val position = findPosition(
                width = width,
                height = height,
                central = central,
                minimumDistance = minimumDistance,
                existing = particles,
                random = random,
            )
            particles += ProjectionDustParticle(
                x = position.first,
                y = position.second,
                radiusDp = sizes[index],
                coreAlpha = random.between(.045f, .085f),
                haloAlpha = random.between(.010f, .018f),
                amplitudeXDp = random.between(1.5f, 4f),
                amplitudeYDp = random.between(3f, 7f),
                phaseX = random.between(0f, (2f * PI).toFloat()),
                phaseY = random.between(0f, (2f * PI).toFloat()),
            )
        }
        return particles
    }

    fun offsetX(particle: ProjectionDustParticle, phase: Float): Float {
        val angle = phase * (2f * PI).toFloat()
        return sin(angle + particle.phaseX) * particle.amplitudeXDp
    }

    fun offsetY(particle: ProjectionDustParticle, phase: Float): Float {
        val angle = phase * (2f * PI).toFloat()
        return cos(angle + particle.phaseY) * particle.amplitudeYDp
    }

    private fun findPosition(
        width: Float,
        height: Float,
        central: Boolean,
        minimumDistance: Float,
        existing: List<ProjectionDustParticle>,
        random: StableRandom,
    ): Pair<Float, Float> {
        var candidate = Pair(width / 2f, height / 2f)
        repeat(80) {
            candidate = if (central) {
                val angle = random.between(0f, (2f * PI).toFloat())
                val radius = sqrt(random.nextFloat())
                Pair(
                    width / 2f + cos(angle) * radius * .38f * width,
                    height / 2f + sin(angle) * radius * .42f * height,
                )
            } else {
                Pair(
                    random.between(.04f * width, .96f * width),
                    random.between(.04f * height, .96f * height),
                )
            }
            val ellipseValue = ellipseValue(candidate.first, candidate.second, width, height)
            if (central && ellipseValue >= 1f) return@repeat
            if (!central && ellipseValue < 1f) return@repeat
            val separated = existing.all { particle ->
                hypot(candidate.first - particle.x, candidate.second - particle.y) >= minimumDistance
            }
            if (separated) return candidate
        }
        if (central) return candidate
        return Pair(
            .04f * width,
            random.between(.04f * height, .96f * height),
        )
    }

    fun ellipseValue(x: Float, y: Float, width: Float, height: Float): Float {
        val normalizedX = (x - width / 2f) / (.38f * width)
        val normalizedY = (y - height / 2f) / (.42f * height)
        return normalizedX * normalizedX + normalizedY * normalizedY
    }

    private class StableRandom(seed: Int) {
        private var state = seed

        fun nextFloat(): Float {
            state = state * 1_664_525 + 1_013_904_223
            return ((state ushr 8) and 0x00FFFFFF) / 16_777_216f
        }

        fun between(minimum: Float, maximum: Float): Float =
            minimum + nextFloat() * (maximum - minimum)

        fun <T> shuffle(values: MutableList<T>) {
            for (index in values.lastIndex downTo 1) {
                val swapIndex = (nextFloat() * (index + 1)).toInt().coerceAtMost(index)
                val value = values[index]
                values[index] = values[swapIndex]
                values[swapIndex] = value
            }
        }
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
