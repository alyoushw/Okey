package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

data class VictoryTrack(val id: String, val title: String, val description: String)

/**
 * Victory drums. The winner's call ("مبرووووك الفوز يا {الاسم}!") is spoken over the beat,
 * [VOICE_DELAY_MS] after it starts. Beats ship in assets/victory_music.
 */
object VictoryMusic {
    const val NONE = "none"
    const val SHORT_TUNE = "short_tune"
    const val DEFAULT = "beat_zaffa"
    const val VOICE_DELAY_MS = 1500L
    private const val BEAT_LEVEL = 0.8f

    private val beats = listOf(
        VictoryTrack("beat_zaffa", "زفّة الأبطال 🪘", "طبل ودربوكة وتصفيق وزغاريد"),
        VictoryTrack("beat_stadium_war", "طبول الحرب 🥁", "طبول ملعب ضخمة وبوق وجمهور يهدر"),
        VictoryTrack("beat_stadium_brass", "مارش الملعب 🎺", "طبول مارش ونفخ نحاسي وجمهور يهتف"),
    )
    val extras = listOf(
        VictoryTrack(SHORT_TUNE, "نغمة قصيرة", "التهنئة ثم نغمة احتفال قصيرة"),
        VictoryTrack(NONE, "بدون موسيقى", "التهنئة باسم الفائز فقط"),
    )

    private val handler = Handler(Looper.getMainLooper())
    private var beatPlayer: MediaPlayer? = null
    private var voicePlayer: MediaPlayer? = null

    /** Beats actually bundled in this build, in display order. */
    fun available(context: Context): List<VictoryTrack> {
        val files = try {
            context.assets.list("victory_music")?.toSet().orEmpty()
        } catch (_: Exception) { emptySet() }
        return beats.filter { "${it.id}.mp3" in files }
    }

    /** The saved choice, or the default beat when the saved one no longer exists. */
    fun resolve(context: Context, id: String): String =
        if (id == NONE || id == SHORT_TUNE || available(context).any { it.id == id }) id
        else available(context).firstOrNull()?.id ?: SHORT_TUNE

    fun titleOf(context: Context, id: String): String =
        (available(context) + extras).firstOrNull { it.id == resolve(context, id) }?.title ?: ""

    /** Starts the beat. Returns false when it is not bundled. */
    fun playBeat(context: Context, id: String, volume: Float, onFinished: () -> Unit = {}): Boolean {
        stop()
        val media = open(context, "victory_music/$id.mp3", volume * BEAT_LEVEL) ?: return false
        media.setOnCompletionListener {
            if (beatPlayer === it) beatPlayer = null
            it.release()
            onFinished()
        }
        media.start()
        beatPlayer = media
        return true
    }

    /** Settings preview: the beat with the generic "مبروك الفوز" call on top. */
    fun preview(context: Context, id: String, volume: Float, onFinished: () -> Unit) {
        if (!playBeat(context, id, volume, onFinished)) { onFinished(); return }
        handler.postDelayed({
            if (beatPlayer == null) return@postDelayed
            voicePlayer = openVoice(context, volume)?.also { voice ->
                voice.setOnCompletionListener { it.release(); if (voicePlayer === it) voicePlayer = null }
                voice.start()
            }
        }, VOICE_DELAY_MS)
    }

    private fun openVoice(context: Context, volume: Float): MediaPlayer? =
        open(context, "game_audio/victory.mp3", volume)

    private fun open(context: Context, asset: String, volume: Float): MediaPlayer? = try {
        val media = MediaPlayer()
        media.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        )
        context.assets.openFd(asset).use { media.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        val level = volume.coerceIn(0f, 1f)
        media.setVolume(level, level)
        media.prepare()
        media
    } catch (_: Exception) {
        null
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        listOf(beatPlayer, voicePlayer).forEach { media ->
            media?.let {
                try { it.stop() } catch (_: Exception) {}
                it.release()
            }
        }
        beatPlayer = null
        voicePlayer = null
    }
}
