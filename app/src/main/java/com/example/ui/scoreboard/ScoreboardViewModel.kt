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
    val isLoading: Boolean = true,
    val hasActiveGame: Boolean = false,
    val players: List<Player> = emptyList(),
    val rounds: List<RoundEntry> = emptyList(),
    val scoringRule: ScoringRule = ScoringRule.LOWEST_WINS,
    val isGameFinished: Boolean = false,
    val finalResults: List<PlayerResult> = emptyList(),
    val recentPlayerNames: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null
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

class ScoreboardViewModel(application: Application) : AndroidViewModel(application) {
    val repository = GameRepository(application)
    val soundPlayer = SoundPlayer(application)
    val speechManager = com.example.audio.SpeechManager(application)
    val hapticUtil = HapticUtil(application)

    private val _uiState = MutableStateFlow(ScoreboardUiState())
    val uiState: StateFlow<ScoreboardUiState> = _uiState.asStateFlow()

    private val pendingWrites = kotlinx.coroutines.channels.Channel<suspend () -> Unit>(
        kotlinx.coroutines.channels.Channel.UNLIMITED
    )
    private val nameJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }

    private fun enqueueWrite(block: suspend () -> Unit) {
        pendingWrites.trySend(block)
    }

    fun preparePlayerNames(names: List<String>) {
        val preferences = repository.getSettings()
        if (!preferences.soundEnabled || !preferences.voiceAnnouncePlayer || preferences.muteAll) return
        names.map(String::trim).filter { it.isNotEmpty() && it.length <= 30 }.distinct().forEach { name ->
            if (nameJobs[name]?.isActive == true) return@forEach
            val service = speechManager.elevenLabsService
            val settings = service.getConfigManager().getSettings()
            // The turn call and the victory call are both ready before the game needs them.
            val phrases = listOf(getEffectiveTurnPhrase(name), victoryPhrase(name))
                .filterNot { service.isCached(it, settings) }
            if (phrases.isEmpty() || !service.hasValidApiKey()) return@forEach
            nameJobs[name] = viewModelScope.launch {
                phrases.forEach { service.generateSpeech(it, settings) }
            }
        }
    }

    init {
        viewModelScope.launch {
            for (write in pendingWrites) {
                try {
                    write()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "تعذّر حفظ اللعبة. حاول مرة أخرى.") }
                }
            }
        }
        loadGameState()
    }

    private fun victoryPhrase(playerName: String): String = "مبروك الفوز يا ${playerName.trim()}"

    private fun getEffectiveTurnPhrase(playerName: String): String {
        val clean = playerName.trim()
        val slots = repository.getCustomSoundSlots(emptyList())
        val turnSlot = slots.find { it.id == "turn_general" }
        val rawTemplate = turnSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" } ?: "يلا يا {الاسم}، دورك"
        return rawTemplate.replace("{الاسم}", clean).trim()
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
                        isLoading = false,
                        hasActiveGame = true,
                        players = recalculatedPlayers,
                        rounds = savedState.rounds,
                        scoringRule = savedState.scoringRule,
                        isGameFinished = savedState.isFinished,
                        recentPlayerNames = recentNames
                    )
                }
                if (savedState.isFinished) {
                    _uiState.update { it.copy(finalResults = computeFinalResults(recalculatedPlayers, savedState.scoringRule)) }
                } else {
                    checkAndRegenerateIfTemplateUpdated(recalculatedPlayers)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
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
        if (_uiState.value.isSaving || _uiState.value.isLoading) return
        val newPlayers = playerNames.take(6).mapIndexed { idx, name ->
            Player(
                id = idx + 1,
                name = name.trim().take(30).ifBlank { "لاعب ${idx + 1}" },
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

        enqueueWrite {
            repository.saveActiveGame(newState)
        }

        pregenerateVoicesForPlayers(newPlayers)
    }

    private fun pregenerateVoicesForPlayers(players: List<Player>) {
        preparePlayerNames(players.map { it.name })
    }

    private fun checkAndRegenerateIfTemplateUpdated(players: List<Player>) {
        preparePlayerNames(players.map { it.name })
    }

    fun addRound(scores: Map<Int, Int>) {
        if (_uiState.value.isSaving || _uiState.value.isGameFinished) return
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
        if (_uiState.value.isSaving || _uiState.value.isGameFinished) return
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
        if (_uiState.value.isSaving || _uiState.value.isGameFinished) return
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
        if (_uiState.value.isSaving || _uiState.value.isGameFinished) return
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
        if (state.players.isEmpty() || state.isGameFinished || state.isSaving) return

        com.example.data.repository.GameSessionManager.stopCurrentTurn()
        val results = computeFinalResults(state.players, state.scoringRule)

        _uiState.update {
            it.copy(
                isSaving = true,
                errorMessage = null
            )
        }

        val winner = results.firstOrNull()
        val winnerName = winner?.name ?: "الفائز"
        val winnerScore = winner?.totalScore ?: 0

        val settings = repository.getSettings()
        hapticUtil.vibrateVictory(settings.hapticEnabled)
        if (settings.soundEnabled && !settings.muteAll) {
            // "مبروك الفوز يا {الاسم}" first, then the celebration music.
            val playMusic = {
                when (settings.victoryMusic) {
                    com.example.audio.VictoryMusic.NONE -> Unit
                    com.example.audio.VictoryMusic.SHORT_TUNE ->
                        soundPlayer.playCelebrationFanfare(true, settings.masterVolume, false)
                    else -> if (!com.example.audio.VictoryMusic.play(getApplication(), settings.victoryMusic, settings.masterVolume)) {
                        soundPlayer.playCelebrationFanfare(true, settings.masterVolume, false)
                    }
                }
            }
            val soleWinner = results.filter { it.isWinner }.singleOrNull()
            val service = speechManager.elevenLabsService
            val named = soleWinner?.let { service.getCachedFile(victoryPhrase(it.name), service.getConfigManager().getSettings()) }
            if (named != null) {
                speechManager.recorderHelper.playAudio(named.absolutePath, settings.masterVolume) { playMusic() }
            } else {
                speechManager.playVictory(repository.getCustomSoundSlots(state.players), settings.masterVolume, false) { playMusic() }
            }
        }

        enqueueWrite {
            repository.saveCompletedGame(
                rule = state.scoringRule,
                roundsCount = state.rounds.size,
                winnerName = winnerName,
                winnerScore = winnerScore,
                results = results
            )
            _uiState.update { it.copy(isSaving = false, isGameFinished = true, finalResults = results) }
        }
    }

    fun startNewGameSamePlayers() {
        if (_uiState.value.isSaving) return
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
        if (_uiState.value.isSaving) return
        _uiState.update {
            it.copy(
                hasActiveGame = false,
                isGameFinished = false,
                finalResults = emptyList()
            )
        }
        enqueueWrite {
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
        enqueueWrite {
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
        nameJobs.values.forEach { it.cancel() }
        pendingWrites.close()
        speechManager.shutdown()
        soundPlayer.release()
    }
}
