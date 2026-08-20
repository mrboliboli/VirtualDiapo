package fr.virtualdiapo.player.projection

import android.content.Context

data class ProjectionOptions(
    val soundEnabled: Boolean = true,
    val fadeEnabled: Boolean = true,
    val autoAdvanceEnabled: Boolean = false,
    val autoAdvanceDelaySeconds: Int = AutoAdvancePolicy.DEFAULT_DELAY_SECONDS,
)

object AutoAdvancePolicy {
    const val MIN_DELAY_SECONDS = 3
    const val MAX_DELAY_SECONDS = 60
    const val DEFAULT_DELAY_SECONDS = 10

    fun normalizeDelay(seconds: Int): Int =
        seconds.coerceIn(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)

    fun adjustDelay(seconds: Int, delta: Int): Int =
        normalizeDelay(seconds + delta)

    fun eligibleSlideIndex(state: ProjectionState, enabled: Boolean): Int? {
        if (!enabled) return null
        val slide = state as? ProjectionState.Slide ?: return null
        return slide.index
    }
}

class ProjectionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): ProjectionOptions = ProjectionOptions(
        soundEnabled = preferences.getBoolean(SOUND_ENABLED, true),
        fadeEnabled = preferences.getBoolean(FADE_ENABLED, true),
        autoAdvanceEnabled = preferences.getBoolean(AUTO_ADVANCE_ENABLED, false),
        autoAdvanceDelaySeconds = AutoAdvancePolicy.normalizeDelay(
            preferences.getInt(AUTO_ADVANCE_DELAY_SECONDS, AutoAdvancePolicy.DEFAULT_DELAY_SECONDS),
        ),
    )

    fun save(options: ProjectionOptions) {
        preferences.edit()
            .putBoolean(SOUND_ENABLED, options.soundEnabled)
            .putBoolean(FADE_ENABLED, options.fadeEnabled)
            .putBoolean(AUTO_ADVANCE_ENABLED, options.autoAdvanceEnabled)
            .putInt(
                AUTO_ADVANCE_DELAY_SECONDS,
                AutoAdvancePolicy.normalizeDelay(options.autoAdvanceDelaySeconds),
            )
            .apply()
    }

    private companion object {
        const val FILE_NAME = "projection_preferences"
        const val SOUND_ENABLED = "sound_enabled"
        const val FADE_ENABLED = "fade_enabled"
        const val AUTO_ADVANCE_ENABLED = "auto_advance_enabled"
        const val AUTO_ADVANCE_DELAY_SECONDS = "auto_advance_delay_seconds"
    }
}
