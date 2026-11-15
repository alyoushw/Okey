package com.example.ui

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.repository.GameRepository
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.scoreboard.ScoreboardScreen
import com.example.ui.scoreboard.ScoreboardViewModel
import com.example.ui.theme.OkeyTheme
import com.example.ui.timer.TimerScreen
import com.example.ui.timer.TimerViewModel

enum class MainTab(val titleRes: Int, val icon: ImageVector) {
    TIMER(R.string.tab_timer, Icons.Default.Timer),
    SCOREBOARD(R.string.tab_scoreboard, Icons.Default.Calculate),
    HISTORY(R.string.tab_history, Icons.Default.History),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val repository = remember { GameRepository(context) }
    val currentSettings by com.example.data.repository.SettingsRepository.settingsState.collectAsState()

    val systemDark = isSystemInDarkTheme()
    val isDark = when (currentSettings.darkThemeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }

    OkeyTheme(darkTheme = isDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(MainTab.TIMER) }
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            val timerViewModel: TimerViewModel = viewModel()
            val scoreboardViewModel: ScoreboardViewModel = viewModel()
            val historyViewModel: HistoryViewModel = viewModel()

            com.example.update.UpdateNotice()

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    // Hide navigation bar in landscape orientation to dedicate 100% of the screen to the giant table timer
                    if (!isLandscape || selectedTab != MainTab.TIMER) {
                        NavigationBar {
                            MainTab.values().forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTab = tab
                                        if (tab == MainTab.TIMER) {
                                            timerViewModel.refreshSettings()
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(tab.titleRes)
                                        )
                                    },
                                    label = {
                                        Text(text = stringResource(tab.titleRes))
                                    },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Crossfade(
                    targetState = selectedTab,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    label = "tab_crossfade"
                ) { tab ->
                    when (tab) {
                        MainTab.TIMER -> TimerScreen(viewModel = timerViewModel)
                        MainTab.SCOREBOARD -> ScoreboardScreen(viewModel = scoreboardViewModel)
                        MainTab.HISTORY -> HistoryScreen(viewModel = historyViewModel)
                        MainTab.SETTINGS -> com.example.ui.settings.SettingsScreen(
                            currentSettings = currentSettings,
                            onSettingsChanged = { updated ->
                                repository.saveSettings(updated)
                                timerViewModel.refreshSettings()
                            }
                        )
                    }
                }
            }
        }
    }
}
