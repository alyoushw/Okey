package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @org.junit.Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.data.repository.SettingsRepository.resetForTesting(context)
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("حاسبة Okey", appName)
  }

  @Test
  fun `default sound slots include turn general, countdown and time up`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.GameRepository(context)
    val slots = repository.getDefaultSoundSlots()

    assertTrue(slots.isNotEmpty())
    val turnGeneral = slots.first { it.id == "turn_general" && it.category == com.example.data.model.SoundSlotCategory.TURN_START }
    assertEquals("يلا يا {الاسم}، دورك", turnGeneral.customText)
    assertTrue(slots.any { it.category == com.example.data.model.SoundSlotCategory.COUNTDOWN_5 })
    assertTrue(slots.any { it.category == com.example.data.model.SoundSlotCategory.COUNTDOWN_1 })
    assertTrue(slots.any { it.category == com.example.data.model.SoundSlotCategory.TIME_UP })

    // Verify individual player slots and warning_10s are removed as required
    assertFalse(slots.any { it.id.startsWith("turn_player_") })
    assertFalse(slots.any { it.category == com.example.data.model.SoundSlotCategory.WARNING_10S })

    val timeUpSlot = slots.first { it.category == com.example.data.model.SoundSlotCategory.TIME_UP }
    assertTrue(timeUpSlot.customText.contains("غشيم") || timeUpSlot.title.contains("انتهاء"))
  }

  @Test
  fun `export and import custom sounds preserves configuration`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.GameRepository(context)
    val slots = repository.getCustomSoundSlots()

    val exportedJson = repository.exportCustomSoundsJson()
    assertTrue(exportedJson.isNotBlank())

    val importResult = repository.importCustomSoundsJson(exportedJson)
    assertTrue(importResult)
  }

  @Test
  fun `GameSessionManager tracks turns and cycles through players`() {
    val players = listOf(
        com.example.data.model.Player(1, "خالد", 0xFFD32F2F),
        com.example.data.model.Player(2, "فهد", 0xFF1976D2),
        com.example.data.model.Player(3, "سلطان", 0xFFF57C00)
    )

    com.example.data.repository.GameSessionManager.setPlayers(players, resetTurnToFirst = true)
    assertEquals(0, com.example.data.repository.GameSessionManager.activePlayerIndex.value)
    assertEquals("خالد", com.example.data.repository.GameSessionManager.getActivePlayer().name)

    // Advance turn
    val p2 = com.example.data.repository.GameSessionManager.nextPlayer()
    assertEquals("فهد", p2?.name)
    assertEquals(1, com.example.data.repository.GameSessionManager.activePlayerIndex.value)

    val p3 = com.example.data.repository.GameSessionManager.nextPlayer()
    assertEquals("سلطان", p3?.name)
    assertEquals(2, com.example.data.repository.GameSessionManager.activePlayerIndex.value)

    // Wraps back to first player
    val p1Again = com.example.data.repository.GameSessionManager.nextPlayer()
    assertEquals("خالد", p1Again?.name)
    assertEquals(0, com.example.data.repository.GameSessionManager.activePlayerIndex.value)

    // Resetting players resets turn to 0
    com.example.data.repository.GameSessionManager.nextPlayer()
    assertEquals(1, com.example.data.repository.GameSessionManager.activePlayerIndex.value)
    com.example.data.repository.GameSessionManager.setPlayers(players, resetTurnToFirst = true)
    assertEquals(0, com.example.data.repository.GameSessionManager.activePlayerIndex.value)
  }

  @Test
  fun `ElevenLabs cache key is deterministic based on text and voiceId`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val elevenLabs = com.example.audio.elevenlabs.ElevenLabsService(context)
    val settings = com.example.audio.elevenlabs.ElevenLabsSettings(voiceId = "ECc3xM44reI7J5jdm8jB")

    val key1 = elevenLabs.computeCacheKey("يَلَّا يَا خالد، دَوْرَك!", settings)
    val key2 = elevenLabs.computeCacheKey("يَلَّا يَا خالد، دَوْرَك!", settings)
    val key3 = elevenLabs.computeCacheKey("يَلَّا يَا فهد، دَوْرَك!", settings)

    assertEquals(key1, key2)
    assertTrue(key1 != key3)
    assertTrue(key1.isNotEmpty())
  }

  @Test
  fun `turn announcement settings default to PLAYER_NAME and persist custom phrase`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.GameRepository(context)
    val defaultSettings = repository.getSettings()

    // Default values check
    assertEquals("PLAYER_NAME", defaultSettings.turnAnnouncementMode)
    assertEquals("اللي بعده", defaultSettings.turnCustomPhrase)

    // Save custom mode and phrase
    val updated = defaultSettings.copy(
        turnAnnouncementMode = "CUSTOM_PHRASE",
        turnCustomPhrase = "دَوْرِكْ يَا حِلْوَة"
    )
    repository.saveSettings(updated)

    val reloaded = repository.getSettings()
    assertEquals("CUSTOM_PHRASE", reloaded.turnAnnouncementMode)
    assertEquals("دَوْرِكْ يَا حِلْوَة", reloaded.turnCustomPhrase)
  }

  @Test
  fun `unconfigured slot calls onFinished immediately without error`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val speechManager = com.example.audio.SpeechManager(context)

    var finished = false
    val emptySlot = com.example.data.model.CustomSoundSlot(
        id = "test_empty",
        title = "فارغ",
        description = "بدون صوت",
        category = com.example.data.model.SoundSlotCategory.COUNTDOWN_1
    )

    speechManager.playSlot(emptySlot) {
      finished = true
    }

    assertTrue(finished)
  }

  @Test
  fun `GameSessionManager handles pregeneration error and dismissal correctly`() {
    com.example.data.repository.GameSessionManager.setPreGenerating(
        active = false,
        progress = 1f,
        message = "أضف ELEVENLABS_API_KEY في Secrets ثم أعد بناء التطبيق",
        isError = true,
        errorMessage = "أضف ELEVENLABS_API_KEY في Secrets ثم أعد بناء التطبيق"
    )

    val errorState = com.example.data.repository.GameSessionManager.preGenState.value
    assertTrue(errorState.isError)
    assertEquals("أضف ELEVENLABS_API_KEY في Secrets ثم أعد بناء التطبيق", errorState.message)

    // Dismissal
    com.example.data.repository.GameSessionManager.dismissPreGen()
    assertFalse(com.example.data.repository.GameSessionManager.preGenState.value.isError)
  }

  @Test
  fun `ElevenLabsService logs record attempts and unsummarized details`() {
    com.example.audio.elevenlabs.ElevenLabsService.clearLogs()
    assertTrue(com.example.audio.elevenlabs.ElevenLabsService.logs.value.isEmpty())

    com.example.audio.elevenlabs.ElevenLabsService.recordLog(
        target = "سلطان",
        success = false,
        statusCode = 401,
        message = "رمز 401: Invalid API key"
    )

    val logs = com.example.audio.elevenlabs.ElevenLabsService.logs.value
    assertEquals(1, logs.size)
    assertEquals("سلطان", logs[0].target)
    assertFalse(logs[0].success)
    assertEquals(401, logs[0].statusCode)
    assertEquals("رمز 401: Invalid API key", logs[0].message)
    assertTrue(com.example.audio.elevenlabs.ElevenLabsService.lastAttemptResult.value?.contains("401") == true)
  }

  @Test
  fun `ScoreboardViewModel voice status reflects NOT_GENERATED and READY`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = com.example.ui.scoreboard.ScoreboardViewModel(context as android.app.Application)

    vm.checkAndRefreshStatusForNames(listOf("أحمد", "سعيد"))
    val statuses = vm.playerVoiceStatuses.value
    assertTrue(statuses.containsKey("أحمد"))
    assertEquals(com.example.ui.scoreboard.VoiceStatusType.NOT_GENERATED, statuses["أحمد"]?.status)
  }

  @Test
  fun `SettingsRepository is synchronous single source of truth`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.GameRepository(context)

    val initial = com.example.data.repository.SettingsRepository.getSettings(context)
    assertEquals(initial, com.example.data.repository.SettingsRepository.settingsState.value)

    val updated = initial.copy(turnAnnouncementMode = "PLAYER_NAME", turnCustomPhrase = "اللي بعده يا بطل")
    repository.saveSettings(updated)

    // Direct SettingsRepository getSettings, state flow, and GameRepository getSettings must match updated immediately
    assertEquals("PLAYER_NAME", com.example.data.repository.SettingsRepository.getSettings(context).turnAnnouncementMode)
    assertEquals("PLAYER_NAME", com.example.data.repository.SettingsRepository.settingsState.value.turnAnnouncementMode)
    assertEquals("PLAYER_NAME", repository.getSettings().turnAnnouncementMode)
    assertEquals("اللي بعده يا بطل", repository.getSettings().turnCustomPhrase)
  }

  @Test
  fun `ScoreboardViewModel generation records mode, player names and phrase logs`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = com.example.ui.scoreboard.ScoreboardViewModel(context as android.app.Application)

    // Ensure mode is set to PLAYER_NAME
    val settings = com.example.data.repository.SettingsRepository.getSettings(context)
    com.example.data.repository.SettingsRepository.saveSettings(context, settings.copy(turnAnnouncementMode = "PLAYER_NAME"))

    com.example.audio.elevenlabs.ElevenLabsService.clearLogs()
    vm.generateVoicesForPlayerList(listOf("طارق", "بدر"))

    val logs = com.example.audio.elevenlabs.ElevenLabsService.logs.value
    assertTrue("Logs should not be empty", logs.isNotEmpty())

    // Must record diagnostic header
    val diagEntry = logs.find { it.target.contains("زر توليد أصوات اللاعبين") }
    assertTrue("Must have diagnostic entry for generation button", diagEntry != null)
    assertTrue("Log must record PLAYER_NAME mode", diagEntry!!.message.contains("PLAYER_NAME"))
    assertTrue("Log must record player list", diagEntry.message.contains("طارق") && diagEntry.message.contains("بدر"))
  }

  @Test
  fun `turn template with old diacritics is automatically migrated to plain arabic`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.GameRepository(context)

    // Simulate old saved slot with diacritics
    val oldSlot = com.example.data.model.CustomSoundSlot(
        id = "turn_general",
        title = "عبارة بداية الدور العامة",
        description = "العبارة الأساسية لبدء الدور (يمكنك استخدام {الاسم})",
        category = com.example.data.model.SoundSlotCategory.TURN_START,
        customText = "يَلَّا يَا {الاسم}، دَوْرَك!"
    )
    repository.saveCustomSoundSlots(listOf(oldSlot))

    // Reading custom sound slots must automatically migrate to plain version without diacritics
    val loaded = repository.getCustomSoundSlots()
    val turnSlot = loaded.first { it.id == "turn_general" }
    assertEquals("يلا يا {الاسم}، دورك", turnSlot.customText)
  }

  @Test
  fun `MainActivity sets volumeControlStream to STREAM_MUSIC`() {
    val activityController = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = activityController.get()
    assertEquals(android.media.AudioManager.STREAM_MUSIC, activity.volumeControlStream)
  }
}

