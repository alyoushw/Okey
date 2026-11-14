package com.example.ui.scoreboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundPlayer
import com.example.data.model.ActiveGameState
import com.example.data.model.Player
import com.example.data.model.PlayerResult
import com.example.data.model.RoundEntry
import com.example.data.model.ScoringRule
import com.example.data.repository.GameRepository
import com.example.util.HapticUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScoreboardUiState(
    val hasActiveGame: Boolean = false,
    val players: List<Player> = emptyList(),
    val rounds: List<RoundEntry> = emptyList(),
    val scoringRule: ScoringRule = ScoringRule.LOWEST_WINS,
    val isGameFinished: Boolean = false,
    val finalResults: List<PlayerResult> = emptyList(),
    val recentPlayerNames: List<String> = emptyList(),
    val isSaving: Boolean = false
) {
    val currentRoundNumber: Int
        get() = rounds.size + 1

    val currentLeaderId: Int?
        get() {
            if (players.isEmpty() || rounds.isEmpty()) return null
            return if (scoringRule == ScoringRule.LOWEST_WINS) {
                players.minByOrNull { it.totalScore }?.id
            } else {
                players.maxByOrNull { it.totalScore }?.id
            }
        }
}

enum class VoiceStatusType {
    NOT_GENERATED,
    GENERATING,
    READY,
    FAILED
}

data class PlayerVoiceStatus(
    val status: VoiceStatusType = VoiceStatusType.NOT_GENERATED,
    val errorMessage: String? = null,
    val statusCode: Int? = null,
    val lastAttemptTime: Long? = null
)

class ScoreboardViewModel(application: Application) : AndroidViewModel(application) {
    val repository = GameRepository(application)
    val soundPlayer = SoundPlayer(application)
    val speechManager = com.example.audio.SpeechManager(application)
    val hapticUtil = HapticUtil(application)

    private val _uiState = MutableStateFlow(ScoreboardUiState())
    val uiState: StateFlow<ScoreboardUiState> = _uiState.asStateFlow()

    private val _playerVoiceStatuses = MutableStateFlow<Map<String, PlayerVoiceStatus>>(emptyMap())
    val playerVoiceStatuses: StateFlow<Map<String, PlayerVoiceStatus>> = _playerVoiceStatuses.asStateFlow()

    private val _isGeneratingAllVoices = MutableStateFlow(false)
    val isGeneratingAllVoices: StateFlow<Boolean> = _isGeneratingAllVoices.asStateFlow()

    private val _voiceErrorMessage = MutableStateFlow<String?>(null)
    val voiceErrorMessage: StateFlow<String?> = _voiceErrorMessage.asStateFlow()

    init {
        loadGameState()
    }

    private fun getEffectiveTurnPhrase(playerName: String): String {
        val clean = playerName.trim()
        val slots = repository.getCustomSoundSlots(emptyList())
        val turnSlot = slots.find { it.id == "turn_general" }
        val rawTemplate = turnSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" } ?: "يلا يا {الاسم}، دورك"
        return rawTemplate.replace("{الاسم}", clean).trim()
    }

    fun checkAndRefreshStatusForNames(names: List<String>) {
        val settings = speechManager.elevenLabsService.getConfigManager().getSettings()
        _playerVoiceStatuses.update { current ->
            val updated = current.toMutableMap()
            names.forEach { name ->
                val clean = name.trim()
                if (clean.isNotEmpty()) {
                    val phrase = getEffectiveTurnPhrase(clean)
                    val isCached = speechManager.elevenLabsService.isCached(phrase, settings)
                    val existing = updated[clean]
                    if (isCached) {
                        updated[clean] = PlayerVoiceStatus(status = VoiceStatusType.READY)
                    } else if (existing == null || existing.status == VoiceStatusType.GENERATING) {
                        updated[clean] = PlayerVoiceStatus(status = VoiceStatusType.NOT_GENERATED)
                    }
                }
            }
            updated
        }
    }

