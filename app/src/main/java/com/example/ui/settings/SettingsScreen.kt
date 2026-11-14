package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.example.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import com.example.audio.AudioRecorderHelper
import com.example.data.model.CustomSoundSlot
import com.example.data.model.Player
import com.example.data.repository.SettingsState
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.components.PronunciationDialog
import com.example.ui.theme.OkeyPlayerPalette

@Composable
fun SettingsScreen(
    currentSettings: SettingsState,
    onSettingsChanged: (SettingsState) -> Unit,
    players: List<Player> = emptyList(),
    customSoundSlots: List<CustomSoundSlot> = emptyList(),
    recorderHelper: AudioRecorderHelper? = null,
    onPlayerColorChanged: (Int, Long) -> Unit = { _, _ -> },
    onPlayerPhoneticChanged: (Int, String) -> Unit = { _, _ -> },
    onTestPronunciation: (String, String) -> Unit = { _, _ -> },
    onSaveSlot: (CustomSoundSlot) -> Unit = {},
    onDeleteSlot: (String) -> Unit = {},
    onResetSlot: (String) -> Unit = {},
    onResetAllSlots: () -> Unit = {},
    onExportJson: () -> String = { "" },
    onImportJson: (String) -> Boolean = { false },
    onTestSlot: (CustomSoundSlot) -> Unit = {},
    onRunSoundTest: () -> Unit = {},
    elevenLabsSettings: com.example.audio.elevenlabs.ElevenLabsSettings = com.example.audio.elevenlabs.ElevenLabsSettings(),
    onUpdateElevenLabsSettings: (com.example.audio.elevenlabs.ElevenLabsSettings) -> Unit = {},
    onTestElevenLabsVoice: (String) -> Unit = {},
    onGenerateSlotElevenLabs: (CustomSoundSlot) -> Unit = {},
    onPregenerateAllElevenLabs: () -> Unit = {},
    totalCharactersUsed: Long = 0L,
    onResetCharactersCounter: () -> Unit = {},
    isGeneratingElevenLabs: String? = null,
    elevenLabsMessage: String? = null,
    onClearElevenLabsMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var settings by remember(currentSettings) { mutableStateOf(currentSettings) }
    var sliderValue by remember(currentSettings.defaultTimerSeconds) { mutableFloatStateOf(currentSettings.defaultTimerSeconds.toFloat()) }
    var editingPlayerPronunciation by remember { mutableStateOf<Player?>(null) }
    var showCustomSoundsScreen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveRecorderHelper = remember(recorderHelper, context) {
        recorderHelper ?: AudioRecorderHelper(context)
    }

    if (showCustomSoundsScreen) {
        BackHandler { showCustomSoundsScreen = false }
        CustomSoundsScreen(
            slots = customSoundSlots,
            players = players,
            recorderHelper = effectiveRecorderHelper,
            elevenLabsSettings = elevenLabsSettings,
            onUpdateElevenLabsSettings = onUpdateElevenLabsSettings,
            onTestElevenLabsVoice = onTestElevenLabsVoice,
            onGenerateSlotElevenLabs = onGenerateSlotElevenLabs,
            onPregenerateAllElevenLabs = onPregenerateAllElevenLabs,
            totalCharactersUsed = totalCharactersUsed,
            onResetCharactersCounter = onResetCharactersCounter,
            isGeneratingElevenLabs = isGeneratingElevenLabs,
            elevenLabsMessage = elevenLabsMessage,
            onClearElevenLabsMessage = onClearElevenLabsMessage,
            onSaveSlot = onSaveSlot,
            onDeleteSlot = onDeleteSlot,
            onResetSlot = onResetSlot,
            onResetAllSlots = onResetAllSlots,
            onExportJson = onExportJson,
            onImportJson = onImportJson,
            onTestSlot = onTestSlot,
            onRunFullTest = onRunSoundTest,
            onNavigateBack = { showCustomSoundsScreen = false },
            modifier = modifier
        )
        return
    }

    fun updateAndNotify(newSettings: SettingsState) {
        settings = newSettings
        onSettingsChanged(newSettings)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Timer Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.default_timer_duration),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${sliderValue.toInt()} ثانية",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            updateAndNotify(settings.copy(defaultTimerSeconds = sliderValue.toInt()))
                        },
                        valueRange = 10f..120f,
                        steps = 21,
                        modifier = Modifier.testTag("timer_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingToggleRow(
                        title = stringResource(R.string.keep_screen_on),
                        subtitle = "منع إطفاء الشاشة أثناء تشغيل المؤقّت",
                        icon = Icons.Default.Visibility,
                        checked = settings.keepScreenOn,
                        onCheckedChange = { updateAndNotify(settings.copy(keepScreenOn = it)) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = stringResource(R.string.tap_to_advance),
                        subtitle = "النقر على الشاشة للتبديل السريع لدور اللاعب التالي",
                        icon = Icons.Default.TouchApp,
                        checked = settings.tapToAdvance,
                        onCheckedChange = { updateAndNotify(settings.copy(tapToAdvance = it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timer Shapes & Sizes Customization Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "مظهر وشكل المؤقّت",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "جميع الأشكال مزوّدة بشريط أو مؤشر متحرك يبيّن تدفق الوقت",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Timer Shape selection (8 distinct styles)
                    Text(
                        text = stringResource(R.string.timer_style_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val stylesList = listOf(
                        "RING" to (stringResource(R.string.timer_style_ring) to "⭕"),
                        "TILE" to (stringResource(R.string.timer_style_tile) to "🀄"),
                        "SPEEDOMETER" to (stringResource(R.string.timer_style_speedometer) to "⏱️"),
                        "BAR" to (stringResource(R.string.timer_style_bar) to "📊"),
                        "WHEEL" to (stringResource(R.string.timer_style_wheel) to "🎡"),
                        "NEON" to (stringResource(R.string.timer_style_neon) to "💡"),
                        "CAPSULE" to (stringResource(R.string.timer_style_capsule) to "🏟️"),
                        "WAVE" to (stringResource(R.string.timer_style_wave) to "🌊")
                    )

                    // Display in 2 rows of 4 or 4 rows of 2
                    for (chunk in stylesList.chunked(2)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for ((styleKey, pair) in chunk) {
                                val (styleLabel, iconEmoji) = pair
                                FilterChip(
                                    selected = settings.timerStyle == styleKey,
                                    onClick = { updateAndNotify(settings.copy(timerStyle = styleKey)) },
                                    label = { Text("$iconEmoji $styleLabel", fontSize = 13.sp, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Timer Font Size selection (ONLY scales the text numbers)
                    Text(
                        text = stringResource(R.string.timer_size_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "يكبّر خط أرقام العد التنازلي للقراءة بوضوح من طرف الطاولة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "NORMAL" to stringResource(R.string.timer_size_normal),
                            "LARGE" to stringResource(R.string.timer_size_large),
                            "EXTRA_LARGE" to stringResource(R.string.timer_size_extra),
                            "HUGE" to stringResource(R.string.timer_size_huge)
                        ).forEach { (fontKey, fontLabel) ->
                            FilterChip(
                                selected = settings.timerFontSize == fontKey,
                                onClick = { updateAndNotify(settings.copy(timerFontSize = fontKey)) },
                                label = { Text(fontLabel, fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Player Colors Customization Card
            val displayedPlayers = if (players.isNotEmpty()) players else listOf(
                Player(1, "لاعب 1", 0xFFD32F2F),
                Player(2, "لاعب 2", 0xFF1976D2),
                Player(3, "لاعب 3", 0xFFF57C00),
                Player(4, "لاعب 4", 0xFF2B2D42)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.player_colors_title),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(R.string.player_colors_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    displayedPlayers.forEachIndexed { index, player ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    PlayerAvatarBadge(
                                        name = player.name,
                                        colorHex = player.colorHex,
                                        size = 34.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = player.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (player.phoneticName.isNotBlank()) {
                                            Text(
                                                text = "النطق: ${player.phoneticName}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { editingPlayerPronunciation = player },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("edit_pronunciation_${player.id}")
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تعديل النطق", fontSize = 11.5.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Horizontal scroll of selectable Okey colors
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OkeyPlayerPalette.forEach { colorHex ->
                                    val isSelected = player.colorHex == colorHex
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .shadow(if (isSelected) 4.dp else 1.dp, CircleShape)
                                            .clip(CircleShape)
                                            .background(Color(colorHex))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.5.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                onPlayerColorChanged(player.id, colorHex)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio & Haptics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Quick Mute All Toggle
                    SettingToggleRow(
                        title = stringResource(R.string.mute_all_title),
                        subtitle = stringResource(R.string.mute_all_desc),
                        icon = if (settings.muteAll) Icons.Default.VolumeOff else Icons.Default.VolumeMute,
                        checked = settings.muteAll,
                        onCheckedChange = { updateAndNotify(settings.copy(muteAll = it)) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Master Volume Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.master_volume_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            text = "${(settings.masterVolume * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.masterVolume,
                        onValueChange = { updateAndNotify(settings.copy(masterVolume = it)) },
                        valueRange = 0f..1f,
                        enabled = !settings.muteAll,
                        modifier = Modifier.fillMaxWidth().testTag("master_volume_slider")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = stringResource(R.string.sound_effects),
                        subtitle = "تشغيل أو كتم كافة المؤثرات الصوتية",
                        icon = Icons.Default.VolumeUp,
                        checked = settings.soundEnabled,
                        onCheckedChange = { updateAndNotify(settings.copy(soundEnabled = it)) }
                    )

                    if (settings.soundEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingToggleRow(
                            title = stringResource(R.string.sound_tick),
                            subtitle = "صوت التكة مع كل ثانية أثناء العد التنازلي",
                            icon = Icons.Default.Schedule,
                            checked = settings.tickSoundEnabled,
                            onCheckedChange = { updateAndNotify(settings.copy(tickSoundEnabled = it)) }
                        )

                        if (settings.tickSoundEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Tick Mode (Fixed vs Escalating)
                            Text(
                                text = stringResource(R.string.tick_mode_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "ESCALATING" to stringResource(R.string.tick_mode_escalating),
                                    "FIXED" to stringResource(R.string.tick_mode_fixed)
                                ).forEach { (modeKey, modeLabel) ->
                                    FilterChip(
                                        selected = settings.tickSoundMode == modeKey,
                                        onClick = { updateAndNotify(settings.copy(tickSoundMode = modeKey)) },
                                        label = { Text(modeLabel, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Tick Tone choice
                            Text(
                                text = stringResource(R.string.tick_tone_title),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "WOOD" to stringResource(R.string.tick_tone_wood),
                                    "DIGITAL" to stringResource(R.string.tick_tone_digital),
                                    "CLICK" to stringResource(R.string.tick_tone_click),
                                    "CLASSIC" to stringResource(R.string.tick_tone_classic)
                                ).forEach { (toneKey, toneLabel) ->
                                    FilterChip(
                                        selected = settings.tickTone == toneKey,
                                        onClick = { updateAndNotify(settings.copy(tickTone = toneKey)) },
                                        label = { Text(toneLabel, fontSize = 11.5.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SettingToggleRow(
                            title = stringResource(R.string.sound_warning),
                            subtitle = "صوت تنبيه متسارع في آخر 5 ثوانٍ",
                            icon = Icons.Default.VolumeUp,
                            checked = settings.warningSoundEnabled,
                            onCheckedChange = { updateAndNotify(settings.copy(warningSoundEnabled = it)) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingToggleRow(
                        title = stringResource(R.string.haptic_feedback),
                        subtitle = "اهتزاز خفيف مع الثواني واهتزاز قوي عند انتهاء الوقت",
                        icon = Icons.Default.Vibration,
                        checked = settings.hapticEnabled,
                        onCheckedChange = { updateAndNotify(settings.copy(hapticEnabled = it)) }
                    )
                }
            }

            // Custom Sounds & Voice Recording Hub Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCustomSoundsScreen = true }
                    .testTag("open_custom_sounds_screen_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "الأصوات والعبارات (تسجيل أو تشكيل)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "تخصيص كامل: سجّل بصوتك أو اكتب بالتشكيل لكل لاعب ولكل ثانية بالمؤقّت",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 2
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "فتح شاشة الأصوات والعبارات",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Natural Saudi Voice Announcements Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.voice_announcements),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = stringResource(R.string.voice_announce_player),
                        subtitle = "نطق صوتي طبيعي ومتحمس عند تبديل دور اللاعب",
                        icon = Icons.Default.RecordVoiceOver,
                        checked = settings.voiceAnnouncePlayer,
                        onCheckedChange = { updateAndNotify(settings.copy(voiceAnnouncePlayer = it)) }
                    )

                    if (settings.voiceAnnouncePlayer) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Turn announcement mode selector (Player Name vs Custom Phrase)
                        Text(
                            text = "نوع النطق عند تبديل الدور",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = settings.turnAnnouncementMode == "PLAYER_NAME",
                                onClick = { updateAndNotify(settings.copy(turnAnnouncementMode = "PLAYER_NAME")) },
                                label = { Text("اسم اللاعب", fontSize = 12.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("turn_mode_player_name")
                            )

                            FilterChip(
                                selected = settings.turnAnnouncementMode == "CUSTOM_PHRASE",
                                onClick = { updateAndNotify(settings.copy(turnAnnouncementMode = "CUSTOM_PHRASE")) },
                                label = { Text("كلمة من اختياري", fontSize = 12.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("turn_mode_custom_phrase")
                            )
                        }

                        if (settings.turnAnnouncementMode == "CUSTOM_PHRASE") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = settings.turnCustomPhrase,
                                    onValueChange = { updateAndNotify(settings.copy(turnCustomPhrase = it)) },
                                    label = { Text("الكلمة أو العبارة (تدعم التشكيل)") },
                                    placeholder = { Text("اللي بعده") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("turn_custom_phrase_input")
                                )

                                FilledTonalButton(
                                    onClick = {
                                        val phraseToPlay = settings.turnCustomPhrase.trim().ifBlank { "اللي بعده" }
                                        onTestElevenLabsVoice(phraseToPlay)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("test_custom_phrase_btn")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("استمع", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Voice Gender Selection
                        Text(
                            text = stringResource(R.string.voice_gender_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "MALE" to stringResource(R.string.voice_gender_male),
                                "FEMALE" to stringResource(R.string.voice_gender_female)
                            ).forEach { (genderKey, genderLabel) ->
                                FilterChip(
                                    selected = settings.voiceGender == genderKey,
                                    onClick = { updateAndNotify(settings.copy(voiceGender = genderKey)) },
                                    label = { Text(genderLabel, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Voice Dialect Selection
                        Text(
                            text = stringResource(R.string.voice_dialect_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "NAJDI" to stringResource(R.string.voice_dialect_najdi),
                                "HIJAZI" to stringResource(R.string.voice_dialect_hijazi)
                            ).forEach { (dialectKey, dialectLabel) ->
                                FilterChip(
                                    selected = settings.voiceDialect == dialectKey,
                                    onClick = { updateAndNotify(settings.copy(voiceDialect = dialectKey)) },
                                    label = { Text(dialectLabel, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Voice countdown toggle (5..1)
                    SettingToggleRow(
                        title = stringResource(R.string.voice_countdown_title),
                        subtitle = stringResource(R.string.voice_countdown_desc),
                        icon = Icons.Default.Hearing,
                        checked = settings.voiceCountdownEnabled,
                        onCheckedChange = { updateAndNotify(settings.copy(voiceCountdownEnabled = it)) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    SettingToggleRow(
                        title = stringResource(R.string.voice_time_up_penalty),
                        subtitle = "نطق الجملة المشهورة «اسحب ثلاث يا غشيم» بصوت حماسي عند انتهاء الوقت",
                        icon = Icons.Default.VolumeUp,
                        checked = settings.voiceTimeUpPenalty,
                        onCheckedChange = { updateAndNotify(settings.copy(voiceTimeUpPenalty = it)) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Audio System Simulation Test Button
                    FilledTonalButton(
                        onClick = onRunSoundTest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_audio_test_btn")
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.audio_test_button),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.audio_test_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.theme_selection),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            "SYSTEM" to "تلقائي (النظام)",
                            "LIGHT" to "فاتح",
                            "DARK" to "داكن"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.darkThemeMode == mode,
                                onClick = { updateAndNotify(settings.copy(darkThemeMode = mode)) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About Footer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "رفيق أوكي • Okey Companion",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "تطبيق لإدارة جلسات لعبة أوكي الواقعية (بدون إنترنت)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        editingPlayerPronunciation?.let { targetPlayer ->
            PronunciationDialog(
                player = targetPlayer,
                onDismiss = { editingPlayerPronunciation = null },
                onSave = { newPhonetic ->
                    onPlayerPhoneticChanged(targetPlayer.id, newPhonetic)
                    editingPlayerPronunciation = null
                },
                onTest = { name, phonetic ->
                    onTestPronunciation(name, phonetic)
                }
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
