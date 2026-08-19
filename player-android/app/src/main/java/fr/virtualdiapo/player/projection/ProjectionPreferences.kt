package fr.virtualdiapo.player.projection

import android.content.Context

data class ProjectionOptions(
    val soundEnabled: Boolean = true,
    val fadeEnabled: Boolean = true,
)

class ProjectionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): ProjectionOptions = ProjectionOptions(
        soundEnabled = preferences.getBoolean(SOUND_ENABLED, true),
        fadeEnabled = preferences.getBoolean(FADE_ENABLED, true),
    )

    fun save(options: ProjectionOptions) {
        preferences.edit()
            .putBoolean(SOUND_ENABLED, options.soundEnabled)
            .putBoolean(FADE_ENABLED, options.fadeEnabled)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "projection_preferences"
        const val SOUND_ENABLED = "sound_enabled"
        const val FADE_ENABLED = "fade_enabled"
    }
}
