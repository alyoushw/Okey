package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlayerResult

@Composable
fun PodiumView(
    results: List<PlayerResult>,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty()) return

    val first = results.getOrNull(0)
    val second = results.getOrNull(1)
    val third = results.getOrNull(2)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        // Second Place (silver)
        if (second != null) {
            PodiumStep(
                player = second,
                stepHeight = 90,
                podiumColor = Color(0xFFC0C0C0),
                rankLabel = "2 🥈",
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.width(8.dp))

        // First Place (Gold)
        if (first != null) {
            PodiumStep(
                player = first,
                stepHeight = 130,
                podiumColor = Color(0xFFFFD700),
                rankLabel = "1 👑 🥇",
                isFirst = true,
                modifier = Modifier.weight(1.2f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Third Place (Bronze)
        if (third != null) {
            PodiumStep(
                player = third,
                stepHeight = 65,
                podiumColor = Color(0xFFCD7F32),
                rankLabel = "3 🥉",
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PodiumStep(
    player: PlayerResult,
    stepHeight: Int,
    podiumColor: Color,
    rankLabel: String,
    isFirst: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlayerAvatarBadge(
            name = player.name,
            colorHex = player.colorHex,
            size = if (isFirst) 54.dp else 44.dp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = player.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isFirst) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${player.totalScore} نقطة",
            style = MaterialTheme.typography.labelLarge,
            color = if (isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stepHeight.dp)
                .shadow(4.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    podiumColor.copy(alpha = 0.9f),
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                )
                .padding(8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = rankLabel,
                fontWeight = FontWeight.Black,
                fontSize = if (isFirst) 18.sp else 15.sp,
                color = Color.Black.copy(alpha = 0.85f)
            )
        }
    }
}
