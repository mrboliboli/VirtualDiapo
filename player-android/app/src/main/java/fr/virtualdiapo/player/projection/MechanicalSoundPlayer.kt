package fr.virtualdiapo.player.projection

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import fr.virtualdiapo.player.R
import kotlinx.coroutines.CompletableDeferred

class MechanicalSoundPlayer(context: Context) {
    private val loaded = CompletableDeferred<Unit>()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, _, status ->
                if (status == 0) loaded.complete(Unit)
                else loaded.completeExceptionally(IllegalStateException("Impossible de charger le son Reflecta ($status)"))
            }
        }
    private val soundId = soundPool.load(context.applicationContext, R.raw.virtualdiapo_reflecta_transition, 1)

    suspend fun awaitReady() = loaded.await()

    fun play() {
        soundPool.stop(activeStreamId)
        activeStreamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() = soundPool.release()

    private var activeStreamId: Int = 0

    companion object {
        const val SLIDE_APPEAR_TIME_MS = 1_690L
    }
}