    fun generateVoicesForPlayerList(playerNames: List<String>) {
        viewModelScope.launch {
            _isGeneratingAllVoices.value = true
            _voiceErrorMessage.value = null

            // 1 & 2) Read guaranteed fresh settings from single source of truth
            val currentSettings = com.example.data.repository.SettingsRepository.getSettings(getApplication<Application>())
            val modeRead = currentSettings.turnAnnouncementMode
            val customPhrase = currentSettings.turnCustomPhrase.trim().ifBlank { "اللي بعده" }
            val cleanNames = playerNames.map { it.trim() }.filter { it.isNotEmpty() }
            val settings = speechManager.elevenLabsService.getConfigManager().getSettings()

            // 4) Record diagnostic log with mode read, player names, and custom phrase
            com.example.audio.elevenlabs.ElevenLabsService.recordLog(
                target = "زر توليد أصوات اللاعبين (شاشة النقاط)",
                success = true,
                statusCode = null,
                message = "وضع التبديل المقروء: $modeRead | قائمة اللاعبين (${cleanNames.size}): [${cleanNames.joinToString(", ")}] | العبارة المخصصة: \"$customPhrase\""
            )

            // 3) Always generate BOTH custom phrase and player names regardless of mode!
            // Step A: Custom phrase
            val customResult = speechManager.elevenLabsService.generateSpeech(customPhrase, settings, forceRegenerate = false)
            customResult.fold(
                onSuccess = {
                    com.example.audio.elevenlabs.ElevenLabsService.recordLog(
                        target = "عبارة الكلمة المخصصة: \"$customPhrase\"",
                        success = true,
                        statusCode = 200,
                        message = "تم تجهيز عبارة تبديل الدور بنجاح ✓"
                    )
                },
                onFailure = { err ->
                    val errMsg = err.message ?: "فشل توليد العبارة المخصصة"
                    com.example.audio.elevenlabs.ElevenLabsService.recordLog(
                        target = "عبارة الكلمة المخصصة: \"$customPhrase\"",
                        success = false,
                        statusCode = null,
                        message = errMsg
                    )
                }
            )

            // Step B: Player names (using turn_general template without diacritics)
            val slots = repository.getCustomSoundSlots(emptyList())
            val turnSlot = slots.find { it.id == "turn_general" }
            val rawTemplate = turnSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" } ?: "يلا يا {الاسم}، دورك"

            var failedCount = 0
            var firstRawError: String? = null

            for (name in cleanNames) {
                _playerVoiceStatuses.update { current ->
                    current + (name to PlayerVoiceStatus(status = VoiceStatusType.GENERATING))
                }

                // Send exact player name as written by user without diacritics or alterations
                val playerPhrase = rawTemplate.replace("{الاسم}", name.trim()).trim()
                val result = speechManager.elevenLabsService.generateSpeech(playerPhrase, settings, forceRegenerate = false)

                result.fold(
                    onSuccess = {
                        com.example.audio.elevenlabs.ElevenLabsService.recordLog(
                            target = "صوت اللاعب: $name (\"$playerPhrase\")",
                            success = true,
                            statusCode = 200,
                            message = "تم تجهيز صوت اللاعب بنجاح ✓"
                        )
                        _playerVoiceStatuses.update { current ->
                            current + (name to PlayerVoiceStatus(
                                status = VoiceStatusType.READY,
                                lastAttemptTime = System.currentTimeMillis()
                            ))
                        }
                    },
                    onFailure = { err ->
                        failedCount++
                        val unsummarized = err.message ?: "خطأ غير معروف في الاتصال بـ ElevenLabs"
                        if (firstRawError == null) {
                            firstRawError = unsummarized
                        }
                        com.example.audio.elevenlabs.ElevenLabsService.recordLog(
                            target = "صوت اللاعب: $name (\"$playerPhrase\")",
                            success = false,
                            statusCode = null,
                            message = unsummarized
                        )
                        _playerVoiceStatuses.update { current ->
                            current + (name to PlayerVoiceStatus(
                                status = VoiceStatusType.FAILED,
                                errorMessage = unsummarized,
                                lastAttemptTime = System.currentTimeMillis()
                            ))
                        }
                    }
                )
            }

            _isGeneratingAllVoices.value = false
            if (failedCount > 0) {
                _voiceErrorMessage.value = firstRawError
            }
        }
    }

