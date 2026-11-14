package com.example.audio

import android.content.Context
import android.util.Log
import com.example.audio.elevenlabs.ElevenLabsService
import com.example.data.model.CustomSoundSlot
import com.example.data.model.SoundSlotCategory
import java.io.File

/**
 * Natural Human Speech System for Okey Companion with Full Customization.
 *
 * Rules:
 * - Only user-provided sounds are played: uploaded files, recordings, or ElevenLabs generated audio.
 * - If a slot is unconfigured or not generated: pure silence (onFinished called immediately).
 * - No dependency on native Android TextToSpeech or bundled static audio assets.
 */
class SpeechManager(private val context: Context) {
    val recorderHelper = AudioRecorderHelper(context)
    val elevenLabsService = ElevenLabsService(context)

    /** Fixed game phrases ship in assets/game_audio and never call a remote service. */
    fun playBundled(id: String, volume: Float = 1f, onFinished: (() -> Unit)? = null) {
        val file = try {
            val target = File(context.cacheDir, "bundled_audio/${com.example.BuildConfig.VERSION_CODE}/$id.mp3")
            if (!target.exists()) {
                target.parentFile?.mkdirs()
                val temporary = File.createTempFile("bundle_", ".tmp", target.parentFile)
                try {
                    context.assets.open("game_audio/$id.mp3").use { input ->
                        temporary.outputStream().use { input.copyTo(it) }
                    }
                    if (!temporary.renameTo(target)) throw java.io.IOException("Could not cache game audio")
                } finally { temporary.delete() }
            }
            target.takeIf { it.length() > 0 }
        } catch (_: java.io.IOException) { null }
        if (file == null) onFinished?.invoke()
        else recorderHelper.playAudio(file.absolutePath, volume) { onFinished?.invoke() }
    }

    /**
     * Plays a specific CustomSoundSlot respecting the strict priority:
     * 1. Uploaded Audio File -> 2. Microphone Recording -> 3. ElevenLabs Generated Audio
     * If no audio is available: plays nothing (silence) and triggers onFinished.
     */
    fun playSlot(
        slot: CustomSoundSlot,
        playerName: String = "",
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        if (!slot.isEnabled || muteAll) {
            onFinished?.invoke()
            return
        }

        val effVolume = masterVolume.coerceIn(0.0f, 1.0f)
        val processedText = slot.customText.replace("{الاسم}", playerName.trim()).trim()

        // 1. Check for Uploaded Audio File (Highest priority)
        if (!slot.uploadedAudioPath.isNullOrBlank()) {
            val file = File(slot.uploadedAudioPath)
            if (file.exists() && file.length() > 0) {
                recorderHelper.playAudio(slot.uploadedAudioPath, effVolume) {
                    onFinished?.invoke()
                }
                return
            }
        }

        // 2. Check for User Recording
        if (!slot.recordingPath.isNullOrBlank()) {
            val file = File(slot.recordingPath)
            if (file.exists() && file.length() > 0) {
                recorderHelper.playAudio(slot.recordingPath, effVolume) {
                    onFinished?.invoke()
                }
                return
            }
        }

        // 3. Slot-level ElevenLabs audio path (if explicitly generated/stored)
        if (!slot.elevenLabsAudioPath.isNullOrBlank()) {
            val elFile = File(slot.elevenLabsAudioPath)
            if (elFile.exists() && elFile.length() > 0) {
                recorderHelper.playAudio(slot.elevenLabsAudioPath, effVolume) {
                    onFinished?.invoke()
                }
                return
            }
        }

        // Bundled defaults are shared by every installation; dynamic names use the disk cache.
        if (slot.category != SoundSlotCategory.TURN_START && slot.category != SoundSlotCategory.CUSTOM) {
            playBundled(slot.id, effVolume, onFinished)
            return
        }

        // 4. Check for ElevenLabs Generated Audio for this specific text (disk cache match)
        val textToCheck = if (processedText.isNotBlank()) {
            processedText
        } else {
            when (slot.category) {
                SoundSlotCategory.COUNTDOWN_5 -> "خَمْسَة"
                SoundSlotCategory.COUNTDOWN_4 -> "أَرْبَعَة"
                SoundSlotCategory.COUNTDOWN_3 -> "ثَلَاثَة"
                SoundSlotCategory.COUNTDOWN_2 -> "اِثْنَيْن"
                SoundSlotCategory.COUNTDOWN_1 -> "وَاحِد"
                SoundSlotCategory.TIME_UP -> "اِسْحَبْ ثَلَاثْ يَا غَشِيم!"
                else -> ""
            }
        }

        if (textToCheck.isNotBlank()) {
            val settings = elevenLabsService.getConfigManager().getSettings()
            val cachedEl = elevenLabsService.getCachedFile(textToCheck, settings)
            if (cachedEl != null && cachedEl.exists() && cachedEl.length() > 0) {
                recorderHelper.playAudio(cachedEl.absolutePath, effVolume) {
                    onFinished?.invoke()
                }
                return
            }
        }

        // 5. No sound generated or recorded: silence
        onFinished?.invoke()
    }

