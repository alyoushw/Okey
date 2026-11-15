package com.example.ui.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioRecorderHelper
import com.example.audio.SoundPlayer
import com.example.audio.SpeechManager
import com.example.audio.elevenlabs.ElevenLabsService
import com.example.data.model.CustomSoundSlot
import com.example.data.model.Player
import com.example.data.repository.GameRepository
import com.example.data.repository.SettingsState
import com.example.util.HapticUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TimerStatus {
    IDLE, RUNNING, PAUSED, FINISHED
}

data class TimerUiState(
    val initialSeconds: Int = 30,
    val remainingMillis: Long = 30_000L,
    val status: TimerStatus = TimerStatus.IDLE,
    val currentTurnCount: Int = 1,
    val isCriticalWarning: Boolean = false,
    val players: List<Player> = emptyList(),
    val activePlayerIndex: Int = 0,
    val settings: SettingsState = SettingsState(),
    val customSoundSlots: List<CustomSoundSlot> = emptyList(),
    val turnPreparationMessage: String? = null,
    val turnNoticeMessage: String? = null
) {
    val remainingSeconds: Int
        get() = ((remainingMillis + 999) / 1000).toInt()

    val progress: Float
        get() = if (initialSeconds > 0) {
            (remainingMillis.toFloat() / (initialSeconds * 1000f)).coerceIn(0f, 1f)
        } else 0f

    val activePlayer: Player
        get() = players.getOrElse(activePlayerIndex) {
            Player(1, "لاعب 1", 0xFFD32F2F)
        }
}

class TimerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    private val soundPlayer = SoundPlayer(application)
    private val speechManager = SpeechManager(application)
    private val hapticUtil = HapticUtil(application)

    val recorderHelper: AudioRecorderHelper
        get() = speechManager.recorderHelper

    val elevenLabsService: ElevenLabsService
        get() = speechManager.elevenLabsService

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private var turnVersion = 0L
    private var timerJob: Job? = null
    private var lastEmittedSecond: Int = -1
    private var lastSpokenSecond: Int = -1
    private var lastTickTime: Long = 0L

    init {
        loadInitialData()
        observeGameSession()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            com.example.data.repository.SettingsRepository.settingsState.collect { updatedSettings ->
                _uiState.update { current ->
                    current.copy(
                        settings = updatedSettings,
                        initialSeconds = if (current.status == TimerStatus.IDLE) updatedSettings.defaultTimerSeconds else current.initialSeconds,
                        remainingMillis = if (current.status == TimerStatus.IDLE) updatedSettings.defaultTimerSeconds * 1000L else current.remainingMillis
                    )
                }
            }
        }
    }

    private fun observeGameSession() {
        viewModelScope.launch {
            com.example.data.repository.GameSessionManager.sessionVersion.collect { reset() }
        }
        viewModelScope.launch {
            com.example.data.repository.GameSessionManager.players.collect { currentPlayers ->
                if (currentPlayers.isNotEmpty()) {
                    _uiState.update { current ->
                        val safeIndex = current.activePlayerIndex.coerceIn(0, currentPlayers.size - 1)
                        current.copy(players = currentPlayers, activePlayerIndex = safeIndex)
                    }
                    loadCustomSoundSlots()
                }
            }
        }
        viewModelScope.launch {
            com.example.data.repository.GameSessionManager.activePlayerIndex.collect { activeIdx ->
                _uiState.update { it.copy(activePlayerIndex = activeIdx) }
            }
        }
    }

    private fun loadInitialData() {
        val settings = repository.getSettings()
        _uiState.update {
            it.copy(
                initialSeconds = settings.defaultTimerSeconds,
                remainingMillis = settings.defaultTimerSeconds * 1000L,
                settings = settings
            )
        }
        refreshPlayers()
        loadCustomSoundSlots()
    }

    fun refreshPlayers() {
        viewModelScope.launch {
            val players = repository.loadActiveOrCachedPlayers()
            _uiState.update { current ->
                val safeIndex = if (players.isNotEmpty()) {
                    current.activePlayerIndex.coerceIn(0, players.size - 1)
                } else 0
                current.copy(players = players, activePlayerIndex = safeIndex)
            }
            loadCustomSoundSlots()
        }
    }

    fun loadCustomSoundSlots() {
        val slots = repository.getCustomSoundSlots(_uiState.value.players)
        _uiState.update { it.copy(customSoundSlots = slots) }
    }

    fun refreshSettings() {
        val settings = repository.getSettings()
        _uiState.update { it.copy(settings = settings) }
        refreshPlayers()
    }

    fun setDuration(seconds: Int) {
        turnVersion++
        val clamped = seconds.coerceIn(5, 300)
        timerJob?.cancel()
        turnPrepJob?.cancel()
        speechManager.stop()
        soundPlayer.stopAll()
        _uiState.update {
            it.copy(
                initialSeconds = clamped,
                remainingMillis = clamped * 1000L,
                status = TimerStatus.IDLE,
                isCriticalWarning = false
            )
        }
        val currentSettings = repository.getSettings()
        repository.saveSettings(currentSettings.copy(defaultTimerSeconds = clamped))
    }

    fun selectPlayer(index: Int) {
        turnVersion++
        val state = _uiState.value
        if (state.players.isEmpty()) return
        val clampedIndex = index.coerceIn(0, state.players.size - 1)
        com.example.data.repository.GameSessionManager.selectPlayer(clampedIndex)
        val player = state.players.getOrElse(clampedIndex) { state.players[0] }

        timerJob?.cancel()
        turnPrepJob?.cancel()
        speechManager.stop()
        soundPlayer.stopAll()

        _uiState.update {
            it.copy(
                activePlayerIndex = clampedIndex,
                remainingMillis = it.initialSeconds * 1000L,
                status = TimerStatus.RUNNING,
                isCriticalWarning = false,
                turnNoticeMessage = null
            )
        }

        hapticUtil.vibrateTick(_uiState.value.settings.hapticEnabled)

        prepareAndAnnounceTurn(player) {
            if (_uiState.value.status == TimerStatus.RUNNING) {
                startLoop()
            }
        }
    }

    private var turnPrepJob: kotlinx.coroutines.Job? = null

    /** Playback never waits for a network request. Names are prepared while setting up the game. */
    fun prepareAndAnnounceTurn(player: Player, onReadyToStartTimer: () -> Unit) {
        turnPrepJob?.cancel()
        val settings = repository.getSettings()
        _uiState.update { it.copy(turnPreparationMessage = null, turnNoticeMessage = null) }
        if (!settings.soundEnabled || !settings.voiceAnnouncePlayer || settings.muteAll || settings.masterVolume <= 0f) {
            onReadyToStartTimer()
            return
        }
        val phrase = if (settings.turnAnnouncementMode == "CUSTOM_PHRASE") {
            settings.turnCustomPhrase.trim().ifBlank { "اللي بعده" }
        } else {
            val template = repository.getCustomSoundSlots().find { it.id == "turn_general" }
                ?.customText?.ifBlank { "يلا يا {الاسم}، دورك" } ?: "يلا يا {الاسم}، دورك"
            template.replace("{الاسم}", player.name.trim())
        }
        val version = turnVersion
        val cached = elevenLabsService.getCachedFile(phrase, elevenLabsService.getConfigManager().getSettings())
        if (cached != null) {
            recorderHelper.playAudio(cached.absolutePath, settings.masterVolume) {
                if (version == turnVersion && _uiState.value.status == TimerStatus.RUNNING) onReadyToStartTimer()
            }
        } else {
            speechManager.playBundled("turn_start", settings.masterVolume) {
                if (version == turnVersion && _uiState.value.status == TimerStatus.RUNNING) onReadyToStartTimer()
            }
        }
    }

    fun clearTurnNoticeMessage() {
        _uiState.update { it.copy(turnNoticeMessage = null) }
    }

    fun startOrResume() {
        turnVersion++
        val state = _uiState.value
        when (state.status) {
            TimerStatus.IDLE, TimerStatus.FINISHED -> {
                _uiState.update {
                    it.copy(
                        remainingMillis = it.initialSeconds * 1000L,
                        status = TimerStatus.RUNNING,
                        isCriticalWarning = false,
                        turnNoticeMessage = null
                    )
                }
                val current = _uiState.value.activePlayer
                prepareAndAnnounceTurn(current) {
                    if (_uiState.value.status == TimerStatus.RUNNING) {
                        startLoop()
                    }
                }
            }
            TimerStatus.PAUSED -> {
                _uiState.update { it.copy(status = TimerStatus.RUNNING) }
                startLoop()
            }
            TimerStatus.RUNNING -> {
                pause()
            }
        }
    }

    fun pause() {
        turnVersion++
        timerJob?.cancel()
        turnPrepJob?.cancel()
        speechManager.stop()
        soundPlayer.stopAll()
        _uiState.update { it.copy(status = TimerStatus.PAUSED) }
    }

    fun reset() {
        turnVersion++
        timerJob?.cancel()
        turnPrepJob?.cancel()
        speechManager.stop()
        soundPlayer.stopAll()
        _uiState.update {
            it.copy(
                remainingMillis = it.initialSeconds * 1000L,
                status = TimerStatus.IDLE,
                isCriticalWarning = false,
                turnPreparationMessage = null,
                turnNoticeMessage = null
            )
        }
    }

    fun nextPlayer() {
        turnVersion++
        timerJob?.cancel()
        turnPrepJob?.cancel()
        speechManager.stop()
        soundPlayer.stopAll()

        val nextPlayer = com.example.data.repository.GameSessionManager.nextPlayer()
            ?: _uiState.value.players.firstOrNull()
            ?: return
        val nextIndex = com.example.data.repository.GameSessionManager.activePlayerIndex.value

        _uiState.update {
            it.copy(
                activePlayerIndex = nextIndex,
                remainingMillis = it.initialSeconds * 1000L,
                status = TimerStatus.RUNNING,
                currentTurnCount = it.currentTurnCount + 1,
                isCriticalWarning = false,
                turnNoticeMessage = null
            )
        }

        hapticUtil.vibrateTick(_uiState.value.settings.hapticEnabled)

        prepareAndAnnounceTurn(nextPlayer) {
            if (_uiState.value.status == TimerStatus.RUNNING) {
                startLoop()
            }
        }
    }

    fun onScreenTapped() {
        if (_uiState.value.settings.tapToAdvance) {
            nextPlayer()
        }
    }

    private fun startLoop() {
        timerJob?.cancel()
        lastEmittedSecond = _uiState.value.remainingSeconds
        lastSpokenSecond = -1
        lastTickTime = android.os.SystemClock.elapsedRealtime()
        val startTime = android.os.SystemClock.elapsedRealtime()
        val totalRemaining = _uiState.value.remainingMillis
        val version = turnVersion

        timerJob = viewModelScope.launch {
            while (true) {
                delay(25)
                val elapsed = android.os.SystemClock.elapsedRealtime() - startTime
                val newRemaining = (totalRemaining - elapsed).coerceAtLeast(0L)
                val currentSec = ((newRemaining + 999) / 1000).toInt()
                val settings = _uiState.value.settings
                val slots = _uiState.value.customSoundSlots

                // "Final five seconds" toggle owns every special effect of seconds 5..1:
                // spoken numbers, accelerated/louder urgent ticks and the strong vibration.
                // When it is off those seconds behave like any ordinary second.
                val finalFiveEnabled = settings.soundEnabled && !settings.muteAll && settings.voiceCountdownEnabled
                val isCritical = currentSec in 1..5 && finalFiveEnabled
                val isElevated = currentSec in 6..10
                val escalating = settings.tickSoundMode == "ESCALATING"

                // 10 seconds warning announcement
                if (currentSec == 10 && lastSpokenSecond != 10 && settings.soundEnabled && settings.warningSoundEnabled) {
                    lastSpokenSecond = 10
                    speechManager.play10sWarning(
                        slots = slots,
                        masterVolume = settings.masterVolume,
                        muteAll = settings.muteAll
                    )
                }

                // Voice countdown for numbers 5 down to 1
                if (isCritical && currentSec != lastSpokenSecond) {
                    lastSpokenSecond = currentSec
                    speechManager.playCountdownNumber(
                        number = currentSec,
                        slots = slots,
                        masterVolume = settings.masterVolume,
                        muteAll = settings.muteAll
                    )
                }

                // Tick cadence:
                // > 5s: 1 tick/sec (1000ms)
                // 4..5s: 2 ticks/sec (500ms)
                // 1..3s: 3 ticks/sec (333ms)
                val now = android.os.SystemClock.elapsedRealtime()
                val tickIntervalMs = if (escalating) {
                    when {
                        isCritical && currentSec in 1..3 -> 333L // 3 ticks/sec
                        isCritical && currentSec in 4..5 -> 500L // 2 ticks/sec
                        else -> 1000L              // 1 tick/sec
                    }
                } else {
                    1000L
                }

                if (newRemaining > 0 && (now - lastTickTime >= tickIntervalMs)) {
                    lastTickTime = now
                    lastEmittedSecond = currentSec

                    // Escalating volume & pitch calculation
                    val volumeFactor = if (escalating) {
                        when {
                            isCritical -> 0.70f + ((5 - currentSec) / 4.0f) * 0.30f
                            isElevated -> 0.25f + ((10 - currentSec) / 4.0f) * 0.30f
                            else -> 0.25f
                        }
                    } else {
                        0.60f
                    }

                    val pitchFactor = if (escalating) {
                        when {
                            isCritical -> 1.15f + ((5 - currentSec) * 0.03f)
                            isElevated -> 1.0f + ((10 - currentSec) * 0.02f)
                            else -> 1.0f
                        }
                    } else {
                        1.0f
                    }

                    if (settings.soundEnabled && settings.tickSoundEnabled) {
                        soundPlayer.playTick(
                            enabled = true,
                            toneType = settings.tickTone,
                            volumeFactor = volumeFactor,
                            isUrgent = isCritical || isElevated,
                            pitchFactor = pitchFactor,
                            masterVolume = settings.masterVolume,
                            muteAll = settings.muteAll
                        )
                    }

                    if (settings.hapticEnabled) {
                        if (isCritical) {
                            hapticUtil.vibrateWarning(true)
                        } else {
                            hapticUtil.vibrateTick(true)
                        }
                    }
                }

                if (newRemaining <= 0L) {
                    _uiState.update {
                        it.copy(
                            remainingMillis = 0L,
                            status = TimerStatus.FINISHED,
                            isCriticalWarning = false
                        )
                    }
                    soundPlayer.playTimeUp(
                        enabled = settings.soundEnabled,
                        masterVolume = settings.masterVolume,
                        muteAll = settings.muteAll
                    )
                    hapticUtil.vibrateTimeUp(settings.hapticEnabled)

                    // Speak the penalty phrase: custom recording, or custom diacritic text, or default "اسحب ثلاث يا غشيم"!
                    speechManager.playTimeUp(
                        slots = slots,
                        masterVolume = settings.masterVolume,
                        muteAll = settings.muteAll || !settings.soundEnabled || !settings.voiceTimeUpPenalty,
                        onFinished = {
                            viewModelScope.launch {
                                delay(600)
                                if (version == turnVersion && _uiState.value.status == TimerStatus.FINISHED) {
                                    nextPlayer()
                                }
                            }
                        }
                    )
                    break
                } else {
                    _uiState.update {
                        it.copy(
                            remainingMillis = newRemaining,
                            isCriticalWarning = isCritical
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        turnPrepJob?.cancel()
        soundPlayer.release()
        speechManager.shutdown()
        soundPlayer.release()
    }
}