    fun testAndPlayPlayerVoice(playerName: String, onFinished: (() -> Unit)? = null) {
        val clean = playerName.trim()
        if (clean.isEmpty()) return

        viewModelScope.launch {
            val settings = speechManager.elevenLabsService.getConfigManager().getSettings()
            val phrase = getEffectiveTurnPhrase(clean)
            val appSettings = repository.getSettings()

            _playerVoiceStatuses.update { current ->
                current + (clean to PlayerVoiceStatus(status = VoiceStatusType.GENERATING))
            }

            val result = speechManager.elevenLabsService.generateSpeech(phrase, settings, forceRegenerate = false)
            result.fold(
                onSuccess = { file ->
                    _playerVoiceStatuses.update { current ->
                        current + (clean to PlayerVoiceStatus(
                            status = VoiceStatusType.READY,
                            lastAttemptTime = System.currentTimeMillis()
                        ))
                    }
                    speechManager.recorderHelper.playAudio(
                        filePath = file.absolutePath,
                        volume = appSettings.masterVolume.coerceIn(0.0f, 1f)
                    ) {
                        onFinished?.invoke()
                    }
                },
                onFailure = { err ->
                    val unsummarized = err.message ?: "فشل في توليد صوت ElevenLabs"
                    _playerVoiceStatuses.update { current ->
                        current + (clean to PlayerVoiceStatus(
                            status = VoiceStatusType.FAILED,
                            errorMessage = unsummarized,
                            lastAttemptTime = System.currentTimeMillis()
                        ))
                    }
                    _voiceErrorMessage.value = unsummarized
                    onFinished?.invoke()
                }
            )
        }
    }

    fun clearVoiceErrorMessage() {
        _voiceErrorMessage.value = null
    }

    fun loadGameState() {
        viewModelScope.launch {
            val savedState = repository.loadActiveGame()
            val recentNames = repository.getLastPlayerNames()

            if (savedState != null && savedState.players.isNotEmpty()) {
                val recalculatedPlayers = calculatePlayerTotals(savedState.players, savedState.rounds)
                com.example.data.repository.GameSessionManager.setPlayers(recalculatedPlayers, resetTurnToFirst = false)
                _uiState.update {
                    it.copy(
                        hasActiveGame = true,
                        players = recalculatedPlayers,
                        rounds = savedState.rounds,
                        scoringRule = savedState.scoringRule,
                        isGameFinished = savedState.isFinished,
                        recentPlayerNames = recentNames
                    )
                }
                if (savedState.isFinished) {
                    computeFinalResults(recalculatedPlayers, savedState.scoringRule)
                } else {
                    checkAndRegenerateIfTemplateUpdated(recalculatedPlayers)
                }
            } else {
                _uiState.update {
                    it.copy(
                        hasActiveGame = false,
                        recentPlayerNames = recentNames
                    )
                }
            }
        }
    }

    fun startNewGame(
        playerNames: List<String>,
        playerColors: List<Long>,
        scoringRule: ScoringRule
    ) {
        val newPlayers = playerNames.mapIndexed { idx, name ->
            Player(
                id = idx + 1,
                name = name.ifBlank { "لاعب ${idx + 1}" },
                colorHex = playerColors.getOrElse(idx) { 0xFF1976D2 },
                totalScore = 0
            )
        }

        repository.saveLastPlayerNames(playerNames)
        com.example.data.repository.GameSessionManager.setPlayers(newPlayers, resetTurnToFirst = true)

        val newState = ActiveGameState(
            players = newPlayers,
            rounds = emptyList(),
            scoringRule = scoringRule,
            isFinished = false
        )

        _uiState.update {
            it.copy(
                hasActiveGame = true,
                players = newPlayers,
                rounds = emptyList(),
                scoringRule = scoringRule,
                isGameFinished = false,
                finalResults = emptyList()
            )
        }

        viewModelScope.launch {
            repository.saveActiveGame(newState)
        }

        pregenerateVoicesForPlayers(newPlayers)
    }

