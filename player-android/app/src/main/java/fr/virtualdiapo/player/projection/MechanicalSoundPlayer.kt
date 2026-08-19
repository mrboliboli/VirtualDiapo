package fr.virtualdiapo.player.projection

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import fr.virtualdiapo.player.R
import kotlinx.coroutines.CompletableDeferred

class MechanicalSoundPlayer(context: Context) {
    private val loaded = CompletableDeferred<Boolean>()
    @Volatile private var ready = false
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
                ready = status == 0
                if (!ready) Log.w(TAG, "Son mécanique indisponible (SoundPool status=$status)")
                loaded.complete(ready)
            }
        }
    private val soundId = soundPool.load(context.applicationContext, R.raw.virtualdiapo_reflecta_transition, 1).also {
        if (it == 0) {
            Log.w(TAG, "SoundPool a refusé le chargement du son mécanique")
            loaded.complete(false)
        }
    }

    suspend fun awaitReady() = loaded.await()

    fun play() {
        if (!ready) return
        soundPool.stop(activeStreamId)
        activeStreamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() = soundPool.release()

    private var activeStreamId: Int = 0

    companion object {
        private const val TAG = "MechanicalSoundPlayer"
        const val SLIDE_APPEAR_TIME_MS = 1_690L
    }
}
