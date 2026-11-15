package com.example.ui.scoreboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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


@Composable
fun ScoreboardScreen(
    viewModel: ScoreboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNewRoundDialog by remember { mutableStateOf(false) }
    var editingRound by remember { mutableStateOf<RoundEntry?>(null) }
    var showEndGameConfirmation by remember { mutableStateOf(false) }
    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("تعذّر الحفظ") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("حسنًا") } }
        )
    }

    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!uiState.hasActiveGame) {
        GameSetupScreen(
            recentPlayerNames = uiState.recentPlayerNames,
            onStartGame = { names, colors, rule ->
                viewModel.startNewGame(names, colors, rule)
            },
            onPrepareNames = { viewModel.preparePlayerNames(it) },
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

    val horizontalScroll = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val tableWidth = maxWidth.coerceAtMost(680.dp)
        val columnWidth = ((tableWidth - 84.dp) / uiState.players.size.coerceAtLeast(1)).coerceAtLeast(48.dp)
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Info Bar (Round counter + Undo + End Game)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                        enabled = !uiState.isSaving,
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
                enabled = !uiState.isSaving,
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