    private fun pregenerateVoicesForPlayers(players: List<Player>) {
        viewModelScope.launch {
            val total = players.size
            if (total == 0) return@launch

            val currentSettings = repository.getSettings()
            val settings = speechManager.elevenLabsService.getConfigManager().getSettings()

            // If turn announcements disabled, don't generate
            if (!currentSettings.voiceAnnouncePlayer) {
                com.example.data.repository.GameSessionManager.setPreGenerating(
                    active = false,
                    progress = 1f,
                    message = "نطق الأسماء غير مفعّل في الإعدادات",
                    isError = false
                )
                return@launch
            }

            // Always generate both custom phrase and all player names
            val customPhrase = currentSettings.turnCustomPhrase.trim().ifBlank { "اللي بعده" }
            speechManager.elevenLabsService.generateSpeech(customPhrase, settings, forceRegenerate = false)

            // Mode: PLAYER_NAME
            com.example.data.repository.GameSessionManager.setPreGenerating(
                active = true,
                progress = 0f,
                message = "جارٍ تجهيز أصوات اللاعبين (0/$total)...",
                isError = false
            )

            val slots = repository.getCustomSoundSlots(players)
            val turnSlot = slots.find { it.id == "turn_general" }
            val rawTemplate = turnSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" } ?: "يلا يا {الاسم}، دورك"

            var completed = 0
            var firstErrorMsg: String? = null
            for (player in players) {
                // Exact player name as entered by user without diacritics or automatic alterations
                val exactName = player.name.trim()
                val phrase = rawTemplate.replace("{الاسم}", exactName).trim()

                val res = speechManager.elevenLabsService.generateSpeech(phrase, settings, forceRegenerate = false)
                if (res.isFailure && firstErrorMsg == null) {
                    firstErrorMsg = res.exceptionOrNull()?.message
                }
                completed++
                val prog = completed.toFloat() / total.toFloat()
                com.example.data.repository.GameSessionManager.setPreGenerating(
                    active = true,
                    progress = prog,
                    message = "جارٍ حفظ صوت: $exactName ($completed/$total)",
                    isError = false
                )
            }

            if (firstErrorMsg != null) {
                com.example.data.repository.GameSessionManager.setPreGenerating(
                    active = false,
                    progress = 1f,
                    message = firstErrorMsg,
                    isError = true,
                    errorMessage = firstErrorMsg
                )
            } else {
                com.example.data.repository.GameSessionManager.setPreGenerating(
                    active = false,
                    progress = 1f,
                    message = "تم تجهيز أصوات اللاعبين",
                    isError = false
                )
            }
        }
    }

    private fun checkAndRegenerateIfTemplateUpdated(players: List<Player>) {
        viewModelScope.launch {
            val settings = speechManager.elevenLabsService.getConfigManager().getSettings()
            val anyMissing = players.any { player ->
                val phrase = getEffectiveTurnPhrase(player.name)
                !speechManager.elevenLabsService.isCached(phrase, settings)
            }
            if (anyMissing) {
                pregenerateVoicesForPlayers(players)
            }
        }
    }

    fun addRound(scores: Map<Int, Int>) {
        val currentRounds = _uiState.value.rounds.toMutableList()
        val nextRoundNumber = currentRounds.size + 1
        currentRounds.add(RoundEntry(nextRoundNumber, scores))

        val updatedPlayers = calculatePlayerTotals(_uiState.value.players, currentRounds)

        _uiState.update {
            it.copy(
                rounds = currentRounds,
                players = updatedPlayers
            )
        }

        val settings = repository.getSettings()
        hapticUtil.vibrateTick(settings.hapticEnabled)

        persistState(updatedPlayers, currentRounds, _uiState.value.scoringRule, false)
    }

    fun editRound(roundNumber: Int, updatedScores: Map<Int, Int>) {
        val currentRounds = _uiState.value.rounds.map { r ->
            if (r.roundNumber == roundNumber) r.copy(scores = updatedScores) else r
        }
        val updatedPlayers = calculatePlayerTotals(_uiState.value.players, currentRounds)

        _uiState.update {
            it.copy(
                rounds = currentRounds,
                players = updatedPlayers
            )
        }
        persistState(updatedPlayers, currentRounds, _uiState.value.scoringRule, false)
    }

