package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.ActiveGameEntity
import com.example.data.local.AppDatabase
import com.example.data.local.GameRecordEntity
import com.example.data.model.ActiveGameState
import com.example.data.model.Player
import com.example.data.model.PlayerResult
import com.example.data.model.CustomSoundSlot
import com.example.data.model.RoundEntry
import com.example.data.model.ScoringRule
import com.example.data.model.SoundSlotCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

data class PreGenState(
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val isError: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Shared singleton managing active players list, current turn index, and voice pregeneration.
 * Serves as the single source of truth for both Timer and Scoreboard screens.
 */
object GameSessionManager {
    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private val _activePlayerIndex = MutableStateFlow(0)
    val activePlayerIndex: StateFlow<Int> = _activePlayerIndex.asStateFlow()

    private val _preGenState = MutableStateFlow(PreGenState())
    val preGenState: StateFlow<PreGenState> = _preGenState.asStateFlow()

    private val _sessionVersion = MutableStateFlow(0L)
    val sessionVersion: StateFlow<Long> = _sessionVersion.asStateFlow()

    fun stopCurrentTurn() { _sessionVersion.value += 1 }

    fun setPlayers(newPlayers: List<Player>, resetTurnToFirst: Boolean = false) {
        _players.value = newPlayers
        if (resetTurnToFirst) stopCurrentTurn()
        if (resetTurnToFirst || _activePlayerIndex.value !in newPlayers.indices) {
            _activePlayerIndex.value = 0
        }
    }

    fun nextPlayer(): Player? {
        val current = _players.value
        if (current.isEmpty()) return null
        val nextIdx = (_activePlayerIndex.value + 1) % current.size
        _activePlayerIndex.value = nextIdx
        return current.getOrNull(nextIdx)
    }

    fun selectPlayer(index: Int): Player? {
        val current = _players.value
        if (current.isEmpty()) return null
        val clamped = index.coerceIn(0, current.size - 1)
        _activePlayerIndex.value = clamped
        return current.getOrNull(clamped)
    }

    fun getActivePlayer(): Player {
        val current = _players.value
        val idx = _activePlayerIndex.value
        return current.getOrNull(idx) ?: Player(1, "لاعب 1", 0xFFD32F2F)
    }

    fun updatePlayerColor(playerId: Int, colorHex: Long) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(colorHex = colorHex) else it
        }
    }

    fun updatePlayerPhonetic(playerId: Int, phoneticName: String) {
        _players.value = _players.value.map {
            if (it.id == playerId) it.copy(phoneticName = phoneticName) else it
        }
    }

    fun setPreGenerating(
        active: Boolean,
        progress: Float = 0f,
        message: String? = null,
        isError: Boolean = false,
        errorMessage: String? = null
    ) {
        _preGenState.value = PreGenState(
            isGenerating = active,
            progress = progress.coerceIn(0f, 1f),
            message = message,
            isError = isError,
            errorMessage = errorMessage
        )
    }

    fun dismissPreGen() {
        _preGenState.value = PreGenState()
    }
}

data class SettingsState(
    val defaultTimerSeconds: Int = 30,
    val soundEnabled: Boolean = true,
    val tickSoundEnabled: Boolean = true,
    val warningSoundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val tapToAdvance: Boolean = false,
    val darkThemeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val timerStyle: String = "RING", // RING, TILE, SPEEDOMETER, BAR, WHEEL, NEON, CAPSULE, WAVE
    val timerFontSize: String = "LARGE", // NORMAL, LARGE, EXTRA_LARGE, HUGE
    val tickTone: String = "WOOD", // WOOD, DIGITAL, CLICK, CLASSIC
    val tickSoundMode: String = "ESCALATING", // ESCALATING, FIXED
    val voiceAnnouncePlayer: Boolean = true,
    val turnAnnouncementMode: String = "PLAYER_NAME", // PLAYER_NAME, CUSTOM_PHRASE
    val turnCustomPhrase: String = "اللي بعده",
    val voiceTimeUpPenalty: Boolean = true,
    val voiceGender: String = "MALE", // MALE, FEMALE
    val voiceDialect: String = "NAJDI", // NAJDI, HIJAZI
    val voiceCountdownEnabled: Boolean = true,
    val masterVolume: Float = 1.0f,
    val muteAll: Boolean = false
)

