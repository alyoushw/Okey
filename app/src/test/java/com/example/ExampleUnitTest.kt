package com.example

import com.example.data.model.CustomSoundSlot
import com.example.data.model.SoundSlotCategory
import com.example.data.model.SoundSourceType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun customSoundSlot_sourcePriority_uploadedAudioTakesHighestPrecedence() {
        val slot = CustomSoundSlot(
            id = "test_slot_uploaded",
            title = "اختبار رفع ملف",
            description = "وصف",
            category = SoundSlotCategory.TIME_UP,
            customText = "خَلَصْ وَقْتَكْ",
            recordingPath = "/path/to/rec.m4a",
            uploadedAudioPath = "/path/to/human_voice.mp3",
            uploadedAudioName = "human_voice.mp3"
        )
        // Uploaded audio file must take highest priority even if recording is present
        assertEquals(SoundSourceType.UPLOADED, slot.activeSource)
        assertEquals("/path/to/human_voice.mp3", slot.effectiveAudioPath)
    }

    @Test
    fun customSoundSlot_sourcePriority_elevenlabsTakesPrecedenceOverText() {
        val slot = CustomSoundSlot(
            id = "test_slot_el",
            title = "اختبار ElevenLabs",
            description = "وصف",
            category = SoundSlotCategory.TIME_UP,
            customText = "اسحب ثلاث يا غشيم",
            elevenLabsAudioPath = "/path/to/el_cache.mp3",
            lastGeneratedChars = 19
        )
        // ElevenLabs takes priority over diacritic text / TTS fallback
        assertEquals(SoundSourceType.ELEVENLABS, slot.activeSource)
        assertEquals("/path/to/el_cache.mp3", slot.effectiveAudioPath)
    }

    @Test
    fun customSoundSlot_sourcePriority_recordingTakesPrecedenceOverElevenLabs() {
        val slot = CustomSoundSlot(
            id = "test_slot_rec_el",
            title = "اختبار",
            description = "وصف",
            category = SoundSlotCategory.TIME_UP,
            customText = "اسحب ثلاث يا غشيم",
            recordingPath = "/path/to/rec.m4a",
            elevenLabsAudioPath = "/path/to/el_cache.mp3"
        )
        // User's own recording takes priority over generated AI voice
        assertEquals(SoundSourceType.RECORDING, slot.activeSource)
        assertEquals("/path/to/rec.m4a", slot.effectiveAudioPath)
    }

    @Test
    fun elevenLabsSettings_defaultsMatchRequirements() {
        val settings = com.example.audio.elevenlabs.ElevenLabsSettings()
        assertEquals("ECc3xM44reI7J5jdm8jB", settings.voiceId)
        assertEquals("eleven_multilingual_v2", settings.modelId)
        assertEquals(0.5f, settings.stability, 0.001f)
        assertEquals(0.75f, settings.similarityBoost, 0.001f)
        assertEquals(0.0f, settings.style, 0.001f)
        assertEquals(1.0f, settings.speed, 0.001f)
    }

    @Test
    fun customSoundSlot_sourcePriority_recordingTakesPrecedenceOverText() {
        val slot = CustomSoundSlot(
            id = "test_slot",
            title = "اختبار",
            description = "وصف",
            category = SoundSlotCategory.TIME_UP,
            customText = "خَلَصْ وَقْتَكْ يَا كَبِيرْ",
            recordingPath = "/path/to/rec.m4a"
        )
        // Recording must have priority over text
        assertEquals(SoundSourceType.RECORDING, slot.activeSource)
    }

    @Test
    fun customSoundSlot_sourcePriority_diacriticTextSecondPriority() {
        val slot = CustomSoundSlot(
            id = "test_slot_text",
            title = "اختبار تشكيل",
            description = "وصف",
            category = SoundSlotCategory.WARNING_10S,
            customText = "عَشْر ثَوَانِي بِسْرْعَة يَا وَحْش",
            recordingPath = null
        )
        // Diacritic text takes priority over default when recording is absent
        assertEquals(SoundSourceType.TEXT, slot.activeSource)
    }

    @Test
    fun customSoundSlot_sourcePriority_defaultFallback() {
        val slot = CustomSoundSlot(
            id = "test_slot_default",
            title = "اختبار افتراضي",
            description = "وصف",
            category = SoundSlotCategory.COUNTDOWN_5,
            customText = "",
            recordingPath = null
        )
        assertEquals(SoundSourceType.DEFAULT, slot.activeSource)
    }

    @Test
    fun customSoundSlot_textFormatting_preservesArabicDiacritics() {
        val originalText = "يَلَّا يَا {الاسم}، دَوْرَكْ الحِينْ!"
        val slot = CustomSoundSlot(
            id = "turn_player_1",
            title = "دور لاعب 1",
            description = "وصف",
            category = SoundSlotCategory.TURN_START,
            customText = originalText,
            speechPitch = 1.15f,
            speechRate = 1.05f,
            voiceGender = "MALE"
        )

        assertEquals(originalText, slot.customText)
        assertEquals("MALE", slot.voiceGender)
        assertEquals(1.15f, slot.speechPitch)
    }
}

