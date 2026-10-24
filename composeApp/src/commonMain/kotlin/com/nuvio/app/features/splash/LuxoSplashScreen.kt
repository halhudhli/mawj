package com.nuvio.app.features.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark
import org.jetbrains.compose.resources.painterResource

enum class SplashBackgroundStyle {
    STARS, LIGHT_WAVES, SOLID
}

@Composable
fun LuxoSplashScreen(
    backgroundStyle: SplashBackgroundStyle = SplashBackgroundStyle.STARS,
    backgroundColor: Color = Color.Black,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when (backgroundStyle) {
            SplashBackgroundStyle.STARS -> StarsBackground()
            SplashBackgroundStyle.LIGHT_WAVES -> LightWavesBackground()
            SplashBackgroundStyle.SOLID -> {} // Just solid color
        }

        // Logo
        Icon(
            painter = painterResource(Res.drawable.app_logo_wordmark),
            contentDescription = "Luxo",
            modifier = Modifier.size(280.dp),
            tint = Color(0xFFE8E8E8)
        )
    }
}

@Composable
private fun StarsBackground() {
    val infiniteTransition = rememberInfiniteTransition()
    val particleCount = 100

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(particleCount) { index ->
            val animatedAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000 + (index * 100), easing = LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )

            val xPos = remember(index) { (index * 37) % 100 }
            val yPos = remember(index) { ((index * 73) % 100) }

            Box(
                modifier = Modifier
                    .fillMaxSize(0.5f)
                    .align(Alignment.TopStart)
            ) {
                Box(
                    modifier = Modifier
                        .size(2.dp)
                        .background(
                            Color.White.copy(alpha = animatedAlpha),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .align(androidx.compose.ui.BiasAlignment(xPos / 50f - 1f, yPos / 50f - 1f))
                )
            }
        }
    }
}

@Composable
private fun LightWavesBackground() {
    val infiniteTransition = rememberInfiniteTransition()

    Box(modifier = Modifier.fillMaxSize()) {
        repeat(3) { waveIndex ->
            Box(
                modifier = Modifier
                    .size(300.dp + (waveIndex * 100.dp))
                    .align(Alignment.Center)
                    .background(Color.Transparent),
            )
        }
    }
}
