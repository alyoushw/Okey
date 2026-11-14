package com.example.ui.scoreboard

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Player
import com.example.ui.components.PlayerAvatarBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreInputDialog(
    roundNumber: Int,
    players: List<Player>,
    initialScores: Map<Int, Int>? = null,
    onDismiss: () -> Unit,
    onSaveScores: (Map<Int, Int>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Player ID -> string representation of score (e.g. "-10" or "25")
    val scoresMap = remember {
        mutableStateMapOf<Int, String>().apply {
            players.forEach { p ->
                val initVal = initialScores?.get(p.id)
                put(p.id, initVal?.toString() ?: "0")
            }
        }
    }

    var selectedPlayerIndex by remember { mutableIntStateOf(0) }
    val currentPlayer = players.getOrElse(selectedPlayerIndex) { players.first() }
    val currentInput = scoresMap[currentPlayer.id] ?: "0"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
                Text(
                    text = "نقاط الجولة $roundNumber",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = {
                        val parsed = players.associate { p ->
                            val text = scoresMap[p.id] ?: "0"
                            p.id to (text.toIntOrNull() ?: 0)
                        }
                        onSaveScores(parsed)
                    },
                    modifier = Modifier.testTag("save_round_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حفظ")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Player Selector Row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(players) { idx, p ->
                    val isSelected = idx == selectedPlayerIndex
                    val scoreText = scoresMap[p.id] ?: "0"

                    Card(
                        modifier = Modifier
                            .clickable { selectedPlayerIndex = idx }
                            .testTag("player_tab_${p.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = if (isSelected)
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else null
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PlayerAvatarBadge(name = p.name, colorHex = p.colorHex, size = 36.dp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = p.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                            Text(
                                text = scoreText,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current Player Value Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "نقاط ${currentPlayer.name}:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val totalWithRound = currentPlayer.totalScore + (currentInput.toIntOrNull() ?: 0)
                        Text(
                            text = "المجموع المتوقع: $totalWithRound",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = currentInput,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Keypad (1 to 9, +/-, 0, Backspace) + Next Player button
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("+/-", "0", "CLEAR")
                )

                for (row in keys) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when (key) {
                                            "CLEAR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                            "+/-" -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .clickable {
                                        handleKeypadPress(
                                            key = key,
                                            currentText = currentInput,
                                            onUpdate = { scoresMap[currentPlayer.id] = it }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "CLEAR") {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "حذف",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = key,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick presets or next player stepper
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick 0 button (very common for Okey winner of round)
                    OutlinedButton(
                        onClick = { scoresMap[currentPlayer.id] = "0" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("صفر", fontWeight = FontWeight.Bold)
                    }

                    // Next Player in list
                    Button(
                        onClick = {
                            if (selectedPlayerIndex < players.size - 1) {
                                selectedPlayerIndex++
                            } else {
                                selectedPlayerIndex = 0
                            }
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("اللاعب التالي", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun handleKeypadPress(key: String, currentText: String, onUpdate: (String) -> Unit) {
    when (key) {
        "+/-" -> {
            if (currentText.startsWith("-")) {
                onUpdate(currentText.removePrefix("-").ifEmpty { "0" })
            } else {
                if (currentText != "0") {
                    onUpdate("-$currentText")
                } else {
                    onUpdate("-")
                }
            }
        }
        "CLEAR" -> {
            if (currentText.length > 1) {
                val next = currentText.dropLast(1)
                onUpdate(if (next == "-") "0" else next)
            } else {
                onUpdate("0")
            }
        }
        else -> { // Numbers 0-9
            if (currentText == "0" || currentText.isEmpty()) {
                onUpdate(key)
            } else if (currentText == "-") {
                onUpdate("-$key")
            } else if (currentText.length < 5) {
                onUpdate(currentText + key)
            }
        }
    }
}
