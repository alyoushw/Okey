package com.example.ui.timer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioRecorderHelper
import com.example.audio.SoundPlayer
import com.example.audio.SpeechManager
import com.example.audio.elevenlabs.ElevenLabsConfigManager
import com.example.audio.elevenlabs.ElevenLabsService
import com.example.audio.elevenlabs.ElevenLabsSettings
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

    private val _elevenLabsSettings = MutableStateFlow(elevenLabsService.getConfigManager().getSettings())
    val elevenLabsSettings: StateFlow<ElevenLabsSettings> = _elevenLabsSettings.asStateFlow()

    private val _totalCharactersUsed = MutableStateFlow(elevenLabsService.getConfigManager().getTotalCharactersUsed())
    val totalCharactersUsed: StateFlow<Long> = _totalCharactersUsed.asStateFlow()

    private val _isGeneratingElevenLabs = MutableStateFlow<String?>(null)
    val isGeneratingElevenLabs: StateFlow<String?> = _isGeneratingElevenLabs.asStateFlow()

    private val _elevenLabsMessage = MutableStateFlow<String?>(null)
    val elevenLabsMessage: StateFlow<String?> = _elevenLabsMessage.asStateFlow()

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
        val clamped = seconds.coerceIn(5, 300)
        timerJob?.cancel()
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

    /**
     * Manages turn announcement with ElevenLabs.
     * 1. If already cached: plays immediately from disk with zero delay.
     * 2. If not cached: generates on the fly with brief indicator.
     * 3. If generation fails: shows specific error notice and starts timer without hanging!
     * 4. When PLAYER_NAME mode is active: player's name takes priority over uploaded/recorded general clips.
     */
    fun prepareAndAnnounceTurn(player: Player, onReadyToStartTimer: () -> Unit) {
        turnPrepJob?.cancel()
        val settings = com.example.data.repository.SettingsRepository.getSettings(getApplication())

        if (!settings.voiceAnnouncePlayer || settings.muteAll) {
            _uiState.update { it.copy(turnPreparationMessage = null) }
            onReadyToStartTimer()
            return
        }

        turnPrepJob = viewModelScope.launch {
            val elevenSettings = _elevenLabsSettings.value

            // 1. CUSTOM_PHRASE mode
            if (settings.turnAnnouncementMode.equals("CUSTOM_PHRASE", ignoreCase = true)) {
                val phrase = settings.turnCustomPhrase.trim().ifBlank { "اللي بعده" }
                val cached = elevenLabsService.getCachedFile(phrase, elevenSettings)

                if (cached != null && cached.exists() && cached.length() > 0) {
                    _uiState.update { it.copy(turnPreparationMessage = null) }
                    recorderHelper.playAudio(cached.absolutePath, settings.masterVolume) {
                        if (_uiState.value.status == TimerStatus.RUNNING) {
                            onReadyToStartTimer()
                        }
                    }
                } else {
                    _uiState.update { it.copy(turnPreparationMessage = "جارٍ تجهيز صوت: $phrase...") }
                    val result = elevenLabsService.generateSpeech(phrase, elevenSettings, forceRegenerate = false)
                    _uiState.update { it.copy(turnPreparationMessage = null) }

                    result.fold(
                        onSuccess = { file ->
                            recorderHelper.playAudio(file.absolutePath, settings.masterVolume) {
                                if (_uiState.value.status == TimerStatus.RUNNING) {
                                    onReadyToStartTimer()
                                }
                            }
                        },
                        onFailure = { err ->
                            _uiState.update { it.copy(turnNoticeMessage = err.message) }
                            onReadyToStartTimer()
                        }
                    )
                }
                return@launch
            }

            // 2. PLAYER_NAME mode:
            // Send exact name without diacritics or automatic alterations
            val exactName = player.name.trim()
            val slots = _uiState.value.customSoundSlots

            // Player-specific slot (targetPlayerId == player.id)
            val specificSlot = slots.find { it.targetPlayerId == player.id && it.isEnabled }
            if (specificSlot != null && (!specificSlot.uploadedAudioPath.isNullOrBlank() ||
                        !specificSlot.recordingPath.isNullOrBlank() ||
                        !specificSlot.elevenLabsAudioPath.isNullOrBlank())) {
                _uiState.update { it.copy(turnPreparationMessage = null) }
                speechManager.playSlot(specificSlot, exactName, settings.masterVolume, settings.muteAll) {
                    if (_uiState.value.status == TimerStatus.RUNNING) {
                        onReadyToStartTimer()
                    }
                }
                return@launch
            }

            // Template from general slot or default phrase without diacritics
            val turnSlot = slots.find { it.id == "turn_general" }
            val rawTemplate = turnSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" }
                ?: "يلا يا {الاسم}، دورك"
            val phrase = rawTemplate.replace("{الاسم}", exactName).trim()

            val cached = elevenLabsService.getCachedFile(phrase, elevenSettings)
            if (cached != null && cached.exists() && cached.length() > 0) {
                _uiState.update { it.copy(turnPreparationMessage = null) }
                recorderHelper.playAudio(cached.absolutePath, settings.masterVolume) {
                    if (_uiState.value.status == TimerStatus.RUNNING) {
                        onReadyToStartTimer()
                    }
                }
            } else {
                // Not cached yet: generate on the fly with brief UI indicator
                _uiState.update { it.copy(turnPreparationMessage = "جارٍ تجهيز صوت: $exactName...") }
                val result = elevenLabsService.generateSpeech(phrase, elevenSettings, forceRegenerate = false)
                _uiState.update { it.copy(turnPreparationMessage = null) }

                result.fold(
                    onSuccess = { file ->
                        recorderHelper.playAudio(file.absolutePath, settings.masterVolume) {
                            if (_uiState.value.status == TimerStatus.RUNNING) {
                                onReadyToStartTimer()
                            }
                        }
                    },
                    onFailure = { err ->
                        _uiState.update { it.copy(turnNoticeMessage = err.message) }
                        // Never freeze app: start timer immediately on failure
                        onReadyToStartTimer()
                    }
                )
            }
        }
    }

    fun clearTurnNoticeMessage() {
        _uiState.update { it.copy(turnNoticeMessage = null) }
    }

    fun updatePlayerColor(playerId: Int, newColorHex: Long) {
        viewModelScope.launch {
            repository.updatePlayerColor(playerId, newColorHex)
            refreshPlayers()
        }
    }

    fun updatePlayerPhoneticName(playerId: Int, phoneticName: String) {
        viewModelScope.launch {
            repository.updatePlayerPhoneticName(playerId, phoneticName)
            val player = _uiState.value.players.find { it.id == playerId }
            if (player != null) {
                // Pre-generate audio for this player alone without touching others
                val slots = _uiState.value.customSoundSlots
                val turnSlot = slots.find { it.id == "turn_general" }
                val rawTemplate = turnSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" } ?: "يلا يا {الاسم}، دورك"
                val targetName = phoneticName.ifBlank { player.name }.trim()
                val phrase = rawTemplate.replace("{الاسم}", targetName).trim()
                elevenLabsService.generateSpeech(phrase, _elevenLabsSettings.value, forceRegenerate = false)
            }
            refreshPlayers()
        }
    }

    fun testPronunciation(name: String, phonetic: String) {
        val settings = _uiState.value.settings
        val target = phonetic.ifBlank { name }.trim()
        val slots = _uiState.value.customSoundSlots
        val turnSlot = slots.find { it.id == "turn_general" }
        val rawTemplate = turnSlot?.customText?.ifBlank { "يلا يا {الاسم}، دورك" } ?: "يلا يا {الاسم}، دورك"
        val phrase = rawTemplate.replace("{الاسم}", target).trim()

        viewModelScope.launch {
            val res = elevenLabsService.generateSpeech(phrase, _elevenLabsSettings.value, forceRegenerate = false)
            res.fold(
                onSuccess = { file ->
                    recorderHelper.playAudio(file.absolutePath, settings.masterVolume)
                },
                onFailure = { err ->
                    _elevenLabsMessage.value = err.message
                }
            )
        }
    }

    // ==========================================
    // Custom Sound Slot Actions
    // ==========================================

    fun saveCustomSlot(slot: CustomSoundSlot) {
        repository.saveCustomSoundSlot(slot, _uiState.value.players)
        loadCustomSoundSlots()
    }

    fun deleteCustomSlot(slotId: String) {
        repository.deleteCustomSoundSlot(slotId, _uiState.value.players)
        loadCustomSoundSlots()
    }

    fun resetSlotToDefault(slotId: String) {
        repository.resetSlotToDefault(slotId, _uiState.value.players)
        loadCustomSoundSlots()
    }

    fun resetAllSlotsToDefault() {
        repository.resetAllSlotsToDefault(_uiState.value.players)
        loadCustomSoundSlots()
    }

    fun exportCustomSoundsJson(): String {
        return repository.exportCustomSoundsJson(_uiState.value.players)
    }

    fun importCustomSoundsJson(json: String): Boolean {
        val success = repository.importCustomSoundsJson(json)
        if (success) {
            loadCustomSoundSlots()
        }
        return success
    }

    fun testSlot(slot: CustomSoundSlot, samplePlayerName: String = "") {
        val settings = _uiState.value.settings
        val name = samplePlayerName.ifBlank { _uiState.value.activePlayer.name }
        speechManager.playSlot(slot, name, settings.masterVolume, settings.muteAll)
    }

    // ==========================================
    // ElevenLabs AI Voice Actions & Pre-generation
    // ==========================================

    fun updateElevenLabsSettings(newSettings: ElevenLabsSettings) {
        elevenLabsService.getConfigManager().saveSettings(newSettings)
        _elevenLabsSettings.value = newSettings
    }

    fun testElevenLabsVoice(phrase: String = "يلا يا خالد، دورك!") {
        viewModelScope.launch {
            _isGeneratingElevenLabs.value = "TEST"
            _elevenLabsMessage.value = null
            val currentSettings = _elevenLabsSettings.value
            val result = elevenLabsService.generateSpeech(phrase, currentSettings, forceRegenerate = false)
            result.fold(
                onSuccess = { file ->
                    _totalCharactersUsed.value = elevenLabsService.getConfigManager().getTotalCharactersUsed()
                    recorderHelper.playAudio(file.absolutePath, _uiState.value.settings.masterVolume)
                    _isGeneratingElevenLabs.value = null
                },
                onFailure = { error ->
                    _isGeneratingElevenLabs.value = null
                    _elevenLabsMessage.value = error.message ?: "فشل في توليد الصوت عبر ElevenLabs"
                }
            )
        }
    }

    fun generateSlotElevenLabs(slot: CustomSoundSlot, samplePlayerName: String = "", force: Boolean = true) {
        viewModelScope.launch {
            _isGeneratingElevenLabs.value = slot.id
            _elevenLabsMessage.value = null

            val targetName = samplePlayerName.ifBlank {
                _uiState.value.players.find { it.id == slot.targetPlayerId }?.name ?: "خالد"
            }
            val textToSynthesize = if (slot.customText.isNotBlank()) {
                slot.customText.replace("{الاسم}", targetName).trim()
            } else {
                when (slot.category) {
                    com.example.data.model.SoundSlotCategory.TURN_START -> "يَلَّا يَا $targetName، دَوْرَك!"
                    com.example.data.model.SoundSlotCategory.COUNTDOWN_5 -> "خَمْسَة"
                    com.example.data.model.SoundSlotCategory.COUNTDOWN_4 -> "أَرْبَعَة"
                    com.example.data.model.SoundSlotCategory.COUNTDOWN_3 -> "ثَلَاثَة"
                    com.example.data.model.SoundSlotCategory.COUNTDOWN_2 -> "اِثْنَيْن"
                    com.example.data.model.SoundSlotCategory.COUNTDOWN_1 -> "وَاحِد"
                    com.example.data.model.SoundSlotCategory.TIME_UP -> "اِسْحَبْ ثَلَاثْ يَا غَشِيم!"
                    else -> slot.title
                }
            }

            val currentSettings = _elevenLabsSettings.value
            val result = elevenLabsService.generateSpeech(textToSynthesize, currentSettings, forceRegenerate = force)
            result.fold(
                onSuccess = { file ->
                    val updatedSlot = slot.copy(
                        elevenLabsAudioPath = file.absolutePath,
                        lastGeneratedChars = textToSynthesize.length,
                        lastGeneratedTimestamp = System.currentTimeMillis()
                    )
                    saveCustomSlot(updatedSlot)
                    _totalCharactersUsed.value = elevenLabsService.getConfigManager().getTotalCharactersUsed()
                    _isGeneratingElevenLabs.value = null
                    _elevenLabsMessage.value = "تم توليد الصوت وحفظه محلياً بنجاح ⚡"
                    recorderHelper.playAudio(file.absolutePath, _uiState.value.settings.masterVolume)
                },
                onFailure = { error ->
                    _isGeneratingElevenLabs.value = null
                    _elevenLabsMessage.value = error.message ?: "فشل في توليد الصوت"
                }
            )
        }
    }

    fun pregenerateAllGameSounds(force: Boolean = true) {
        viewModelScope.launch {
            _isGeneratingElevenLabs.value = "ALL"
            _elevenLabsMessage.value = "جارٍ توليد كل الأصوات بالصوت الأساسي وتخزينها..."

            val slots = _uiState.value.customSoundSlots
            var successCount = 0
            for (slot in slots) {
                if (!slot.isEnabled) continue

                if (slot.id == "turn_general" || slot.category == com.example.data.model.SoundSlotCategory.TURN_START) {
                    val rawTemplate = slot.customText.ifBlank { "يلا يا {الاسم}، دورك" }
                    for (player in _uiState.value.players) {
                        val pName = player.name.trim()
                        val text = rawTemplate.replace("{الاسم}", pName).trim()
                        val res = elevenLabsService.generateSpeech(text, _elevenLabsSettings.value, forceRegenerate = force)
                        if (res.isSuccess) successCount++
                    }
                    val previewText = rawTemplate.replace("{الاسم}", "اللاعب").trim()
                    val res = elevenLabsService.generateSpeech(previewText, _elevenLabsSettings.value, forceRegenerate = force)
                    res.onSuccess { file ->
                        val updated = slot.copy(
                            elevenLabsAudioPath = file.absolutePath,
                            lastGeneratedChars = previewText.length,
                            lastGeneratedTimestamp = System.currentTimeMillis()
                        )
                        saveCustomSlot(updated)
                        successCount++
                    }
                    continue
                }

                val text = if (slot.customText.isNotBlank()) {
                    slot.customText.trim()
                } else {
                    when (slot.category) {
                        com.example.data.model.SoundSlotCategory.COUNTDOWN_5 -> "خَمْسَة"
                        com.example.data.model.SoundSlotCategory.COUNTDOWN_4 -> "أَرْبَعَة"
                        com.example.data.model.SoundSlotCategory.COUNTDOWN_3 -> "ثَلَاثَة"
                        com.example.data.model.SoundSlotCategory.COUNTDOWN_2 -> "اِثْنَيْن"
                        com.example.data.model.SoundSlotCategory.COUNTDOWN_1 -> "وَاحِد"
                        com.example.data.model.SoundSlotCategory.TIME_UP -> "اِسْحَبْ ثَلَاثْ يَا غَشِيم!"
                        else -> slot.title
                    }
                }

                if (text.isNotBlank()) {
                    val result = elevenLabsService.generateSpeech(text, _elevenLabsSettings.value, forceRegenerate = force)
                    result.onSuccess { file ->
                        val updated = slot.copy(
                            elevenLabsAudioPath = file.absolutePath,
                            lastGeneratedChars = text.length,
                            lastGeneratedTimestamp = System.currentTimeMillis()
                        )
                        saveCustomSlot(updated)
                        successCount++
                    }
                }
            }

            // Also generate the custom turn switching phrase
            val customPhrase = _uiState.value.settings.turnCustomPhrase.trim().ifBlank { "اللي بعده" }
            val customRes = elevenLabsService.generateSpeech(customPhrase, _elevenLabsSettings.value, forceRegenerate = force)
            if (customRes.isSuccess) successCount++

            _totalCharactersUsed.value = elevenLabsService.getConfigManager().getTotalCharactersUsed()
            _isGeneratingElevenLabs.value = null
            _elevenLabsMessage.value = "اكتمل توليد كل الأصوات ($successCount صوت مخزّن محلياً) ⚡"
        }
    }

    fun resetElevenLabsCharacters() {
        elevenLabsService.getConfigManager().resetCharactersCounter()
        _totalCharactersUsed.value = 0L
    }

    fun testTurnCustomPhrase(phrase: String = "") {
        val settings = _uiState.value.settings
        val targetPhrase = phrase.ifBlank { settings.turnCustomPhrase }.ifBlank { "اللي بعده" }.trim()
        viewModelScope.launch {
            val res = elevenLabsService.generateSpeech(targetPhrase, _elevenLabsSettings.value, forceRegenerate = false)
            res.fold(
                onSuccess = { file ->
                    recorderHelper.playAudio(file.absolutePath, settings.masterVolume)
                },
                onFailure = { err ->
                    _elevenLabsMessage.value = err.message
                }
            )
        }
    }

    fun clearElevenLabsMessage() {
        _elevenLabsMessage.value = null
    }

    fun startOrResume() {
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
        timerJob?.cancel()
        turnPrepJob?.cancel()
        speechManager.stop()
        soundPlayer.stopAll()
        _uiState.update { it.copy(status = TimerStatus.PAUSED) }
    }

    fun reset() {
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

    fun runAudioTestSimulation() {
        timerJob?.cancel()
        turnPrepJob?.cancel()
        speechManager.stop()
        soundPlayer.stopAll()

        val testSec = 10
        val settings = _uiState.value.settings
        _uiState.update {
            it.copy(
                initialSeconds = testSec,
                remainingMillis = testSec * 1000L,
                status = TimerStatus.RUNNING,
                isCriticalWarning = false,
                turnNoticeMessage = null
            )
        }

        val player = _uiState.value.activePlayer
        prepareAndAnnounceTurn(player) {
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
        lastTickTime = System.currentTimeMillis()
        val startTime = System.currentTimeMillis()
        val totalRemaining = _uiState.value.remainingMillis

        timerJob = viewModelScope.launch {
            while (true) {
                delay(25)
                val elapsed = System.currentTimeMillis() - startTime
                val newRemaining = (totalRemaining - elapsed).coerceAtLeast(0L)
                val currentSec = ((newRemaining + 999) / 1000).toInt()
                val settings = _uiState.value.settings
                val slots = _uiState.value.customSoundSlots

                val isCritical = currentSec in 1..5
                val isElevated = currentSec in 6..10

                // 10 seconds warning announcement
                if (currentSec == 10 && lastSpokenSecond != 10 && settings.warningSoundEnabled) {
                    lastSpokenSecond = 10
                    speechManager.play10sWarning(
                        slots = slots,
                        masterVolume = settings.masterVolume,
                        muteAll = settings.muteAll
                    )
                }

                // Voice countdown for numbers 5 down to 1
                if (currentSec in 1..5 && currentSec != lastSpokenSecond && settings.voiceCountdownEnabled) {
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
                val now = System.currentTimeMillis()
                val tickIntervalMs = if (settings.tickSoundMode == "ESCALATING") {
                    when {
                        currentSec in 1..3 -> 333L // 3 ticks/sec
                        currentSec in 4..5 -> 500L // 2 ticks/sec
                        else -> 1000L              // 1 tick/sec
                    }
                } else {
                    1000L
                }

                if (newRemaining > 0 && (now - lastTickTime >= tickIntervalMs)) {
                    lastTickTime = now
                    lastEmittedSecond = currentSec

                    // Escalating volume & pitch calculation
                    val volumeFactor = if (settings.tickSoundMode == "ESCALATING") {
                        when {
                            currentSec in 1..5 -> 0.70f + ((5 - currentSec) / 4.0f) * 0.30f
                            currentSec in 6..10 -> 0.25f + ((10 - currentSec) / 4.0f) * 0.30f
                            else -> 0.25f
                        }
                    } else {
                        0.60f
                    }

                    val pitchFactor = if (settings.tickSoundMode == "ESCALATING") {
                        when {
                            currentSec in 1..5 -> 1.15f + ((5 - currentSec) * 0.03f)
                            currentSec in 6..10 -> 1.0f + ((10 - currentSec) * 0.02f)
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
                        muteAll = settings.muteAll,
                        onFinished = {
                            viewModelScope.launch {
                                delay(600)
                                if (_uiState.value.status == TimerStatus.FINISHED) {
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
        speechManager.shutdown()
        soundPlayer.release()
    }
}