    fun deleteRound(roundNumber: Int) {
        val filtered = _uiState.value.rounds.filter { it.roundNumber != roundNumber }
        // Re-index remaining rounds
        val reindexed = filtered.mapIndexed { index, roundEntry ->
            roundEntry.copy(roundNumber = index + 1)
        }
        val updatedPlayers = calculatePlayerTotals(_uiState.value.players, reindexed)

        _uiState.update {
            it.copy(
                rounds = reindexed,
                players = updatedPlayers
            )
        }
        persistState(updatedPlayers, reindexed, _uiState.value.scoringRule, false)
    }

    fun undoLastRound() {
        if (_uiState.value.rounds.isNotEmpty()) {
            val updatedRounds = _uiState.value.rounds.dropLast(1)
            val updatedPlayers = calculatePlayerTotals(_uiState.value.players, updatedRounds)
            _uiState.update {
                it.copy(
                    rounds = updatedRounds,
                    players = updatedPlayers
                )
            }
            persistState(updatedPlayers, updatedRounds, _uiState.value.scoringRule, false)
        }
    }

    fun finishGame() {
        val state = _uiState.value
        if (state.players.isEmpty()) return

        val results = computeFinalResults(state.players, state.scoringRule)

        _uiState.update {
            it.copy(
                isGameFinished = true,
                finalResults = results
            )
        }

        val winner = results.firstOrNull()
        val winnerName = winner?.name ?: "الفائز"
        val winnerScore = winner?.totalScore ?: 0

        val settings = repository.getSettings()
        soundPlayer.playCelebrationFanfare(settings.soundEnabled, settings.masterVolume, settings.muteAll)
        val customSlots = repository.getCustomSoundSlots(state.players)
        speechManager.playVictory(customSlots, settings.masterVolume, settings.muteAll)
        hapticUtil.vibrateVictory(settings.hapticEnabled)

        viewModelScope.launch {
            repository.saveCompletedGame(
                rule = state.scoringRule,
                roundsCount = state.rounds.size,
                winnerName = winnerName,
                winnerScore = winnerScore,
                results = results
            )
        }
    }

    fun startNewGameSamePlayers() {
        val currentPlayers = _uiState.value.players.map { it.copy(totalScore = 0) }
        val rule = _uiState.value.scoringRule

        com.example.data.repository.GameSessionManager.setPlayers(currentPlayers, resetTurnToFirst = true)

        _uiState.update {
            it.copy(
                hasActiveGame = true,
                players = currentPlayers,
                rounds = emptyList(),
                isGameFinished = false,
                finalResults = emptyList()
            )
        }
        persistState(currentPlayers, emptyList(), rule, false)
        pregenerateVoicesForPlayers(currentPlayers)
    }

    fun resetToNewGameSetup() {
        _uiState.update {
            it.copy(
                hasActiveGame = false,
                isGameFinished = false,
                finalResults = emptyList()
            )
        }
        viewModelScope.launch {
            repository.clearActiveGame()
        }
    }

    private fun computeFinalResults(players: List<Player>, rule: ScoringRule): List<PlayerResult> {
        val sorted = if (rule == ScoringRule.LOWEST_WINS) {
            players.sortedBy { it.totalScore }
        } else {
            players.sortedByDescending { it.totalScore }
        }

        val bestScore = sorted.firstOrNull()?.totalScore ?: 0

        var currentRank = 1
        return sorted.mapIndexed { index, p ->
            if (index > 0 && p.totalScore != sorted[index - 1].totalScore) {
                currentRank = index + 1
            }
            PlayerResult(
                id = p.id,
                name = p.name,
                colorHex = p.colorHex,
                totalScore = p.totalScore,
                rank = currentRank,
                isWinner = p.totalScore == bestScore
            )
        }
    }

    private fun calculatePlayerTotals(players: List<Player>, rounds: List<RoundEntry>): List<Player> {
        return players.map { player ->
            val sum = rounds.sumOf { r -> r.scores[player.id] ?: 0 }
            player.copy(totalScore = sum)
        }
    }

    private fun persistState(players: List<Player>, rounds: List<RoundEntry>, rule: ScoringRule, finished: Boolean) {
        viewModelScope.launch {
            repository.saveActiveGame(
                ActiveGameState(
                    players = players,
                    rounds = rounds,
                    scoringRule = rule,
                    isFinished = finished
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.release()
    }
}
