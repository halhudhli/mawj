package com.nuvio.app.features.luxo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.luxoAccentColors
import com.nuvio.app.core.ui.nuvio
import kotlin.math.PI
import kotlin.math.sin

/** Luxo assistant palette: night violet with a warm glow, independent of a white theme accent. */
internal val LuxoAssistantColors = listOf(Color(0xFF8B5CFF), Color(0xFFFF5FB0), Color(0xFFFFB86B))

/** A living orb: rotating gradient ring with a soft pulse — the companion's "face". */
@Composable
internal fun LuxoAssistantOrb(size: Int = 52, thinking: Boolean = false) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (thinking) 1600 else 6000, easing = LinearEasing)), label = "orb_spin")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "orb_pulse")
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp).graphicsLayer { scaleX = 0.92f + pulse * 0.08f; scaleY = 0.92f + pulse * 0.08f }) {
            drawCircle(Brush.radialGradient(listOf(LuxoAssistantColors[1].copy(alpha = 0.45f), Color.Transparent)), radius = this.size.minDimension / 2f)
        }
        Box(
            Modifier
                .size((size * 0.72f).dp)
                .rotate(spin)
                .clip(CircleShape)
                .background(Brush.sweepGradient(LuxoAssistantColors + LuxoAssistantColors.first())),
        )
        Box(
            Modifier.size((size * 0.56f).dp).clip(CircleShape).background(Color(0xFF14101F)),
            contentAlignment = Alignment.Center,
        ) { Text("✨", fontSize = (size * 0.26f).sp) }
    }
}

/** Sheet header: orb, title and subtitle over a soft aurora. */
@Composable
internal fun LuxoAssistantHeader(title: String, subtitle: String, thinking: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(LuxoAssistantColors[0].copy(alpha = 0.28f), LuxoAssistantColors[1].copy(alpha = 0.16f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(1200f, 300f),
                ),
            )
            .border(1.dp, Brush.linearGradient(LuxoAssistantColors.map { it.copy(alpha = 0.35f) }), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LuxoAssistantOrb(thinking = thinking)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

/** Horizontal row of suggestion chips. */
@Composable
internal fun LuxoSuggestionChips(items: List<Pair<String, () -> Unit>>, enabled: Boolean = true) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, onClick) ->
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Brush.linearGradient(LuxoAssistantColors.map { it.copy(alpha = 0.5f) }), RoundedCornerShape(50))
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** A chat bubble: the viewer's on the reading-start side in gradient, the companion's in glass. */
@Composable
internal fun LuxoChatBubble(text: String, fromUser: Boolean) {
    val shape = if (fromUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.Start else Arrangement.End) {
        Text(
            text,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .clip(shape)
                .background(
                    if (fromUser) {
                        Brush.linearGradient(listOf(LuxoAssistantColors[0], LuxoAssistantColors[1]))
                    } else {
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.05f)))
                    },
                )
                .then(if (fromUser) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Three bouncing dots while the companion thinks. */
@Composable
internal fun LuxoTypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "dots_phase")
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            val y = sin(((phase + i / 3f) % 1f) * 2f * PI.toFloat()) * 4f
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { translationY = -y * density }
                    .clip(CircleShape)
                    .background(LuxoAssistantColors[i]),
            )
        }
    }
}

/** The question bar: a glass pill with a gradient send button. Always visible. */
@Composable
internal fun LuxoAskBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    val canSend = enabled && value.isNotBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Brush.linearGradient(LuxoAssistantColors.map { it.copy(alpha = 0.45f) }), RoundedCornerShape(28.dp))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = Color.White.copy(alpha = 0.45f), fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(LuxoAssistantColors[1]),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) Brush.linearGradient(LuxoAssistantColors) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))))
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** Dark sheet background with the assistant's aurora. */
internal val LuxoAssistantSheetColor = Color(0xFF0F0C18)

@Composable
internal fun luxoAssistantAccent(): Color = luxoAccentColors().first().let { if (MaterialTheme.nuvio.colors.accent == it) LuxoAssistantColors[1] else it }

@Suppress("unused")
private val AssistantSpacer = Modifier.height(0.dp)
