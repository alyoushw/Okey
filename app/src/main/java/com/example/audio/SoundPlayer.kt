package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.sin

/**
 * High-performance, low-latency audio player using SoundPool.
 * Enables exact per-tick volume scaling from very subtle/low (0.15f)
 * to full resonant urgency (1.0f) as the timer counts down to 10s and finishes.
 */
class SoundPlayer(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(audioAttributes)
        .build()

    private var soundWoodNormal = 0
    private var soundWoodUrgent = 0
    private var soundDigitalNormal = 0
    private var soundDigitalUrgent = 0
    private var soundClick = 0
    private var soundClassic = 0
    private var soundTimeUp = 0
    private var soundFanfare = 0

    init {
        scope.launch {
            try {
                soundWoodNormal = generateAndLoadWav("wood_normal.wav", freq = 520f, decay = 110.0, durationMs = 35)
                soundWoodUrgent = generateAndLoadWav("wood_urgent.wav", freq = 740f, decay = 85.0, durationMs = 45)
                soundDigitalNormal = generateAndLoadWav("digital_norm.wav", freq = 1200f, decay = 60.0, durationMs = 35)
                soundDigitalUrgent = generateAndLoadWav("digital_urg.wav", freq = 1600f, decay = 40.0, durationMs = 50)
                soundClick = generateAndLoadWav("click.wav", freq = 900f, decay = 150.0, durationMs = 25)
                soundClassic = generateAndLoadWav("classic.wav", freq = 1050f, decay = 55.0, durationMs = 40)
                soundTimeUp = generateAndLoadTimeUpWav("time_up.wav")
                soundFanfare = generateAndLoadFanfareWav("fanfare_v2.wav")
            } catch (e: Exception) {
                Log.e("SoundPlayer", "Failed to init SoundPool sounds", e)
            }
        }
    }

    fun playTick(
        enabled: Boolean,
        toneType: String = "WOOD",
        volumeFactor: Float = 0.6f,
        isUrgent: Boolean = false,
        pitchFactor: Float = 1.0f,
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false
    ) {
        if (!enabled || muteAll || masterVolume <= 0f) return

        val effVol = (volumeFactor * masterVolume).coerceIn(0f, 1f)
        val finalPitch = (if (isUrgent && pitchFactor == 1.0f) 1.06f else pitchFactor).coerceIn(0.5f, 2.0f)

        val soundId = when (toneType) {
            "WOOD" -> if (isUrgent) soundWoodUrgent else soundWoodNormal
            "DIGITAL" -> if (isUrgent) soundDigitalUrgent else soundDigitalNormal
            "CLICK" -> soundClick
            else -> soundClassic
        }

        if (soundId != 0) {
            soundPool.play(soundId, effVol, effVol, 1, 0, finalPitch)
        }
    }

    fun playWarning(
        enabled: Boolean,
        volumeFactor: Float = 1.0f,
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false
    ) {
        if (!enabled || muteAll || masterVolume <= 0f) return
        val effVol = (volumeFactor * masterVolume).coerceIn(0f, 1f)
        val soundId = if (soundDigitalUrgent != 0) soundDigitalUrgent else soundClassic
        if (soundId != 0) {
            soundPool.play(soundId, effVol, effVol, 2, 0, 1.15f)
        }
    }

    fun playTimeUp(enabled: Boolean, masterVolume: Float = 1.0f, muteAll: Boolean = false) {
        if (!enabled || muteAll || masterVolume <= 0f) return
        val vol = masterVolume.coerceIn(0f, 1f)
        if (soundTimeUp != 0) {
            soundPool.play(soundTimeUp, vol, vol, 3, 0, 1.0f)
        }
    }

    fun playCelebrationFanfare(enabled: Boolean, masterVolume: Float = 1.0f, muteAll: Boolean = false) {
        if (!enabled || muteAll || masterVolume <= 0f) return
        val vol = masterVolume.coerceIn(0f, 1f)
        if (soundFanfare != 0) {
            soundPool.play(soundFanfare, vol, vol, 2, 0, 1.0f)
        }
    }

    fun stopAll() {
        soundPool.autoPause()
    }

    private fun generateAndLoadWav(filename: String, freq: Float, decay: Double, durationMs: Int): Int {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(100)
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t * decay)
            // Blend fundamental frequency with a crisp overtone for a punchy, tactile click/knock
            val sample = (sin(2.0 * Math.PI * freq * t) * 0.75 + sin(4.0 * Math.PI * freq * t) * 0.25) * envelope * Short.MAX_VALUE * 0.95
            pcm[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val file = File(context.cacheDir, filename)
        writePcmToWav(file, pcm, sampleRate)
        return soundPool.load(file.absolutePath, 1)
    }

    private fun generateAndLoadTimeUpWav(filename: String): Int {
        val sampleRate = 22050
        val durationMs = 450
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val pcm = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val freq = 460.0 + sin(t * 35.0) * 120.0
            val env = if (t < 0.04) t / 0.04 else (1.0 - (t / (durationMs / 1000.0)))
            val sample = sin(2.0 * Math.PI * freq * t) * env * Short.MAX_VALUE * 0.95
            pcm[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val file = File(context.cacheDir, filename)
        writePcmToWav(file, pcm, sampleRate)
        return soundPool.load(file.absolutePath, 1)
    }

    /** A short triumphant brass-style tune (about four seconds) with bass and drum hits. */
    private fun generateAndLoadFanfareWav(filename: String): Int {
        val sampleRate = 22050
        val totalSec = 4.0
        val mix = DoubleArray((sampleRate * totalSec).toInt())
        val G4 = 392.0; val C5 = 523.25; val D5 = 587.33; val E5 = 659.25; val F5 = 698.46; val G5 = 783.99
        val C3 = 130.81; val G2 = 98.0
        // start, duration, frequency, loudness
        val notes = listOf(
            doubleArrayOf(0.00, 0.12, G4, 0.8), doubleArrayOf(0.14, 0.12, G4, 0.8), doubleArrayOf(0.28, 0.12, G4, 0.8),
            doubleArrayOf(0.42, 0.55, C5, 1.0),
            doubleArrayOf(1.00, 0.15, G4, 0.8), doubleArrayOf(1.17, 0.15, C5, 0.85),
            doubleArrayOf(1.34, 0.55, E5, 1.0),
            doubleArrayOf(1.92, 0.14, D5, 0.85), doubleArrayOf(2.08, 0.14, E5, 0.85), doubleArrayOf(2.24, 0.14, F5, 0.9),
            doubleArrayOf(2.40, 1.45, G5, 1.0), doubleArrayOf(2.40, 1.45, E5, 0.55), doubleArrayOf(2.40, 1.45, C5, 0.5),
            doubleArrayOf(0.42, 0.55, C3, 0.7), doubleArrayOf(1.34, 0.55, C3, 0.7),
            doubleArrayOf(1.92, 0.46, G2, 0.7), doubleArrayOf(2.40, 1.45, C3, 0.8)
        )
        for (n in notes) {
            val begin = (n[0] * sampleRate).toInt()
            val length = (n[1] * sampleRate).toInt()
            for (i in 0 until length) {
                val index = begin + i
                if (index >= mix.size) break
                val t = i.toDouble() / sampleRate
                val attack = (t / 0.015).coerceAtMost(1.0)
                val release = ((n[1] - t) / 0.06).coerceIn(0.0, 1.0)
                val body = if (t < 0.08) 1.0 - t * 3.0 else 0.76
                val vibrato = if (t > 0.25) sin(2.0 * Math.PI * 5.5 * t) * 0.004 else 0.0
                val phase = 2.0 * Math.PI * n[2] * (1.0 + vibrato) * t
                val brass = sin(phase) + 0.55 * sin(2 * phase) + 0.35 * sin(3 * phase) + 0.2 * sin(4 * phase) + 0.1 * sin(5 * phase)
                mix[index] += brass * attack * release * body * n[3]
            }
        }
        val random = java.util.Random(7)
        for (hit in listOf(0.42, 1.34, 1.92, 2.24, 2.40, 2.40 + 0.09)) {
            val begin = (hit * sampleRate).toInt()
            for (i in 0 until (0.12 * sampleRate).toInt()) {
                val index = begin + i
                if (index >= mix.size) break
                val t = i.toDouble() / sampleRate
                mix[index] += (random.nextDouble() * 2 - 1) * exp(-t * 35.0) * 0.9 + sin(2 * Math.PI * 70 * t) * exp(-t * 18.0) * 1.4
            }
        }
        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        val pcm = ShortArray(mix.size) { i ->
            (mix[i] / peak * Short.MAX_VALUE * 0.9).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val file = File(context.cacheDir, filename)
        writePcmToWav(file, pcm, sampleRate)
        return soundPool.load(file.absolutePath, 1)
    }

    private fun writePcmToWav(file: File, pcm: ShortArray, sampleRate: Int) {
        if (file.length() == pcm.size * 2L + 44L) return
        val temporary = File.createTempFile("effect_", ".wav", context.cacheDir)
        try {
            writeWaveData(temporary, pcm, sampleRate)
            check(temporary.renameTo(file)) { "Could not cache sound effect" }
        } finally { temporary.delete() }
    }

    private fun writeWaveData(file: File, pcm: ShortArray, sampleRate: Int) {
        val totalAudioLen = pcm.size * 2
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2

        FileOutputStream(file).use { out ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // PCM
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = (channels * 2).toByte()
            header[33] = 0
            header[34] = 16 // 16 bit
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
            out.write(header)

            val byteBuf = ByteArray(pcm.size * 2)
            for (i in pcm.indices) {
                byteBuf[i * 2] = (pcm[i].toInt() and 0xff).toByte()
                byteBuf[i * 2 + 1] = ((pcm[i].toInt() shr 8) and 0xff).toByte()
            }
            out.write(byteBuf)
        }
    }

    fun release() {
        scope.cancel()
        try {
            soundPool.release()
        } catch (_: Exception) {}
    }
}
