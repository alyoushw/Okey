package com.example.ui.scoreboard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PlayerResult
import com.example.data.model.RoundEntry
import com.example.data.model.ScoringRule
import com.example.ui.components.ConfettiCelebration
import com.example.ui.components.PlayerAvatarBadge
import com.example.ui.components.PodiumView

@Composable
fun FinalResultScreen(
    results: List<PlayerResult>,
    rounds: List<RoundEntry>,
    scoringRule: ScoringRule,
    onNewGameSamePlayers: () -> Unit,
    onResetToNewGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val winners = results.filter { it.isWinner }
    val primaryWinner = winners.firstOrNull() ?: results.firstOrNull()

    Box(modifier = modifier.fillMaxSize()) {
        // Confetti in background
        ConfettiCelebration(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Trophy & Winner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFFB300),
                                    Color(0xFFF57C00)
                                )
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (winners.size > 1) "تعادل في المركز الأول! 🏆" else "بطل الجلسة! 🏆",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val winnerNames = winners.joinToString(" و ") { it.name }
                        Text(
                            text = winnerNames,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "بمجموع ${primaryWinner?.totalScore ?: 0} نقطة (${scoringRule.titleAr})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Podium for Top 3
            if (results.size >= 2) {
                Text(
                    text = "منصة التتويج",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                PodiumView(
                    results = results,
                    modifier = Modifier.widthIn(max = 500.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Full Ranking Table Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "الترتيب النهائي لجميع اللاعبين",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    results.forEachIndexed { index, res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (res.rank) {
                                        1 -> "🥇 #1"
                                        2 -> "🥈 #2"
                                        3 -> "🥉 #3"
                                        else -> "#${res.rank}"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(52.dp)
                                )
                                PlayerAvatarBadge(name = res.name, colorHex = res.colorHex, size = 36.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = res.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }

                            Text(
                                text = "${res.totalScore} نقطة",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (res.isWinner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (index < results.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Share Result Button
                FilledTonalButton(
                    onClick = {
                        shareGameResults(context, results, rounds.size, scoringRule)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("share_results_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share_result), fontWeight = FontWeight.Bold)
                }

                // New Game Same Players
                Button(
                    onClick = onNewGameSamePlayers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("new_game_same_players_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.new_game_same_players), fontWeight = FontWeight.Bold)
                }

                // Reset To New Game
                OutlinedButton(
                    onClick = onResetToNewGame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("new_game_fresh_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.new_game_fresh), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun shareGameResults(
    context: Context,
    results: List<PlayerResult>,
    roundsCount: Int,
    rule: ScoringRule
) {
    val winners = results.filter { it.isWinner }.joinToString(" و ") { it.name }
    val sb = StringBuilder()
    sb.append("🏆 نتيجة لعبة أوكي (رفيق أوكي) 🏆\n\n")
    sb.append("بطل الجلسة: $winners 🎉\n")
    sb.append("نظام الفوز: ${rule.titleAr}\n")
    sb.append("عدد الجولات: $roundsCount\n\n")
    sb.append("الترتيب النهائي:\n")
    results.forEach { res ->
        val medal = when (res.rank) {
            1 -> "🥇"
            2 -> "🥈"
            3 -> "🥉"
            else -> "▫️"
        }
        sb.append("$medal المركز ${res.rank}: ${res.name} (${res.totalScore} نقطة)\n")
    }
    sb.append("\nتم الحساب بواسطة تطبيق «رفيق أوكي»")

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "نتيجة لعبة أوكي")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة النتيجة عبر"))
}
