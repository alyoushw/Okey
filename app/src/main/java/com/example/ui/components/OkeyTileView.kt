package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OkeyTileBone
import com.example.ui.theme.OkeyTileBorder

@Composable
fun OkeyTileBadge(
    number: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val cornerRadius = size * 0.18f
    Box(
        modifier = modifier
            .size(width = size * 0.85f, height = size)
            .shadow(3.dp, RoundedCornerShape(cornerRadius))
            .background(OkeyTileBone, RoundedCornerShape(cornerRadius))
            .border(1.5.dp, OkeyTileBorder, RoundedCornerShape(cornerRadius))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = number,
                color = accentColor,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.42f).sp,
                lineHeight = (size.value * 0.44f).sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Box(
                modifier = Modifier
                    .size(size * 0.16f)
                    .background(accentColor, CircleShape)
            )
        }
    }
}

@Composable
fun PlayerAvatarBadge(
    name: String,
    colorHex: Long,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val color = Color(colorHex)
    val initial = name.trim().take(1).ifEmpty { "ل" }
    Box(
        modifier = modifier
            .size(size)
            .shadow(2.dp, CircleShape)
            .background(color, CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp
        )
    }
}
