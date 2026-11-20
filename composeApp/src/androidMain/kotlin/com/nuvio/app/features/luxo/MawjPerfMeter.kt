package com.nuvio.app.features.luxo

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * «عدّاد الأداء»: frames drawn in the last second, how many were slow, and memory, in a corner.
 * It reads the frame timings Android already records (no extra drawing), so it doesn't slow the app.
 */
@Composable
fun MawjPerfMeter() {
    if (!rememberLuxoFeatures().perfMeter) return
    val activity = LocalContext.current as? Activity ?: return
    if (Build.VERSION.SDK_INT < 24) return
    val frames = remember { AtomicInteger(0) }
    val slow = remember { AtomicInteger(0) }
    val frozen = remember { AtomicInteger(0) }
    DisposableEffect(activity) {
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val ms = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000L
            frames.incrementAndGet()
            if (ms > 17) slow.incrementAndGet()
            if (ms > 700) frozen.incrementAndGet()
        }
        activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
        onDispose { runCatching { activity.window.removeOnFrameMetricsAvailableListener(listener) } }
    }
    var line by remember { mutableStateOf("…") }
    var color by remember { mutableStateOf(Color(0xFF5BBF7A)) }
    LaunchedEffect(activity) {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        val rt = Runtime.getRuntime()
        while (true) {
            delay(1_000L)
            val f = frames.getAndSet(0)
            val s = slow.getAndSet(0)
            val z = frozen.get()
            am.getMemoryInfo(info)
            val heapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val heapMax = rt.maxMemory() / (1024 * 1024)
            val nativeMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
            val freeMb = info.availMem / (1024 * 1024)
            val smooth = if (f == 0) "ساكن" else "$f إطار/ث · بطيء $s"
            line = "$smooth · متجمّد $z\nذاكرة التطبيق ${heapMb}/${heapMax} + ${nativeMb} م.ب · المتاح ${freeMb} م.ب" +
                if (info.lowMemory) " ⚠️" else ""
            color = when {
                info.lowMemory || (f > 0 && s * 3 > f) -> Color(0xFFE0574F)
                f > 0 && s * 8 > f -> Color(0xFFE3C34D)
                else -> Color(0xFF5BBF7A)
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopEnd) {
        Text(
            line,
            color = color,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xCC070D14)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
