package fr.virtualdiapo.player.projection

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.PI

class MechanicalSoundPlayer {
    private val sampleRate = 48_000
    private val samples = makeClick()
    private val bufferSize = max(
        samples.size * Short.SIZE_BYTES,
        AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ),
    )
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
        .apply { setVolume(1.0f) }

    @Synchronized
    fun play() {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
        }
        track.flush()
        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written > 0) {
            track.play()
        } else {
            Log.e("VirtualDiapoAudio", "Échec AudioTrack.write: $written")
        }
    }

    fun release() = track.release()

    private fun makeClick(): ShortArray {
        var random = 0x13579BDF
        var previousNoise = 0.0
        return ShortArray((sampleRate * 0.30).toInt()) { index ->
            random = random * 1_103_515_245 + 12_345
            val noise = ((random ushr 16) and 0x7fff) / 16_384.0 - 1.0
            val softenedNoise = noise * 0.35 + previousNoise * 0.65
            previousNoise = noise
            val time = index.toDouble() / sampleRate

            val firstBody = if (time < 0.14) {
                val attack = (time / 0.004).coerceAtMost(1.0)
                attack * (
                    sin(2.0 * PI * 165.0 * time) * exp(-time * 24.0) * 0.46 +
                        sin(2.0 * PI * 238.0 * time + 0.35) * exp(-time * 34.0) * 0.24
                    )
            } else 0.0
            val firstTexture = if (time < 0.032) softenedNoise * exp(-time * 75.0) * 0.09 else 0.0

            val travelTime = time - 0.055
            val travel = if (travelTime in 0.0..0.115) {
                val envelope = sin(PI * travelTime / 0.115).coerceAtLeast(0.0)
                (softenedNoise * 0.018 + sin(2.0 * PI * 92.0 * travelTime) * 0.012) * envelope
            } else 0.0

            val lockTime = time - 0.178
            val lockBody = if (lockTime in 0.0..0.105) {
                val attack = (lockTime / 0.0025).coerceAtMost(1.0)
                attack * (
                    sin(2.0 * PI * 285.0 * lockTime) * exp(-lockTime * 38.0) * 0.34 +
                        sin(2.0 * PI * 470.0 * lockTime + 0.6) * exp(-lockTime * 58.0) * 0.22
                    )
            } else 0.0
            val snap = if (lockTime in 0.0..0.012) softenedNoise * exp(-lockTime * 150.0) * 0.13 else 0.0

            ((firstBody + firstTexture + travel + lockBody + snap) * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
