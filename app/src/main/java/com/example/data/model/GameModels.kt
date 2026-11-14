package com.example.data.model

enum class ScoringRule(val titleAr: String, val descriptionAr: String) {
    LOWEST_WINS("الأقل نقاطاً هو الفائز", "حساب عقوبات وأخطاء أحجار أوكي المتبقية"),
    HIGHEST_WINS("الأعلى نقاطاً هو الفائز", "تجميع النقاط والمجموع الأكبر يفوز")
}

data class Player(
    val id: Int,
    val name: String,
    val colorHex: Long,
    val totalScore: Int = 0,
    val phoneticName: String = ""
)

data class RoundEntry(
    val roundNumber: Int,
    val scores: Map<Int, Int> // PlayerId -> points in this round
)

data class ActiveGameState(
    val players: List<Player>,
    val rounds: List<RoundEntry>,
    val scoringRule: ScoringRule = ScoringRule.LOWEST_WINS,
    val isFinished: Boolean = false
)

data class PlayerResult(
    val id: Int,
    val name: String,
    val colorHex: Long,
    val totalScore: Int,
    val rank: Int,
    val isWinner: Boolean
)

enum class SoundSlotCategory {
    TURN_START,      // عبارة بداية دور اللاعب
    WARNING_10S,     // عبارة التنبيه عند باقي 10 ثوانٍ
    COUNTDOWN_5,     // العد التنازلي: 5
    COUNTDOWN_4,     // العد التنازلي: 4
    COUNTDOWN_3,     // العد التنازلي: 3
    COUNTDOWN_2,     // العد التنازلي: 2
    COUNTDOWN_1,     // العد التنازلي: 1
    TIME_UP,         // عبارة انتهاء الوقت
    WIN_VICTORY,     // صوت الفوز في النتيجة النهائية
    CUSTOM           // عبارات إضافية
}

enum class SoundSourceType(val labelAr: String) {
    UPLOADED("ملف مرفوع 📁"),
    RECORDING("تسجيلي 🎙️"),
    ELEVENLABS("صوت ElevenLabs 🤖"),
    TEXT("نص مشكّل ✍️"),
    DEFAULT("صامت (لم يُعيّن)")
}

data class CustomSoundSlot(
    val id: String,
    val title: String,
    val description: String,
    val category: SoundSlotCategory,
    val customText: String = "",
    val recordingPath: String? = null,
    val uploadedAudioPath: String? = null,
    val uploadedAudioName: String? = null,
    val elevenLabsAudioPath: String? = null,
    val lastGeneratedChars: Int = 0,
    val lastGeneratedTimestamp: Long = 0L,
    val speechRate: Float = 1.12f,
    val speechPitch: Float = 1.0f,
    val voiceGender: String = "MALE",
    val isEnabled: Boolean = true,
    val targetPlayerId: Int? = null
) {
    val activeSource: SoundSourceType
        get() = when {
            !uploadedAudioPath.isNullOrBlank() -> SoundSourceType.UPLOADED
            !recordingPath.isNullOrBlank() -> SoundSourceType.RECORDING
            !elevenLabsAudioPath.isNullOrBlank() -> SoundSourceType.ELEVENLABS
            customText.isNotBlank() -> SoundSourceType.TEXT
            else -> SoundSourceType.DEFAULT
        }

    val effectiveAudioPath: String?
        get() = uploadedAudioPath?.takeIf { it.isNotBlank() }
            ?: recordingPath?.takeIf { it.isNotBlank() }
            ?: elevenLabsAudioPath?.takeIf { it.isNotBlank() }
}

