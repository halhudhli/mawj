package com.nuvio.app.features.luxo

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Shows the system fingerprint / face / device-PIN prompt. True when the user unlocked. */
internal expect suspend fun luxoDeviceUnlock(title: String, subtitle: String): Boolean

/** True when the device has a fingerprint, face or screen lock that can unlock the app. */
internal expect fun luxoDeviceUnlockAvailable(): Boolean

/** Luxo: optional lock on opening the app (and after it has been in the background a while). */
internal object LuxoAppLock {
    private const val KEY = "app_lock_enabled"
    private const val RELOCK_AFTER_MS = 30_000L

    private val _enabled = MutableStateFlow(
        runCatching { LuxoPrefsStorage.load(KEY)?.toBooleanStrictOrNull() }.getOrNull() ?: false,
    )
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _locked = MutableStateFlow(_enabled.value)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAtMs: Long? = null

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        runCatching { LuxoPrefsStorage.save(KEY, value.toString()) }
        if (!value) _locked.value = false
    }

    fun unlocked() { _locked.value = false }

    fun onBackground() { backgroundedAtMs = Clock.System.now().toEpochMilliseconds() }

    fun onForeground() {
        val since = backgroundedAtMs ?: return
        backgroundedAtMs = null
        if (_enabled.value && Clock.System.now().toEpochMilliseconds() - since >= RELOCK_AFTER_MS) {
            _locked.value = true
        }
    }
}

@Composable
internal fun LuxoAppLockOverlay() {
    val locked by LuxoAppLock.locked.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> LuxoAppLock.onBackground()
                Lifecycle.Event.ON_START -> LuxoAppLock.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!locked) return

    val scope = rememberCoroutineScope()
    var prompting by remember { mutableStateOf(false) }
    val title = lx("افتح موج", "Unlock Mawj")
    val subtitle = lx("استخدم البصمة أو قفل الجهاز", "Use your fingerprint or device lock")
    val prompt: () -> Unit = {
        if (!prompting) {
            prompting = true
            scope.launch {
                val ok = runCatching { luxoDeviceUnlock(title, subtitle) }.getOrDefault(false)
                prompting = false
                if (ok) LuxoAppLock.unlocked()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!luxoDeviceUnlockAvailable()) LuxoAppLock.unlocked() else prompt()
    }
    val mawjLock = com.nuvio.app.core.ui.rememberMawjStageOn()
    if (mawjLock) {
        // «قفل التطبيق»: opaque night sea (the theme background is see-through under the sea), gold ring, serif title.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0xFF13283A), androidx.compose.ui.graphics.Color(0xFF070D14))))
                .background(androidx.compose.ui.graphics.Color(0xFF070D14).copy(alpha = 0f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRect(androidx.compose.ui.graphics.Color(0xFF070D14), alpha = 0.35f)
                val y = size.height - 80.dp.toPx()
                val path = androidx.compose.ui.graphics.Path()
                var x = 0f
                path.moveTo(0f, y)
                while (x <= size.width) { path.lineTo(x, y + kotlin.math.sin(x / size.width * 8f * kotlin.math.PI.toFloat()) * 12.dp.toPx()); x += 4f }
                drawPath(path, com.nuvio.app.core.ui.MawjTint.gold.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(32.dp)) {
                Box(
                    Modifier.size(104.dp).border(2.5.dp, com.nuvio.app.core.ui.MawjTint.gold, androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2A3C52), androidx.compose.ui.graphics.Color(0xFF0D1620))), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFF1DDA6), modifier = Modifier.size(52.dp)) }
                Text(lx("موج مقفول", "Mawj is locked"), color = androidx.compose.ui.graphics.Color(0xFFF6F1E4), fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp))
                Text(subtitle, color = androidx.compose.ui.graphics.Color(0xFFA9B6C4), textAlign = TextAlign.Center)
                Box(
                    Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(com.nuvio.app.core.ui.MawjTint.gold).clickable(onClick = prompt).padding(horizontal = 30.dp, vertical = 14.dp),
                ) { Text(lx("افتح بالبصمة", "Unlock"), color = androidx.compose.ui.graphics.Color(0xFF070D14), fontWeight = FontWeight.SemiBold) }
            }
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow touches so nothing underneath is reachable while locked.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = lx("موج مقفول", "Mawj is locked"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Button(onClick = prompt) { Text(lx("افتح بالبصمة", "Unlock")) }
        }
    }
}
