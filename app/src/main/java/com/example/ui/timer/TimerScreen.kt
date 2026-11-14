package com.example.ui.timer

import android.app.Activity
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.RecordVoiceOver
import com.example.R
import com.example.data.model.Player
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.components.PronunciationDialog
import com.example.ui.theme.OkeyPlayerPalette
import com.example.ui.theme.OkeyTileBone
import com.example.ui.theme.OkeyTileBorder
import com.example.ui.theme.TimerComfort
import com.example.ui.theme.TimerCritical
import com.example.ui.theme.TimerWarning
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TimerScreen(
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showCustomDialog by remember { mutableStateOf(false) }
    var showPronunciationDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }

    // Keep screen awake while timer is running or keepScreenOn setting is enabled
    DisposableEffect(uiState.status, uiState.settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        val shouldKeepOn = uiState.settings.keepScreenOn || uiState.status == TimerStatus.RUNNING
        if (shouldKeepOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Dynamic color: exactly matches the active player's chosen color, turning red on time up/critical warning
    val activePlayerColor = Color(uiState.activePlayer.colorHex)
    val ringColor by animateColorAsState(
        targetValue = when {
            uiState.status == TimerStatus.FINISHED -> TimerCritical
            uiState.isCriticalWarning || uiState.remainingSeconds <= 5 -> TimerCritical
            else -> activePlayerColor
        },
        animationSpec = tween(400),
        label = "ringColor"
    )

    // Pulse animation for last 5 seconds
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.isCriticalWarning) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Font size scaling factor: scales the digits directly!
    val fontScaleMultiplier = when (uiState.settings.timerFontSize) {
        "NORMAL" -> 1.0f
        "LARGE" -> 1.25f
        "EXTRA_LARGE" -> 1.55f
        "HUGE" -> 1.85f
        else -> 1.25f
    }

    if (isLandscape) {
        LandscapeTimerLayout(
            uiState = uiState,
            ringColor = ringColor,
            pulseScale = pulseScale,
            fontScaleMultiplier = fontScaleMultiplier,
            onStartPause = { viewModel.startOrResume() },
            onReset = { viewModel.reset() },
            onNextPlayer = { viewModel.nextPlayer() },
            onSelectPlayer = { viewModel.selectPlayer(it) },
            onSetDuration = { viewModel.setDuration(it) },
            onOpenCustomDialog = { showCustomDialog = true },
            onClearTurnNotice = { viewModel.clearTurnNoticeMessage() },
            onScreenTapped = { viewModel.onScreenTapped() },
            modifier = modifier
        )
    } else {
        PortraitTimerLayout(
            uiState = uiState,
            ringColor = ringColor,
            pulseScale = pulseScale,
            fontScaleMultiplier = fontScaleMultiplier,
            onStartPause = { viewModel.startOrResume() },
            onReset = { viewModel.reset() },
            onNextPlayer = { viewModel.nextPlayer() },
            onSelectPlayer = { viewModel.selectPlayer(it) },
            onSetDuration = { viewModel.setDuration(it) },
            onOpenCustomDialog = { showCustomDialog = true },
            onEditPronunciation = { showPronunciationDialog = true },
            onClearTurnNotice = { viewModel.clearTurnNoticeMessage() },
            onScreenTapped = { viewModel.onScreenTapped() },
            modifier = modifier
        )
    }

    if (showCustomDialog) {
        CustomDurationDialog(
            currentSeconds = uiState.initialSeconds,
            onDismiss = { showCustomDialog = false },
            onConfirm = { sec ->
                viewModel.setDuration(sec)
                showCustomDialog = false
            }
        )
    }

    if (showPronunciationDialog) {
        PronunciationDialog(
            player = uiState.activePlayer,
            onDismiss = { showPronunciationDialog = false },
            onSave = { newPhonetic ->
                viewModel.updatePlayerPhoneticName(uiState.activePlayer.id, newPhonetic)
                showPronunciationDialog = false
            },
            onTest = { name, phonetic ->
                viewModel.testPronunciation(name, phonetic)
            }
        )
    }
}

