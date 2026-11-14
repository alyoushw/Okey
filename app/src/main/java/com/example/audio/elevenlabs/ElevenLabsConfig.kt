package com.example.audio.elevenlabs

import android.content.Context
import android.content.SharedPreferences

data class ElevenLabsSettings(
    val voiceId: String = "ECc3xM44reI7J5jdm8jB",
    val modelId: String = "eleven_multilingual_v2",
    val stability: Float = 0.5f,
    val similarityBoost: Float = 0.75f,
    val style: Float = 0.0f,
    val speed: Float = 1.0f
)

class ElevenLabsConfigManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("elevenlabs_config_prefs", Context.MODE_PRIVATE)

    fun getSettings(): ElevenLabsSettings {
        return ElevenLabsSettings(
            voiceId = prefs.getString("voice_id", "ECc3xM44reI7J5jdm8jB") ?: "ECc3xM44reI7J5jdm8jB",
            modelId = prefs.getString("model_id", "eleven_multilingual_v2") ?: "eleven_multilingual_v2",
            stability = prefs.getFloat("stability", 0.5f),
            similarityBoost = prefs.getFloat("similarity_boost", 0.75f),
            style = prefs.getFloat("style", 0.0f),
            speed = prefs.getFloat("speed", 1.0f)
        )
    }

    fun saveSettings(settings: ElevenLabsSettings) {
        prefs.edit()
            .putString("voice_id", settings.voiceId.trim())
            .putString("model_id", settings.modelId.trim())
            .putFloat("stability", settings.stability)
            .putFloat("similarity_boost", settings.similarityBoost)
            .putFloat("style", settings.style)
            .putFloat("speed", settings.speed)
            .apply()
    }

    fun getTotalCharactersUsed(): Long {
        return prefs.getLong("total_chars_used", 0L)
    }

    fun recordCharactersUsed(chars: Int) {
        val current = getTotalCharactersUsed()
        prefs.edit().putLong("total_chars_used", current + chars).apply()
    }

    fun resetCharactersCounter() {
        prefs.edit().putLong("total_chars_used", 0L).apply()
    }
}