    /**
     * Resolves turn start sound for a player.
     * When announcementMode is PLAYER_NAME, the player's name takes priority over any
     * uploaded file or recording attached to the general turn slot ("turn_general").
     */
    fun playTurnStart(
        playerId: Int,
        playerName: String,
        phoneticName: String,
        slots: List<CustomSoundSlot>,
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false,
        announcementMode: String = "PLAYER_NAME",
        customPhrase: String = "اللي بعده",
        onFinished: (() -> Unit)? = null
    ) {
        if (muteAll) {
            onFinished?.invoke()
            return
        }

        val effVolume = masterVolume.coerceIn(0.0f, 1.0f)
        val settings = elevenLabsService.getConfigManager().getSettings()

        // Mode 2: User's custom word/phrase without player name (e.g. "اللي بعده")
        if (announcementMode.equals("CUSTOM_PHRASE", ignoreCase = true)) {
            val phraseText = customPhrase.ifBlank { "اللي بعده" }.trim()
            val cachedEl = elevenLabsService.getCachedFile(phraseText, settings)
            if (cachedEl != null && cachedEl.exists() && cachedEl.length() > 0) {
                recorderHelper.playAudio(cachedEl.absolutePath, effVolume) {
                    onFinished?.invoke()
                }
            } else {
                onFinished?.invoke()
            }
            return
        }

        // Mode 1: Player Name announcement (exact player name without diacritics or automatic alterations)
        val exactName = playerName.trim()

        // If a specific slot was created specifically for this player ID, respect it
        val specificSlot = slots.find { it.targetPlayerId == playerId && it.isEnabled }
        if (specificSlot != null && (!specificSlot.uploadedAudioPath.isNullOrBlank() ||
                    !specificSlot.recordingPath.isNullOrBlank() ||
                    !specificSlot.elevenLabsAudioPath.isNullOrBlank())) {
            playSlot(specificSlot, exactName, masterVolume, muteAll, onFinished)
            return
        }

        // General turn start: Ignore uploaded files or recordings on turn_general in PLAYER_NAME mode!
        // Format the template with the current player's name and play the ElevenLabs audio.
        val generalSlot = slots.find { it.id == "turn_general" && it.isEnabled }
        val rawTemplate = generalSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" }
            ?: "يلا يا {الاسم}، دورك"
        val phrase = rawTemplate.replace("{الاسم}", exactName).trim()

        val cachedEl = elevenLabsService.getCachedFile(phrase, settings)
        if (cachedEl != null && cachedEl.exists() && cachedEl.length() > 0) {
            recorderHelper.playAudio(cachedEl.absolutePath, effVolume) {
                onFinished?.invoke()
            }
        } else {
            // Not cached yet: silence callback (TimerViewModel coordinates on-the-fly generation if needed)
            onFinished?.invoke()
        }
    }

    /**
     * Plays 10-second warning announcement.
     * Only plays if user uploaded a file, recorded audio, or generated ElevenLabs audio.
     */
    fun play10sWarning(
        slots: List<CustomSoundSlot>,
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        if (muteAll) {
            onFinished?.invoke()
            return
        }
        val slot = slots.find { it.category == SoundSlotCategory.WARNING_10S && it.isEnabled }
        if (slot != null) {
            playSlot(slot, "", masterVolume, muteAll, onFinished)
        } else {
            onFinished?.invoke()
        }
    }

    /**
     * Plays countdown number (5 down to 1).
     * Only plays if user uploaded a file, recorded audio, or generated ElevenLabs audio.
     */
    fun playCountdownNumber(
        number: Int,
        slots: List<CustomSoundSlot>,
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        if (muteAll || number !in 1..5) {
            onFinished?.invoke()
            return
        }
        val targetCategory = when (number) {
            5 -> SoundSlotCategory.COUNTDOWN_5
            4 -> SoundSlotCategory.COUNTDOWN_4
            3 -> SoundSlotCategory.COUNTDOWN_3
            2 -> SoundSlotCategory.COUNTDOWN_2
            1 -> SoundSlotCategory.COUNTDOWN_1
            else -> {
                onFinished?.invoke()
                return
            }
        }

        val slot = slots.find { it.category == targetCategory && it.isEnabled }
        if (slot != null) {
            playSlot(slot, "", masterVolume, muteAll, onFinished)
        } else {
            onFinished?.invoke()
        }
    }

    /**
     * Plays time up announcement.
     * Only plays if user uploaded a file, recorded audio, or generated ElevenLabs audio.
     */
    fun playTimeUp(
        slots: List<CustomSoundSlot>,
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        if (muteAll) {
            onFinished?.invoke()
            return
        }
        val slot = slots.find { it.category == SoundSlotCategory.TIME_UP && it.isEnabled }
        if (slot != null) {
            playSlot(slot, "", masterVolume, muteAll, onFinished)
        } else {
            onFinished?.invoke()
        }
    }

    /**
     * Plays victory announcement on final game result.
     * Only plays if user uploaded a file, recorded audio, or generated ElevenLabs audio.
     */
    fun playVictory(
        slots: List<CustomSoundSlot>,
        masterVolume: Float = 1.0f,
        muteAll: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        if (muteAll) {
            onFinished?.invoke()
            return
        }
        val slot = slots.find { it.category == SoundSlotCategory.WIN_VICTORY && it.isEnabled }
        if (slot != null) {
            playSlot(slot, "", masterVolume, muteAll, onFinished)
        } else {
            onFinished?.invoke()
        }
    }

    fun stop() {
        recorderHelper.stopPlayback()
    }

    fun shutdown() {
        stop()
        recorderHelper.stopRecording()
    }
}
