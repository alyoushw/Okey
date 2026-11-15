package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

data class VictoryTrack(val id: String, val title: String)

/** Celebration music played after "مبروك الفوز". Tracks ship in assets/victory_music. */
object VictoryMusic {
    const val NONE = "none"
    const val SHORT_TUNE = "short_tune"
    const val DEFAULT = "drums_heroes"

    private val titles = linkedMapOf(
        "drums_heroes" to "طبول الأبطال 🥁",
        "gulf_zaffa" to "زفّة خليجية 🪘",
        "arena_hype" to "حماس الملعب 🎸",
        "epic_triumph" to "انتصار ملحمي 🎺"
    )

    private var player: MediaPlayer? = null

    /** Tracks actually bundled in this build, in display order. */
    fun available(context: Context): List<VictoryTrack> {
        val files = try {
            context.assets.list("victory_music")?.toSet().orEmpty()
        } catch (_: Exception) { emptySet() }
        return titles.filterKeys { "$it.mp3" in files }.map { (id, title) -> VictoryTrack(id, title) }
    }

    /** Plays the chosen track. Returns false when it is not bundled, so the caller can fall back. */
    fun play(context: Context, id: String, volume: Float, onFinished: () -> Unit = {}): Boolean {
        stop()
        return try {
            val descriptor = context.assets.openFd("victory_music/$id.mp3")
            val media = MediaPlayer()
            media.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            descriptor.use { media.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            val level = volume.coerceIn(0f, 1f)
            media.setVolume(level, level)
            media.setOnCompletionListener {
                if (player === it) player = null
                it.release()
                onFinished()
            }
            media.prepare()
            media.start()
            player = media
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (_: Exception) { false }

    fun stop() {
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        player = null
    }
}
