package com.nuvio.app.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.features.splash.MawjIdent
import com.nuvio.app.features.splash.MawjIdentPrefs
import com.nuvio.app.features.splash.MawjIdentStyle

/** «شاشة الدخول»: pick one of the three openers (or a random one each launch, or none), with a live preview. */
internal fun LazyListScope.luxoSplashSettingsContent(isTablet: Boolean) {
    item(key = "mawj_ident_settings") { MawjIdentSettingsBody() }
}

@Composable
private fun MawjIdentSettingsBody() {
    val choice = MawjIdentPrefs.choice
    var preview by remember { mutableStateOf<MawjIdentStyle?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تُختار افتتاحية التطبيق، ويظهر الاختيار من التشغيل التالي. والضغط على الافتتاحية يتخطاها.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        MawjIdentStyle.entries.forEach { s ->
            IdentCard(
                title = s.title,
                desc = s.inspired,
                selected = choice == s.name,
                onSelect = { MawjIdentPrefs.set(s.name) },
                onPreview = { preview = s },
            ) {
                // The chosen opener loops inside its card.
                var loop by remember { mutableIntStateOf(0) }
                LaunchedEffect(s) { while (true) { kotlinx.coroutines.delay(s.durationMs + 900L); loop++ } }
                MawjIdent(s, Modifier.fillMaxSize(), replayKey = loop)
            }
        }
        IdentCard("عشوائي 🎲", "تُختار إحدى الافتتاحيات عشوائياً عند كل تشغيل.", choice == MawjIdentPrefs.RANDOM, { MawjIdentPrefs.set(MawjIdentPrefs.RANDOM) }, null, null)
        IdentCard("بدون افتتاحية", "يُفتح التطبيق مباشرة.", choice == MawjIdentPrefs.OFF, { MawjIdentPrefs.set(MawjIdentPrefs.OFF) }, null, null)
    }
    preview?.let { s ->
        var key by remember(s) { mutableIntStateOf(0) }
        LaunchedEffect(s, key) {
            kotlinx.coroutines.delay(s.durationMs + 600L)
            preview = null
        }
        Dialog(onDismissRequest = { preview = null }, properties = com.nuvio.app.features.luxo.luxoFullScreenDialogProperties()) {
            Box(Modifier.fillMaxSize().clickable { preview = null }) { MawjIdent(s, Modifier.fillMaxSize(), replayKey = key) }
        }
    }
}

@Composable
private fun IdentCard(
    title: String,
    desc: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: (() -> Unit)?,
    thumb: (@Composable () -> Unit)?,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.10f else 0.04f))
            .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFF9DB8E0) else Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text((if (selected) "◉ " else "○ ") + title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (onPreview != null) {
                Text(
                    "▶ معاينة",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onPreview).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text(desc, fontSize = 13.sp, color = Color.White.copy(alpha = 0.62f), lineHeight = 20.sp)
        if (thumb != null && selected) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) { thumb() }
        }
    }
}
