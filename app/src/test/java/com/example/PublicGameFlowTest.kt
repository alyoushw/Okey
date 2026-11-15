package com.example

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.GameRepository
import com.example.ui.scoreboard.GameSetupScreen
import com.example.ui.scoreboard.ScoreboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h480dp")
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PublicGameFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun namesPrepareAfterTypingWithoutAButtonAndSetupFitsSmallScreens() {
        var prepared = emptyList<String>()
        var started = emptyList<String>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    GameSetupScreen(emptyList(), { names, _, _ -> started = names }, { prepared = it })
                }
            }
        }
        compose.onNodeWithTag("player_name_0").performTextInput("خالد")
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertTrue(prepared.isEmpty()) }
        compose.onNodeWithTag("player_name_0").performTextInput(" محمد")
        compose.mainClock.advanceTimeBy(1100)
        compose.runOnIdle { assertEquals(listOf("خالد محمد"), prepared) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("توليد أصوات اللاعبين الآن ⚡").assertDoesNotExist()
        compose.onNodeWithTag("start_game_button").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(4, started.size)
            assertEquals("خالد محمد", started.first())
            assertEquals("لاعب 2", started[1])
        }
    }

    @Test fun publicNavigationOpensCleanScoreSetup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { GameRepository(app).clearActiveGame() }
        compose.setContent { com.example.ui.MainAppScreen() }
        compose.onNodeWithTag("nav_tab_scoreboard").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("start_game_button").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("لعبة جديدة").assertIsDisplayed()
        compose.onNodeWithText("سجل المحاولات 📋").assertDoesNotExist()
    }

    @Test fun finalFiveSecondsVoiceCanBeDisabledIndependentlyOfTicks() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = GameRepository(app)
        val settings = com.example.data.repository.SettingsState(soundEnabled = false)
        val state = androidx.compose.runtime.mutableStateOf(settings)
        compose.setContent {
            MaterialTheme {
                com.example.ui.settings.SettingsScreen(state.value, {
                    state.value = it
                    repository.saveSettings(it)
                })
            }
        }
        compose.onNodeWithTag("voice_countdown_toggle").performScrollTo().assertIsOn().performClick()
        compose.runOnIdle {
            assertFalse(repository.getSettings().voiceCountdownEnabled)
            assertTrue(repository.getSettings().tickSoundEnabled)
            state.value = state.value.copy(soundEnabled = true)
        }
        compose.onNodeWithTag("tick_escalation_toggle").performScrollTo().assertIsOn().performClick()
        compose.runOnIdle {
            assertEquals("FIXED", repository.getSettings().tickSoundMode)
            assertFalse(repository.getSettings().voiceCountdownEnabled)
        }
    }

    @Test fun completedGameMovesToHistoryAndCannotBeSavedTwice() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = GameRepository(app)
        repository.clearActiveGame()
        repository.clearAllRecords()
        repository.saveSettings(repository.getSettings().copy(soundEnabled = false, voiceAnnouncePlayer = false))
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ScoreboardViewModel::class.java]
        try {
            withTimeout(10000) { vm.uiState.first { !it.isLoading } }
            vm.startNewGame(listOf("خالد", "فهد"), listOf(0xFFD32F2F, 0xFF1976D2), ScoringRule.LOWEST_WINS)
            vm.addRound(mapOf(1 to 0, 2 to 12))
            vm.finishGame()
            vm.finishGame()
            withTimeout(10000) { vm.uiState.first { it.isGameFinished } }
            val records = repository.gameRecords.first()
            assertEquals(1, records.size)
            assertEquals("خالد", records.single().winnerName)
            assertEquals(1, records.single().roundsCount)
            assertNull(repository.loadActiveGame())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
