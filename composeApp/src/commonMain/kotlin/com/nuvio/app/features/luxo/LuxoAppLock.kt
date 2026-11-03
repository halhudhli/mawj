package com.nuvio.app.features.luxo

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
