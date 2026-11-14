package com.example.ui.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.model.ScoringRule
import kotlinx.coroutines.delay

val OkeyPlayerPalette = listOf(
    0xFFD32F2F, 0xFF1976D2, 0xFFF57C00, 0xFF2B2D42, 0xFF388E3C, 0xFF7B1FA2
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSetupScreen(
    recentPlayerNames: List<String>,
    onStartGame: (List<String>, List<Long>, ScoringRule) -> Unit,
    onPrepareNames: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var playerCount by rememberSaveable { mutableIntStateOf(4) }
    var scoringRule by rememberSaveable { mutableStateOf(ScoringRule.LOWEST_WINS) }
    var names by rememberSaveable {
        mutableStateOf(List(6) { recentPlayerNames.getOrNull(it).orEmpty() })
    }
    val activeNames = names.take(playerCount)
    LaunchedEffect(activeNames) {
        delay(900)
        onPrepareNames(activeNames.map(String::trim).filter(String::isNotEmpty).distinct())
    }
    Column(
        modifier = modifier.fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("لعبة جديدة", style = MaterialTheme.typography.headlineMedium)
        Text("عدد اللاعبين", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..6).forEach { count ->
                FilterChip(
                    selected = playerCount == count,
                    onClick = { playerCount = count },
                    label = { Text("$count") },
                    modifier = Modifier.testTag("player_count_$count")
                )
            }
        }
        activeNames.forEachIndexed { index, name ->
            OutlinedTextField(
                value = name,
                onValueChange = { value ->
                    names = names.toMutableList().also { it[index] = value.take(30) }
                },
                label = { Text("اسم اللاعب ${index + 1}") },
                leadingIcon = {
                    Box(Modifier.size(12.dp).background(Color(OkeyPlayerPalette[index]), CircleShape))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = if (index == playerCount - 1) ImeAction.Done else ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("player_name_$index")
            )
        }
        Text("الفائز", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoringRule.entries.forEach { rule ->
                FilterChip(
                    selected = scoringRule == rule,
                    onClick = { scoringRule = rule },
                    label = { Text(if (rule == ScoringRule.LOWEST_WINS) "الأقل نقاطًا" else "الأعلى نقاطًا") }
                )
            }
        }
        Button(
            onClick = {
                val players = activeNames.mapIndexed { i, value -> value.trim().ifBlank { "لاعب ${i + 1}" } }
                onStartGame(players, OkeyPlayerPalette.take(playerCount), scoringRule)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("start_game_button")
        ) { Text("ابدأ اللعبة") }
    }
}