object SettingsRepository {
    private var prefs: SharedPreferences? = null
    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    @Synchronized
    fun init(context: Context): SettingsState {
        val appContext = context.applicationContext
        if (prefs == null) {
            prefs = appContext.getSharedPreferences("okey_prefs", Context.MODE_PRIVATE)
        }
        val current = readFromPrefs()
        _settingsState.value = current
        return current
    }

    private fun readFromPrefs(): SettingsState {
        val p = prefs ?: return SettingsState()
        return SettingsState(
            defaultTimerSeconds = p.getInt("default_timer", 30),
            soundEnabled = p.getBoolean("sound_enabled", true),
            tickSoundEnabled = p.getBoolean("tick_sound_enabled", true),
            warningSoundEnabled = p.getBoolean("warning_sound_enabled", true),
            hapticEnabled = p.getBoolean("haptic_enabled", true),
            keepScreenOn = p.getBoolean("keep_screen_on", true),
            tapToAdvance = p.getBoolean("tap_to_advance", false),
            darkThemeMode = p.getString("dark_theme_mode", "SYSTEM") ?: "SYSTEM",
            timerStyle = p.getString("timer_style", "RING") ?: "RING",
            timerFontSize = p.getString("timer_font_size", p.getString("timer_scale", "LARGE") ?: "LARGE") ?: "LARGE",
            tickTone = p.getString("tick_tone", "WOOD") ?: "WOOD",
            tickSoundMode = p.getString("tick_sound_mode", "ESCALATING") ?: "ESCALATING",
            voiceAnnouncePlayer = p.getBoolean("voice_announce_player", true),
            turnAnnouncementMode = p.getString("turn_announcement_mode", "PLAYER_NAME") ?: "PLAYER_NAME",
            turnCustomPhrase = p.getString("turn_custom_phrase", "اللي بعده") ?: "اللي بعده",
            voiceTimeUpPenalty = p.getBoolean("voice_time_up_penalty", true),
            voiceGender = p.getString("voice_gender", "MALE") ?: "MALE",
            voiceDialect = p.getString("voice_dialect", "NAJDI") ?: "NAJDI",
            voiceCountdownEnabled = p.getBoolean("voice_countdown_enabled", true),
            masterVolume = p.getFloat("master_volume", 1.0f),
            muteAll = p.getBoolean("mute_all", false)
        )
    }

    @Synchronized
    fun getSettings(context: Context? = null): SettingsState {
        context?.let { init(it) }
        val fresh = readFromPrefs()
        _settingsState.value = fresh
        return fresh
    }

    @Synchronized
    fun saveSettings(context: Context?, settings: SettingsState) {
        context?.let { init(it) }
        _settingsState.value = settings
        prefs?.edit()
            ?.putInt("default_timer", settings.defaultTimerSeconds)
            ?.putBoolean("sound_enabled", settings.soundEnabled)
            ?.putBoolean("tick_sound_enabled", settings.tickSoundEnabled)
            ?.putBoolean("warning_sound_enabled", settings.warningSoundEnabled)
            ?.putBoolean("haptic_enabled", settings.hapticEnabled)
            ?.putBoolean("keep_screen_on", settings.keepScreenOn)
            ?.putBoolean("tap_to_advance", settings.tapToAdvance)
            ?.putString("dark_theme_mode", settings.darkThemeMode)
            ?.putString("timer_style", settings.timerStyle)
            ?.putString("timer_font_size", settings.timerFontSize)
            ?.putString("timer_scale", settings.timerFontSize)
            ?.putString("tick_tone", settings.tickTone)
            ?.putString("tick_sound_mode", settings.tickSoundMode)
            ?.putBoolean("voice_announce_player", settings.voiceAnnouncePlayer)
            ?.putString("turn_announcement_mode", settings.turnAnnouncementMode)
            ?.putString("turn_custom_phrase", settings.turnCustomPhrase)
            ?.putBoolean("voice_time_up_penalty", settings.voiceTimeUpPenalty)
            ?.putString("voice_gender", settings.voiceGender)
            ?.putString("voice_dialect", settings.voiceDialect)
            ?.putBoolean("voice_countdown_enabled", settings.voiceCountdownEnabled)
            ?.putFloat("master_volume", settings.masterVolume)
            ?.putBoolean("mute_all", settings.muteAll)
            ?.commit() // Synchronous persistence guarantee
    }

