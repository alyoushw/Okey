package com.example.ui.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.RoundEntry
import com.example.ui.components.PlayerAvatarBadge

import androidx.compose.foundation.layout.PaddingValues
import com.example.ui.components.GenerationLogsDialog

@Composable
fun ScoreboardScreen(
    viewModel: ScoreboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerVoiceStatuses by viewModel.playerVoiceStatuses.collectAsState()
    val isGeneratingVoices by viewModel.isGeneratingAllVoices.collectAsState()
    val voiceErrorMessage by viewModel.voiceErrorMessage.collectAsState()

    var showNewRoundDialog by remember { mutableStateOf(false) }
    var editingRound by remember { mutableStateOf<RoundEntry?>(null) }
    var showEndGameConfirmation by remember { mutableStateOf(false) }
    var selectedErrorDetail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showLogsDialog by remember { mutableStateOf(false) }

    if (!uiState.hasActiveGame) {
        GameSetupScreen(
            recentPlayerNames = uiState.recentPlayerNames,
            onStartGame = { names, colors, rule ->
                viewModel.startNewGame(names, colors, rule)
            },
            playerVoiceStatuses = playerVoiceStatuses,
            isGeneratingVoices = isGeneratingVoices,
            onGenerateAllVoices = { viewModel.generateVoicesForPlayerList(it) },
            onPlayPlayerVoice = { viewModel.testAndPlayPlayerVoice(it) },
            onCheckVoiceStatuses = { viewModel.checkAndRefreshStatusForNames(it) },
            voiceErrorMessage = voiceErrorMessage,
            onClearVoiceErrorMessage = { viewModel.clearVoiceErrorMessage() },
            modifier = modifier
        )
        return
    }

    if (uiState.isGameFinished) {
        FinalResultScreen(
            results = uiState.finalResults,
            rounds = uiState.rounds,
            scoringRule = uiState.scoringRule,
            onNewGameSamePlayers = { viewModel.startNewGameSamePlayers() },
            onResetToNewGame = { viewModel.resetToNewGameSetup() },
            modifier = modifier
        )
        return
    }

    // Refresh voice statuses for active players on entry
    androidx.compose.runtime.LaunchedEffect(uiState.players) {
        viewModel.checkAndRefreshStatusForNames(uiState.players.map { it.name })
    }

    // Unsummarized error modal
    if (selectedErrorDetail != null) {
        val (playerName, errorText) = selectedErrorDetail!!
        AlertDialog(
            onDismissRequest = { selectedErrorDetail = null },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "تفاصيل خطأ نطق اللاعب: $playerName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "الاستجابة المباشرة كما وردت من ElevenLabs دون تلخيص:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(onClick = { selectedErrorDetail = null }) {
                    Text("حسناً")
                }
            }
        )
    }

    if (showLogsDialog) {
        GenerationLogsDialog(onDismissRequest = { showLogsDialog = false })
    }

    val horizontalScroll = rememberScrollState()
    val columnWidth = 92.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Voice Pregeneration Progress & Error Notification
            val preGenState by com.example.data.repository.GameSessionManager.preGenState.collectAsState()
            if (preGenState.isGenerating || preGenState.message != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            preGenState.isError -> MaterialTheme.colorScheme.errorContainer
                            preGenState.isGenerating -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color(0xFFE8F5E9)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                if (preGenState.isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (preGenState.isError) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = preGenState.message ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        preGenState.isError -> MaterialTheme.colorScheme.onErrorContainer
                                        preGenState.isGenerating -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> Color(0xFF1B5E20)
                                    }
                                )
                            }
                            if (!preGenState.isGenerating) {
                                IconButton(
                                    onClick = { com.example.data.repository.GameSessionManager.dismissPreGen() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "إغلاق",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (preGenState.isError) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                        if (preGenState.isGenerating) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { preGenState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Voice Diagnostics and Generation Card for Active Game
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Hearing,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "أصوات اللاعبين (ElevenLabs)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showLogsDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("سجل المحاولات 📋", fontSize = 11.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    viewModel.generateVoicesForPlayerList(uiState.players.map { it.name })
                                },
                                enabled = !isGeneratingVoices,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isGeneratingVoices) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("جارٍ...", fontSize = 11.sp)
                                } else {
                                    Text("توليد أصوات اللاعبين الآن ⚡", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.players.forEach { player ->
                            val status = playerVoiceStatuses[player.name.trim()] ?: PlayerVoiceStatus()
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(androidx.compose.ui.graphics.Color(player.colorHex), androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = player.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))

                                    when (status.status) {
                                        VoiceStatusType.READY -> {
                                            Surface(
                                                color = androidx.compose.ui.graphics.Color(0xFFE8F5E9),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "جاهز ✓",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = androidx.compose.ui.graphics.Color(0xFF1B5E20),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        VoiceStatusType.GENERATING -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                        VoiceStatusType.FAILED -> {
                                            val reason = status.errorMessage ?: "خطأ في التوليد"
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.clickable {
                                                    selectedErrorDetail = player.name to reason
                                                }
                                            ) {
                                                Text(
                                                    text = "فشل: $reason",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.error,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        VoiceStatusType.NOT_GENERATED -> {
                                            Text(
                                                text = "لم يُولَّد",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.testAndPlayPlayerVoice(player.name) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Hearing,
                                            contentDescription = "استمع",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Header Info Bar (Round counter + Undo + End Game)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "الجولة ${uiState.currentRoundNumber}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = uiState.scoringRule.titleAr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Undo last round button
                    if (uiState.rounds.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.undoLastRound() },
                            modifier = Modifier.testTag("undo_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع عن الجولة الأخيرة")
                        }
                    }

                    // End game button
                    FilledTonalButton(
                        onClick = { showEndGameConfirmation = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("end_game_button")
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.end_game), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sticky Header: Cumulative Totals & Player Avatars
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScroll)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Row label column
                    Box(
                        modifier = Modifier.width(68.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "المجموع",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Players total columns
                    uiState.players.forEach { player ->
                        val isLeader = player.id == uiState.currentLeaderId && uiState.rounds.isNotEmpty()
                        Column(
                            modifier = Modifier
                                .width(columnWidth)
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                PlayerAvatarBadge(
                                    name = player.name,
                                    colorHex = player.colorHex,
                                    size = 40.dp
                                )
                                if (isLeader) {
                                    Text(
                                        text = "👑",
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(start = 22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = player.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "${player.totalScore}",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = if (isLeader) Color(0xFFC67C00) else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Rounds Table List
            if (uiState.rounds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ابدأ الجولة الأولى وسجّل نقاط اللاعبين",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    itemsIndexed(uiState.rounds) { _, round ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { editingRound = round },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(horizontalScroll)
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Round label
                                Box(
                                    modifier = Modifier.width(68.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "ج ${round.roundNumber}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Player Scores in this round
                                uiState.players.forEach { p ->
                                    val score = round.scores[p.id] ?: 0
                                    Box(
                                        modifier = Modifier.width(columnWidth),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$score",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = if (score == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Action: New Round Button
            Button(
                onClick = { showNewRoundDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .testTag("new_round_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.new_round),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // New Round Dialog
    if (showNewRoundDialog) {
        ScoreInputDialog(
            roundNumber = uiState.currentRoundNumber,
            players = uiState.players,
            initialScores = null,
            onDismiss = { showNewRoundDialog = false },
            onSaveScores = { scores ->
                viewModel.addRound(scores)
                showNewRoundDialog = false
            }
        )
    }

    // Edit Round Dialog
    editingRound?.let { roundToEdit ->
        ScoreInputDialog(
            roundNumber = roundToEdit.roundNumber,
            players = uiState.players,
            initialScores = roundToEdit.scores,
            onDismiss = { editingRound = null },
            onSaveScores = { updatedScores ->
                viewModel.editRound(roundToEdit.roundNumber, updatedScores)
                editingRound = null
            }
        )
    }

    // End Game Confirmation Dialog
    if (showEndGameConfirmation) {
        AlertDialog(
            onDismissRequest = { showEndGameConfirmation = false },
            title = { Text("إنهاء اللعبة وإعلان الفائز؟", fontWeight = FontWeight.Bold) },
            text = { Text("سيتم حساب النتائج النهائية، تتويج الفائز، وحفظ الجلسة في سجل الألعاب.") },
            confirmButton = {
                Button(
                    onClick = {
                        showEndGameConfirmation = false
                        viewModel.finishGame()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("نعم، إنهاء اللعبة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndGameConfirmation = false }) {
                    Text("متابعة اللعب")
                }
            }
        )
    }
}
