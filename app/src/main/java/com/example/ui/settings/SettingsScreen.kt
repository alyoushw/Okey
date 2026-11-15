package com.example.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.repository.SettingsState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentSettings: SettingsState,
    onSettingsChanged: (SettingsState) -> Unit,
    modifier: Modifier = Modifier
) {
    var duration by remember(currentSettings.defaultTimerSeconds) {
        mutableFloatStateOf(currentSettings.defaultTimerSeconds.toFloat())
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.headlineMedium)
        Text("مدة الدور · ${duration.toInt()} ثانية", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = duration,
            onValueChange = { duration = it },
            onValueChangeFinished = { onSettingsChanged(currentSettings.copy(defaultTimerSeconds = duration.toInt())) },
            valueRange = 10f..120f,
            steps = 21,
            modifier = Modifier.testTag("timer_slider")
        )
        HorizontalDivider()
        Text("الصوت", style = MaterialTheme.typography.titleMedium)
        SettingToggleRow("الأصوات", currentSettings.soundEnabled && !currentSettings.muteAll) {
            onSettingsChanged(currentSettings.copy(soundEnabled = it, muteAll = false))
        }
        SettingToggleRow("صوت آخر ٥ ثوانٍ (٥، ٤، ٣، ٢، ١)", currentSettings.voiceCountdownEnabled,
            tag = "voice_countdown_toggle") {
            onSettingsChanged(currentSettings.copy(voiceCountdownEnabled = it))
        }
        if (currentSettings.soundEnabled) {
            Text("مستوى الصوت")
            Slider(
                value = currentSettings.masterVolume,
                onValueChange = { onSettingsChanged(currentSettings.copy(masterVolume = it)) },
                valueRange = 0f..1f
            )
            SettingToggleRow("تكات المؤقّت", currentSettings.tickSoundEnabled) {
                onSettingsChanged(currentSettings.copy(tickSoundEnabled = it))
            }
            if (currentSettings.tickSoundEnabled) {
                SettingToggleRow("تسارع التكات آخر الدور", currentSettings.tickSoundMode == "ESCALATING",
                    tag = "tick_escalation_toggle") {
                    onSettingsChanged(currentSettings.copy(tickSoundMode = if (it) "ESCALATING" else "FIXED"))
                }
            }
            SettingToggleRow("نطق اسم اللاعب", currentSettings.voiceAnnouncePlayer) {
                onSettingsChanged(currentSettings.copy(voiceAnnouncePlayer = it))
            }
            SettingToggleRow("تنبيه آخر عشر ثوانٍ", currentSettings.warningSoundEnabled) {
                onSettingsChanged(currentSettings.copy(warningSoundEnabled = it))
            }
            SettingToggleRow("عبارة انتهاء الدور", currentSettings.voiceTimeUpPenalty) {
                onSettingsChanged(currentSettings.copy(voiceTimeUpPenalty = it))
            }
        }
        HorizontalDivider()
        Text("أثناء اللعب", style = MaterialTheme.typography.titleMedium)
        SettingToggleRow("الاهتزاز", currentSettings.hapticEnabled) {
            onSettingsChanged(currentSettings.copy(hapticEnabled = it))
        }
        SettingToggleRow("إبقاء الشاشة مضاءة", currentSettings.keepScreenOn) {
            onSettingsChanged(currentSettings.copy(keepScreenOn = it))
        }
        SettingToggleRow("لمس المؤقّت للانتقال للاعب التالي", currentSettings.tapToAdvance) {
            onSettingsChanged(currentSettings.copy(tapToAdvance = it))
        }
        Text("المظهر", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SYSTEM" to "تلقائي", "LIGHT" to "فاتح", "DARK" to "داكن").forEach { (mode, title) ->
                FilterChip(
                    selected = currentSettings.darkThemeMode == mode,
                    onClick = { onSettingsChanged(currentSettings.copy(darkThemeMode = mode)) },
                    label = { Text(title) }
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(title: String, checked: Boolean, tag: String = "", onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