    @Synchronized
    fun resetForTesting(context: Context) {
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("okey_prefs", Context.MODE_PRIVATE)
        prefs?.edit()?.clear()?.commit()
        _settingsState.value = SettingsState()
    }
}

class GameRepository(val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val gameDao = database.gameDao()
    private val prefs: SharedPreferences = context.getSharedPreferences("okey_prefs", Context.MODE_PRIVATE)

    init {
        SettingsRepository.init(context)
    }

    val gameRecords: Flow<List<GameRecordEntity>> = gameDao.getAllRecords()

    fun getSettings(): SettingsState {
        return SettingsRepository.getSettings(context)
    }

    fun saveSettings(settings: SettingsState) {
        SettingsRepository.saveSettings(context, settings)
    }

    fun saveLastPlayerNames(names: List<String>) {
        val array = JSONArray()
        names.forEach { array.put(it) }
        prefs.edit().putString("last_player_names", array.toString()).apply()
    }

    fun getLastPlayerNames(): List<String> {
        val jsonStr = prefs.getString("last_player_names", null) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun loadActiveOrCachedPlayers(): List<Player> = withContext(Dispatchers.IO) {
        val active = loadActiveGame()
        val players = if (active != null && active.players.isNotEmpty()) {
            active.players
        } else {
            // Fallback to recent player names or default 4 players
            val names = getLastPlayerNames().ifEmpty { listOf("لاعب 1", "لاعب 2", "لاعب 3", "لاعب 4") }
            val defaultPalette = listOf(0xFFD32F2F, 0xFF1976D2, 0xFFF57C00, 0xFF2B2D42, 0xFF388E3C, 0xFF7B1FA2)
            names.mapIndexed { idx, name ->
                val savedColor = prefs.getLong("player_color_$idx", defaultPalette[idx % defaultPalette.size])
                val savedPhonetic = prefs.getString("player_phonetic_$idx", "") ?: ""
                Player(id = idx + 1, name = name, colorHex = savedColor, totalScore = 0, phoneticName = savedPhonetic)
            }
        }
        GameSessionManager.setPlayers(players, resetTurnToFirst = false)
        players
    }

    suspend fun updatePlayerColor(playerId: Int, colorHex: Long) = withContext(Dispatchers.IO) {
        prefs.edit().putLong("player_color_${playerId - 1}", colorHex).apply()
        GameSessionManager.updatePlayerColor(playerId, colorHex)
        val active = loadActiveGame()
        if (active != null) {
            val updatedPlayers = active.players.map { p ->
                if (p.id == playerId) p.copy(colorHex = colorHex) else p
            }
            saveActiveGame(active.copy(players = updatedPlayers))
        }
    }

    suspend fun updatePlayerPhoneticName(playerId: Int, phoneticName: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString("player_phonetic_${playerId - 1}", phoneticName).apply()
        GameSessionManager.updatePlayerPhonetic(playerId, phoneticName)
        val active = loadActiveGame()
        if (active != null) {
            val updatedPlayers = active.players.map { p ->
                if (p.id == playerId) p.copy(phoneticName = phoneticName) else p
            }
            saveActiveGame(active.copy(players = updatedPlayers))
        }
    }

    suspend fun saveActiveGame(state: ActiveGameState) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("rule", state.scoringRule.name)
        root.put("finished", state.isFinished)

        val playersArr = JSONArray()
        for (p in state.players) {
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("color", p.colorHex)
            pObj.put("score", p.totalScore)
            pObj.put("phonetic", p.phoneticName)
            playersArr.put(pObj)
        }
        root.put("players", playersArr)

        val roundsArr = JSONArray()
        for (r in state.rounds) {
            val rObj = JSONObject()
            rObj.put("roundNumber", r.roundNumber)
            val scoresObj = JSONObject()
            for ((pid, score) in r.scores) {
                scoresObj.put(pid.toString(), score)
            }
            rObj.put("scores", scoresObj)
            roundsArr.put(rObj)
        }
        root.put("rounds", roundsArr)

        gameDao.saveActiveGame(ActiveGameEntity(1, root.toString()))
    }

    suspend fun loadActiveGame(): ActiveGameState? = withContext(Dispatchers.IO) {
        try {
            val entity = gameDao.getActiveGame() ?: return@withContext null
            val root = JSONObject(entity.gameStateJson)
            val rule = ScoringRule.valueOf(root.optString("rule", ScoringRule.LOWEST_WINS.name))
            val finished = root.optBoolean("finished", false)

            val playersArr = root.getJSONArray("players")
            val players = mutableListOf<Player>()
            for (i in 0 until playersArr.length()) {
                val pObj = playersArr.getJSONObject(i)
                players.add(
                    Player(
                        id = pObj.getInt("id"),
                        name = pObj.getString("name"),
                        colorHex = pObj.getLong("color"),
                        totalScore = pObj.getInt("score"),
                        phoneticName = pObj.optString("phonetic", "")
                    )
                )
            }

            val roundsArr = root.getJSONArray("rounds")
            val rounds = mutableListOf<RoundEntry>()
            for (i in 0 until roundsArr.length()) {
                val rObj = roundsArr.getJSONObject(i)
                val rNum = rObj.getInt("roundNumber")
                val scoresObj = rObj.getJSONObject("scores")
                val scores = mutableMapOf<Int, Int>()
                val keys = scoresObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    scores[k.toInt()] = scoresObj.getInt(k)
                }
                rounds.add(RoundEntry(rNum, scores))
            }

            ActiveGameState(players, rounds, rule, finished)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearActiveGame() = withContext(Dispatchers.IO) {
        gameDao.clearActiveGame()
    }

    suspend fun saveCompletedGame(
        rule: ScoringRule,
        roundsCount: Int,
        winnerName: String,
        winnerScore: Int,
        results: List<PlayerResult>
    ) = withContext(Dispatchers.IO) {
        val resultsArr = JSONArray()
        for (res in results) {
            val obj = JSONObject()
            obj.put("id", res.id)
            obj.put("name", res.name)
            obj.put("color", res.colorHex)
            obj.put("totalScore", res.totalScore)
            obj.put("rank", res.rank)
            obj.put("isWinner", res.isWinner)
            resultsArr.put(obj)
        }

        val record = GameRecordEntity(
            timestamp = System.currentTimeMillis(),
            scoringRule = rule.name,
            roundsCount = roundsCount,
            winnerName = winnerName,
            winnerScore = winnerScore,
            resultsJson = resultsArr.toString()
        )
        database.withTransaction {
            gameDao.insertRecord(record)
            gameDao.clearActiveGame()
        }
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        gameDao.deleteRecord(id)
    }

    suspend fun clearAllRecords() = withContext(Dispatchers.IO) {
        gameDao.clearAllRecords()
    }

    fun parseResultsJson(jsonStr: String): List<PlayerResult> {
        val list = mutableListOf<PlayerResult>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PlayerResult(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        colorHex = obj.getLong("color"),
                        totalScore = obj.getInt("totalScore"),
                        rank = obj.getInt("rank"),
                        isWinner = obj.getBoolean("isWinner")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    // ==========================================
    // Custom Sounds & Phrases Management
    // ==========================================

    fun getDefaultSoundSlots(players: List<Player> = emptyList()): List<CustomSoundSlot> {
        val list = mutableListOf<CustomSoundSlot>()

        // 1. Turn Start Phrase (Template + per player) - Plain Arabic without diacritics
        list.add(
            CustomSoundSlot(
                id = "turn_general",
                title = "عبارة بداية الدور العامة",
                description = "العبارة الأساسية لبدء الدور (يمكنك استخدام {الاسم})",
                category = SoundSlotCategory.TURN_START,
                customText = "يلا يا {الاسم}، دورك"
            )
        )

        list.add(CustomSoundSlot(id = "warning_10s", title = "آخر عشر ثوانٍ", description = "", category = SoundSlotCategory.WARNING_10S, customText = "باقي عشر ثواني"))
        list.add(CustomSoundSlot(id = "victory", title = "الفوز", description = "", category = SoundSlotCategory.WIN_VICTORY, customText = "مبروك الفوز"))
        // 2. Countdown 5..1
        list.add(CustomSoundSlot(id = "countdown_5", title = "العد التنازلي: 5", description = "نطق الثانية الخامسة", category = SoundSlotCategory.COUNTDOWN_5, customText = "خَمْسَة"))
        list.add(CustomSoundSlot(id = "countdown_4", title = "العد التنازلي: 4", description = "نطق الثانية الرابعة", category = SoundSlotCategory.COUNTDOWN_4, customText = "أَرْبَعَة"))
        list.add(CustomSoundSlot(id = "countdown_3", title = "العد التنازلي: 3", description = "نطق الثانية الثالثة", category = SoundSlotCategory.COUNTDOWN_3, customText = "ثَلَاثَة"))
        list.add(CustomSoundSlot(id = "countdown_2", title = "العد التنازلي: 2", description = "نطق الثانية الثانية", category = SoundSlotCategory.COUNTDOWN_2, customText = "اِثْنَيْن"))
        list.add(CustomSoundSlot(id = "countdown_1", title = "العد التنازلي: 1", description = "نطق الثانية الأخيرة", category = SoundSlotCategory.COUNTDOWN_1, customText = "وَاحِد"))

        // 3. Time Up
        list.add(
            CustomSoundSlot(
                id = "time_up",
                title = "عبارة انتهاء الوقت",
                description = "الجملة الفاصلة التي تُنطق في لحظة الصفر عند انتهاء وقت الدور",
                category = SoundSlotCategory.TIME_UP,
                customText = "انتهى الوقت، اسحب ثلاث"
            )
        )

        return list
    }

    companion object {
        fun stripArabicDiacritics(text: String): String {
            return text.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        }

        fun normalizeTurnTemplate(raw: String): String {
            val stripped = stripArabicDiacritics(raw).trim()
            return if (raw.contains("يَلَّا") || stripped == "يلا يا {الاسم}، دورك!" || stripped == "يلا يا {الاسم}، دورك") {
                "يلا يا {الاسم}، دورك"
            } else {
                stripped
            }
        }
    }

    fun getCustomSoundSlots(players: List<Player> = emptyList()): List<CustomSoundSlot> {
        val defaults = getDefaultSoundSlots(players)
        val savedJson = prefs.getString("custom_sound_slots_json", null) ?: return defaults

        return try {
            val arr = JSONArray(savedJson)
            val savedMap = mutableMapOf<String, CustomSoundSlot>()
            var hadOldDiacritics = false

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                // Filter out individual player slots and removed slots
                if (id.startsWith("turn_player_") || id == "warning_10s" || id == "win_victory") {
                    continue
                }

                var text = obj.optString("text", "")
                var elPath = obj.optString("elPath").ifEmpty { null }

                // Automatically migrate old diacritics from turn start template
                if (id == "turn_general") {
                    val normalized = normalizeTurnTemplate(text)
                    if (normalized != text) {
                        text = normalized
                        elPath = null // Reset cached audio path so new plain version is regenerated
                        hadOldDiacritics = true
                    }
                }

                savedMap[id] = CustomSoundSlot(
                    id = id,
                    title = obj.getString("title"),
                    description = obj.optString("desc", ""),
                    category = SoundSlotCategory.valueOf(obj.getString("category")),
                    customText = text,
                    recordingPath = obj.optString("recording").ifEmpty { null },
                    uploadedAudioPath = obj.optString("uploadedPath").ifEmpty { null },
                    uploadedAudioName = obj.optString("uploadedName").ifEmpty { null },
                    elevenLabsAudioPath = elPath,
                    lastGeneratedChars = if (elPath == null) 0 else obj.optInt("elChars", 0),
                    lastGeneratedTimestamp = if (elPath == null) 0L else obj.optLong("elTime", 0L),
                    speechRate = obj.optDouble("rate", 1.12).toFloat(),
                    speechPitch = obj.optDouble("pitch", 1.0).toFloat(),
                    voiceGender = obj.optString("gender", "MALE"),
                    isEnabled = obj.optBoolean("enabled", true),
                    targetPlayerId = if (obj.has("targetPlayerId")) obj.getInt("targetPlayerId") else null
                )
            }

            // Merge defaults with saved overrides, and preserve any user-created custom slots
            val result = defaults.map { def ->
                savedMap[def.id] ?: def
            }.toMutableList()

            // Add user created extra custom slots (category == CUSTOM)
            savedMap.values.filter { it.category == SoundSlotCategory.CUSTOM }.forEach { customSlot ->
                if (result.none { it.id == customSlot.id }) {
                    result.add(customSlot)
                }
            }

            if (hadOldDiacritics) {
                saveCustomSoundSlots(result)
            }

            result
        } catch (_: Exception) {
            defaults
        }
    }

    fun saveCustomSoundSlots(slots: List<CustomSoundSlot>) {
        try {
            val arr = JSONArray()
            for (slot in slots) {
                val obj = JSONObject()
                obj.put("id", slot.id)
                obj.put("title", slot.title)
                obj.put("desc", slot.description)
                obj.put("category", slot.category.name)
                obj.put("text", slot.customText)
                obj.put("recording", slot.recordingPath ?: "")
                obj.put("uploadedPath", slot.uploadedAudioPath ?: "")
                obj.put("uploadedName", slot.uploadedAudioName ?: "")
                obj.put("elPath", slot.elevenLabsAudioPath ?: "")
                obj.put("elChars", slot.lastGeneratedChars)
                obj.put("elTime", slot.lastGeneratedTimestamp)
                obj.put("rate", slot.speechRate.toDouble())
                obj.put("pitch", slot.speechPitch.toDouble())
                obj.put("gender", slot.voiceGender)
                obj.put("enabled", slot.isEnabled)
                slot.targetPlayerId?.let { obj.put("targetPlayerId", it) }
                arr.put(obj)
            }
            prefs.edit().putString("custom_sound_slots_json", arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun saveCustomSoundSlot(slot: CustomSoundSlot, players: List<Player> = emptyList()) {
        val current = getCustomSoundSlots(players).toMutableList()
        val index = current.indexOfFirst { it.id == slot.id }
        if (index >= 0) {
            current[index] = slot
        } else {
            current.add(slot)
        }
        saveCustomSoundSlots(current)
    }

    fun deleteCustomSoundSlot(slotId: String, players: List<Player> = emptyList()) {
        val current = getCustomSoundSlots(players).filterNot { it.id == slotId }
        saveCustomSoundSlots(current)
    }

    fun resetSlotToDefault(slotId: String, players: List<Player> = emptyList()) {
        val defaults = getDefaultSoundSlots(players)
        val defaultSlot = defaults.find { it.id == slotId } ?: return
        saveCustomSoundSlot(defaultSlot, players)
    }

    fun resetAllSlotsToDefault(players: List<Player> = emptyList()) {
        prefs.edit().remove("custom_sound_slots_json").apply()
    }

    fun exportCustomSoundsJson(players: List<Player> = emptyList()): String {
        val currentJson = prefs.getString("custom_sound_slots_json", null)
        if (!currentJson.isNullOrBlank()) return currentJson
        val defaults = getDefaultSoundSlots(players)
        saveCustomSoundSlots(defaults)
        return prefs.getString("custom_sound_slots_json", "[]") ?: "[]"
    }

    fun importCustomSoundsJson(jsonStr: String): Boolean {
        if (jsonStr.isBlank()) return false
        return try {
            val arr = JSONArray(jsonStr)
            prefs.edit().putString("custom_sound_slots_json", jsonStr).apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}