@Composable
fun PortraitTimerLayout(
    uiState: TimerUiState,
    ringColor: Color,
    pulseScale: Float,
    fontScaleMultiplier: Float,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onNextPlayer: () -> Unit,
    onSelectPlayer: (Int) -> Unit,
    onSetDuration: (Int) -> Unit,
    onOpenCustomDialog: () -> Unit,
    onEditPronunciation: () -> Unit = {},
    onClearTurnNotice: () -> Unit = {},
    onScreenTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                enabled = uiState.settings.tapToAdvance,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onScreenTapped()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Active Player Banner & Presets (only active player shown)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ActivePlayerCard(
                    activePlayer = uiState.activePlayer,
                    onOpenCustomDialog = onOpenCustomDialog,
                    onEditPronunciation = onEditPronunciation
                )

                if (uiState.turnPreparationMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.turnPreparationMessage ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else if (uiState.turnNoticeMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.turnNoticeMessage ?: "",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onClearTurnNotice,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Presets (15, 30, 45, 60 seconds)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(15, 30, 45, 60).forEach { sec ->
                        FilterChip(
                            selected = uiState.initialSeconds == sec,
                            onClick = { onSetDuration(sec) },
                            label = { Text("$sec ث", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Center: Visual Display with animated moving progress indicator
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimerVisualDisplay(
                    style = uiState.settings.timerStyle,
                    progress = uiState.progress,
                    remainingSeconds = uiState.remainingSeconds,
                    ringColor = ringColor,
                    pulseScale = if (uiState.isCriticalWarning) pulseScale else 1f,
                    fontScaleMultiplier = fontScaleMultiplier,
                    isFinished = uiState.status == TimerStatus.FINISHED,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight
                )
            }

            // Bottom Controls Area (Fixed typography & comfortable padding to prevent clipping)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.settings.tapToAdvance) {
                    Text(
                        text = stringResource(R.string.timer_tap_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("timer_reset_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.timer_reset),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Main Start / Pause / Resume Button
                    Button(
                        onClick = onStartPause,
                        modifier = Modifier
                            .weight(1.35f)
                            .height(50.dp)
                            .shadow(3.dp, RoundedCornerShape(14.dp))
                            .testTag("timer_start_pause_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (uiState.status) {
                                TimerStatus.RUNNING -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = when (uiState.status) {
                                TimerStatus.RUNNING -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (uiState.status) {
                                TimerStatus.RUNNING -> stringResource(R.string.timer_pause)
                                TimerStatus.PAUSED -> stringResource(R.string.timer_resume)
                                else -> stringResource(R.string.timer_start)
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Next Player Button
                    FilledTonalButton(
                        onClick = onNextPlayer,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(50.dp)
                            .testTag("timer_next_button"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = stringResource(R.string.timer_next),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun LandscapeTimerLayout(
    uiState: TimerUiState,
    ringColor: Color,
    pulseScale: Float,
    fontScaleMultiplier: Float,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onNextPlayer: () -> Unit,
    onSelectPlayer: (Int) -> Unit,
    onSetDuration: (Int) -> Unit,
    onOpenCustomDialog: () -> Unit,
    onClearTurnNotice: () -> Unit = {},
    onScreenTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                enabled = uiState.settings.tapToAdvance,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onScreenTapped()
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Flank: Active Player, Presets & Turn Info
            Column(
                modifier = Modifier
                    .width(175.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Active player card in landscape (only active player shown, matching their color)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(uiState.activePlayer.colorHex).copy(alpha = 0.15f)
                    ),
                    border = BorderStroke(1.2.dp, Color(uiState.activePlayer.colorHex).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(uiState.activePlayer.colorHex).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "الدور الحالي",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(uiState.activePlayer.colorHex),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        PlayerAvatarBadge(
                            name = uiState.activePlayer.name,
                            colorHex = uiState.activePlayer.colorHex,
                            size = 38.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.activePlayer.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (uiState.turnPreparationMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = uiState.turnPreparationMessage ?: "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else if (uiState.turnNoticeMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = uiState.turnNoticeMessage ?: "",
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            IconButton(
                                onClick = onClearTurnNotice,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    listOf(15, 30, 45, 60).forEach { sec ->
                        FilterChip(
                            selected = uiState.initialSeconds == sec,
                            onClick = { onSetDuration(sec) },
                            label = { Text("$sec ثانية", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(stringResource(R.string.timer_reset), fontSize = 12.sp, maxLines = 1)
                }
            }

            // Center: Massive Landscape Timer with moving progress
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                TimerVisualDisplay(
                    style = uiState.settings.timerStyle,
                    progress = uiState.progress,
                    remainingSeconds = uiState.remainingSeconds,
                    ringColor = ringColor,
                    pulseScale = if (uiState.isCriticalWarning) pulseScale else 1f,
                    fontScaleMultiplier = fontScaleMultiplier * 1.15f,
                    isFinished = uiState.status == TimerStatus.FINISHED,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    isLandscape = true
                )
            }

            // Right Flank: Large Touch Target Controls for players around table
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilledTonalButton(
                    onClick = onNextPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 6.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "الدور التالي",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                    }
                }

                Button(
                    onClick = onStartPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.9f)
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (uiState.status) {
                            TimerStatus.RUNNING -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (uiState.status) {
                                TimerStatus.RUNNING -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (uiState.status) {
                                TimerStatus.RUNNING -> "إيقاف"
                                TimerStatus.PAUSED -> "استئناف"
                                else -> "بدء"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivePlayerCard(
    activePlayer: Player,
    onOpenCustomDialog: () -> Unit,
    onEditPronunciation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(activePlayer.colorHex).copy(alpha = 0.14f)
        ),
        border = BorderStroke(1.4.dp, Color(activePlayer.colorHex).copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatarBadge(
                    name = activePlayer.name,
                    colorHex = activePlayer.colorHex,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(activePlayer.colorHex).copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = "الدور الحالي",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(activePlayer.colorHex),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activePlayer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (activePlayer.phoneticName.isNotBlank()) {
                        Text(
                            text = "نطق: ${activePlayer.phoneticName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEditPronunciation,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("timer_active_player_pronounce_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = stringResource(R.string.edit_pronunciation),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onOpenCustomDialog,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("custom_time_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.timer_custom), fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Renders the chosen timer visual shape.
 * ALL shapes feature a smooth, moving progress indicator.
 * Displays "اسحب ثلاث يا غشيم" when time runs out.
 */
@Composable
fun TimerVisualDisplay(
    style: String,
    progress: Float,
    remainingSeconds: Int,
    ringColor: Color,
    pulseScale: Float,
    fontScaleMultiplier: Float,
    isFinished: Boolean,
    maxWidth: Dp,
    maxHeight: Dp,
    isLandscape: Boolean = false
) {
    val displayDigits = if (isFinished) "0" else "$remainingSeconds"

    // Base font size: scales ONLY the digits!
    val basePt = if (isLandscape) 90f else 74f
    val computedFontSize: TextUnit = (basePt * fontScaleMultiplier).sp

    // Smooth animated progress value for ultra-fluid indicator movement
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "animProgress"
    )

    when (style) {
        "TILE" -> {
            val tileW = minOf(maxWidth * 0.76f, 310.dp)
            val tileH = minOf(maxHeight * 0.85f, 330.dp)

            Box(
                modifier = Modifier
                    .size(width = tileW, height = tileH)
                    .scale(pulseScale)
                    .shadow(10.dp, RoundedCornerShape(26.dp))
                    .background(OkeyTileBone, RoundedCornerShape(26.dp))
                    .border(2.dp, OkeyTileBorder, RoundedCornerShape(26.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 4.dp.toPx()
                    val cornerR = 24.dp.toPx()
                    drawRoundRect(
                        color = ringColor.copy(alpha = 0.15f),
                        size = size,
                        cornerRadius = CornerRadius(cornerR, cornerR),
                        style = Stroke(width = strokeW)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ringColor,
                        trackColor = ringColor.copy(alpha = 0.2f)
                    )

                    Text(
                        text = displayDigits,
                        fontSize = computedFontSize,
                        fontWeight = FontWeight.Black,
                        color = ringColor,
                        lineHeight = computedFontSize,
                        textAlign = TextAlign.Center
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(ringColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isFinished) stringResource(R.string.timer_time_up) else "ثانية",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isFinished) 14.sp else 13.sp,
                            color = if (isFinished) TimerCritical else Color.Black.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        "SPEEDOMETER" -> {
            val gaugeSize = minOf(maxWidth, maxHeight) * 0.78f

            Box(
                modifier = Modifier
                    .size(gaugeSize)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = size.width * 0.08f
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val radius = (size.width - strokeW) / 2f
                    val startAngle = 150f
                    val totalSweep = 240f

                    drawArc(
                        color = ringColor.copy(alpha = 0.15f),
                        startAngle = startAngle,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = Offset(strokeW / 2f, strokeW / 2f),
                        size = Size(size.width - strokeW, size.height - strokeW),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    val currentSweep = animatedProgress * totalSweep
                    drawArc(
                        color = ringColor,
                        startAngle = startAngle,
                        sweepAngle = currentSweep,
                        useCenter = false,
                        topLeft = Offset(strokeW / 2f, strokeW / 2f),
                        size = Size(size.width - strokeW, size.height - strokeW),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    val headAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
                    val headX = (centerOffset.x + radius * cos(headAngleRad)).toFloat()
                    val headY = (centerOffset.y + radius * sin(headAngleRad)).toFloat()
                    drawCircle(
                        color = Color.White,
                        radius = strokeW * 0.65f,
                        center = Offset(headX, headY)
                    )
                    drawCircle(
                        color = ringColor,
                        radius = strokeW * 0.35f,
                        center = Offset(headX, headY)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayDigits,
                        fontSize = computedFontSize,
                        fontWeight = FontWeight.Black,
                        color = ringColor,
                        lineHeight = computedFontSize
                    )
                    Text(
                        text = if (isFinished) stringResource(R.string.timer_time_up) else "ثانية متبقية",
                        fontSize = if (isFinished) 15.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ringColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        "BAR" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.88f else 0.85f)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = ringColor,
                            trackColor = ringColor.copy(alpha = 0.2f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = displayDigits,
                            fontSize = computedFontSize,
                            fontWeight = FontWeight.Black,
                            color = ringColor,
                            lineHeight = computedFontSize
                        )

                        Text(
                            text = if (isFinished) stringResource(R.string.timer_time_up) else "ثانية متبقية",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isFinished) 16.sp else 15.sp,
                            color = ringColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = ringColor,
                            trackColor = ringColor.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }

        "WHEEL" -> {
            val wheelSize = minOf(maxWidth, maxHeight) * 0.78f

            Box(
                modifier = Modifier
                    .size(wheelSize)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.width * 0.42f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val numPips = 16

                    for (i in 0 until numPips) {
                        val angleDeg = (i * (360f / numPips)) - 90f
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val pipFraction = (numPips - i).toFloat() / numPips

                        val isPipActive = animatedProgress >= pipFraction

                        val x = (center.x + radius * cos(angleRad)).toFloat()
                        val y = (center.y + radius * sin(angleRad)).toFloat()

                        drawCircle(
                            color = if (isPipActive) ringColor else ringColor.copy(alpha = 0.18f),
                            radius = if (isPipActive) 9.dp.toPx() else 6.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }

                    drawCircle(
                        color = ringColor.copy(alpha = 0.25f),
                        radius = radius - 16.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius + 16.dp.toPx(), center.y - radius + 16.dp.toPx()),
                        size = Size((radius - 16.dp.toPx()) * 2, (radius - 16.dp.toPx()) * 2),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayDigits,
                        fontSize = computedFontSize,
                        fontWeight = FontWeight.Black,
                        color = ringColor,
                        lineHeight = computedFontSize
                    )
                    Text(
                        text = if (isFinished) stringResource(R.string.timer_time_up) else "ثانية",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isFinished) 15.sp else 14.sp,
                        color = ringColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        "NEON" -> {
            Box(
                modifier = Modifier
                    .scale(pulseScale)
                    .shadow(12.dp, RoundedCornerShape(22.dp))
                    .background(Color(0xFF091410), RoundedCornerShape(22.dp))
                    .border(3.dp, ringColor.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 36.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ringColor,
                        trackColor = ringColor.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = displayDigits.padStart(2, '0'),
                        fontSize = computedFontSize,
                        fontWeight = FontWeight.Black,
                        color = ringColor,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = computedFontSize
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isFinished) stringResource(R.string.timer_time_up) else "• SECONDS REMAINING •",
                        fontSize = if (isFinished) 14.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ringColor.copy(alpha = 0.95f),
                        letterSpacing = if (isFinished) 1.sp else 3.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        "CAPSULE" -> {
            val capsuleW = minOf(maxWidth * 0.82f, 340.dp)
            val capsuleH = minOf(maxHeight * 0.72f, 260.dp)

            Box(
                modifier = Modifier
                    .size(width = capsuleW, height = capsuleH)
                    .scale(pulseScale)
                    .shadow(8.dp, RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
                    .border(2.dp, ringColor.copy(alpha = 0.3f), RoundedCornerShape(percent = 50))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 6.dp.toPx()
                    val cr = size.height / 2f
                    drawRoundRect(
                        color = ringColor,
                        size = size,
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(
                            width = strokeW,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(size.width * 2 * animatedProgress, size.width * 2),
                                0f
                            )
                        )
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayDigits,
                        fontSize = computedFontSize,
                        fontWeight = FontWeight.Black,
                        color = ringColor,
                        lineHeight = computedFontSize
                    )
                    Text(
                        text = if (isFinished) stringResource(R.string.timer_time_up) else "ثانية",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isFinished) 15.sp else 14.sp,
                        color = ringColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        "WAVE" -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.86f else 0.82f)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = displayDigits,
                            fontSize = computedFontSize,
                            fontWeight = FontWeight.Black,
                            color = ringColor,
                            lineHeight = computedFontSize
                        )

                        Text(
                            text = if (isFinished) stringResource(R.string.timer_time_up) else "ثانية متبقية",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isFinished) 16.sp else 15.sp,
                            color = ringColor,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(ringColor.copy(alpha = 0.15f))
                        ) {
                            val activeW = size.width * animatedProgress
                            val path = Path()
                            path.moveTo(0f, size.height)
                            path.lineTo(0f, size.height * 0.3f)

                            var x = 0f
                            val step = 20f
                            while (x <= activeW) {
                                val y = (sin((x / 25f).toDouble()) * 4f + size.height * 0.4f).toFloat()
                                path.lineTo(x, y)
                                x += step
                            }
                            path.lineTo(activeW, size.height)
                            path.close()

                            drawPath(
                                path = path,
                                brush = Brush.horizontalGradient(
                                    listOf(ringColor.copy(alpha = 0.85f), ringColor)
                                )
                            )
                        }
                    }
                }
            }
        }

        else -> {
            // "RING" Royal Crown Ring
            val circleSize = minOf(maxWidth, maxHeight) * 0.76f

            Box(
                modifier = Modifier
                    .size(circleSize)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = size.width * 0.08f
                    val radius = (size.width - strokeW) / 2f
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    drawCircle(
                        color = ringColor.copy(alpha = 0.15f),
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = strokeW)
                    )

                    val sweepAngle = animatedProgress * 360f
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(strokeW / 2f, strokeW / 2f),
                        size = Size(size.width - strokeW, size.height - strokeW),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    if (sweepAngle > 0f) {
                        val headAngleRad = Math.toRadians((sweepAngle - 90f).toDouble())
                        val hx = (centerOffset.x + radius * cos(headAngleRad)).toFloat()
                        val hy = (centerOffset.y + radius * sin(headAngleRad)).toFloat()
                        drawCircle(
                            color = Color.White,
                            radius = strokeW * 0.65f,
                            center = Offset(hx, hy)
                        )
                        drawCircle(
                            color = ringColor,
                            radius = strokeW * 0.35f,
                            center = Offset(hx, hy)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = displayDigits,
                        fontSize = computedFontSize,
                        fontWeight = FontWeight.Black,
                        color = ringColor,
                        textAlign = TextAlign.Center,
                        lineHeight = computedFontSize
                    )
                    Text(
                        text = if (isFinished)
                            stringResource(R.string.timer_time_up)
                        else
                            "ثانية متبقية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFinished) TimerCritical else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CustomDurationDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var seconds by remember { mutableIntStateOf(currentSeconds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحديد وقت الدور (بالثواني)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$seconds ثانية",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (seconds > 5) seconds -= 5 },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "إنقاص 5 ثوان")
                    }

                    IconButton(
                        onClick = { if (seconds < 300) seconds += 5 },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "زيادة 5 ثوان")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(20, 25, 30, 40, 50, 90).forEach { sec ->
                        OutlinedButton(
                            onClick = { seconds = sec },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("$sec", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(seconds) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}
