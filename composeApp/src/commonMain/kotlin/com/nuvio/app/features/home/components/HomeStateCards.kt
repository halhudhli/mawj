package com.nuvio.app.features.home.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.core.ui.nuvio

@Composable
fun HomeEmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    scene: com.nuvio.app.features.luxo.MawjScene = com.nuvio.app.features.luxo.MawjScene.Boat,
) {
    NuvioSurfaceCard(modifier = modifier) {
        val mawj = com.nuvio.app.core.ui.rememberMawjStageOn()
        if (com.nuvio.app.features.luxo.rememberLuxoFeatures().sceneArt) {
            // A small still drawing: a boat for empty lists, an empty net for no results.
            com.nuvio.app.features.luxo.MawjSceneArt(scene, MaterialTheme.nuvio.colors.accent)
        } else if (mawj) {
            // Empty, offline and error states share Mawj's calm sea instead of a bare card.
            com.nuvio.app.core.ui.MawjWave(Modifier.padding(bottom = 4.dp), alpha = 0.7f)
            com.nuvio.app.core.ui.MawjWave(Modifier.padding(bottom = 12.dp), alpha = 0.3f)
        }
        Text(
            text = title,
            style = if (mawj) MaterialTheme.typography.titleLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontSize = 24.sp) else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            NuvioPrimaryButton(
                text = actionLabel,
                onClick = onActionClick,
            )
        }
    }
}
