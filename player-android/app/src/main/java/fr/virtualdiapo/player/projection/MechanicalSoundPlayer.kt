package fr.virtualdiapo.player.projection

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.exp

class MechanicalSoundPlayer {
    private val sampleRate = 22_050
    private val samples = makeClick()
    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setBufferSizeInBytes(samples.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
        .apply { write(samples, 0, samples.size) }

    fun play() {
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    fun release() = track.release()

    private fun makeClick(): ShortArray {
        var random = 0x13579BDF
        return ShortArray((sampleRate * 0.11).toInt()) { index ->
            random = random * 1_103_515_245 + 12_345
            val noise = ((random ushr 16) and 0x7fff) / 16_384.0 - 1.0
            val time = index.toDouble() / sampleRate
            val firstImpact = if (time < 0.025) noise * exp(-time * 120.0) else 0.0
            val secondTime = time - 0.052
            val secondImpact = if (secondTime in 0.0..0.035) noise * exp(-secondTime * 95.0) else 0.0
            ((firstImpact * 0.75 + secondImpact * 0.5) * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}

