package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Harbor-style frosted rating chip on a poster's corner. Hidden when there's no usable rating. */
@Composable
internal fun LuxoGlassRatingBadge(
    ratingText: String?,
    modifier: Modifier = Modifier,
) {
    val rating = ratingText?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.48f), shape)
            .border(0.75.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = Color(0xFFF5C518),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = ((rating * 10).toInt() / 10.0).toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = Color.White,
        )
    }
}
