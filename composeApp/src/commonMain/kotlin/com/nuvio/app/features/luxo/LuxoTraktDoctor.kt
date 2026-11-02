package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.trakt.TraktConfig

private data class DoctorLine(val ok: Boolean?, val title: String, val detail: String)

/**
 * «فاحص Trakt»: checks the built-in Trakt keys and the connection, and says in plain Arabic what is
 * wrong. Nothing secret is shown (only lengths and whether there are stray spaces).
 */
@Composable
fun LuxoTraktDoctorDialog(onDismiss: () -> Unit) {
    val lines = remember { mutableStateListOf<DoctorLine>() }
    var running by remember { mutableStateOf(true) }
    var run by remember { mutableStateOf(0) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(run) {
        lines.clear()
        running = true
        val id = TraktConfig.CLIENT_ID
        val secret = TraktConfig.CLIENT_SECRET
        lines += if (id.isBlank()) {
            DoctorLine(false, "مفتاح Client ID", "غير موجود في هذا الإصدار. يُضاف في GitHub ← Settings ← Secrets باسم TRAKT_CLIENT_ID ثم يُبنى إصدار جديد.")
        } else {
            DoctorLine(true, "مفتاح Client ID", "موجود (${id.length} حرف).")
        }
        lines += if (secret.isBlank()) {
            DoctorLine(false, "المفتاح السري Client Secret", "غير موجود. يُضاف باسم TRAKT_CLIENT_SECRET ثم يُبنى إصدار جديد.")
        } else {
            DoctorLine(true, "المفتاح السري Client Secret", "موجود (${secret.length} حرف).")
        }
        if (id.length != 64 && id.isNotBlank()) {
            lines += DoctorLine(false, "طول Client ID غير طبيعي", "مفاتيح Trakt طولها 64 حرف. غالباً انلصق ناقص أو معه كلام زايد.")
        }
        if (secret.length != 64 && secret.isNotBlank()) {
            lines += DoctorLine(false, "طول المفتاح السري غير طبيعي", "المفروض 64 حرف. غالباً انلصق ناقص أو انلصق مكانه Client ID.")
        }
        if (id.isNotBlank() && id == secret) {
            lines += DoctorLine(false, "المفتاحين متطابقين", "تم وضع نفس القيمة في الاثنين. المفتاح السري مختلف عن Client ID.")
        }
        if (id.isNotBlank()) {
            val r = runCatching {
                httpRequestRaw("GET", "https://api.trakt.tv/movies/trending?limit=1", mapOf("trakt-api-key" to id, "trakt-api-version" to "2", "Content-Type" to "application/json"), "")
            }.getOrNull()
            lines += when {
                r == null -> DoctorLine(false, "الاتصال بـ Trakt", "لا يوجد اتصال بالإنترنت أو Trakt لا يستجيب الآن.")
                r.status in 200..299 -> DoctorLine(true, "Client ID مقبول عند Trakt", "Trakt تعرّف على التطبيق.")
                r.status == 403 || r.status == 401 -> DoctorLine(false, "Client ID مرفوض", "Trakt رفض المفتاح (${r.status}). يُنسخ من جديد من trakt.tv/oauth/applications.")
                else -> DoctorLine(null, "رد غير متوقع", "رمز ${r.status}.")
            }
            val device = runCatching {
                httpRequestRaw("POST", "https://api.trakt.tv/oauth/device/code", mapOf("Content-Type" to "application/json"), "{\"client_id\":\"$id\"}")
            }.getOrNull()
            lines += when {
                device == null -> DoctorLine(null, "تسجيل الدخول بالرمز", "تعذّر الفحص.")
                device.status in 200..299 -> DoctorLine(true, "تسجيل الدخول بالرمز", "يعمل: Trakt يعطي رمز تفعيل للتطبيق.")
                else -> DoctorLine(false, "تسجيل الدخول بالرمز", "مرفوض (${device.status}). تأكد من تفعيل التطبيق في صفحة Trakt.")
            }
        }
        if (id.isNotBlank() && secret.isNotBlank()) {
            // A fake code: Trakt answers «invalid_grant» when the secret is right, «invalid_client» when it's wrong.
            val token = runCatching {
                httpRequestRaw(
                    "POST",
                    "https://api.trakt.tv/oauth/token",
                    mapOf("Content-Type" to "application/json"),
                    "{\"code\":\"luxo-check\",\"client_id\":\"$id\",\"client_secret\":\"$secret\",\"redirect_uri\":\"${TraktConfig.REDIRECT_URI}\",\"grant_type\":\"authorization_code\"}",
                )
            }.getOrNull()
            val body = token?.body.orEmpty()
            lines += when {
                token == null -> DoctorLine(null, "المفتاح السري", "تعذّر الفحص.")
                body.contains("invalid_client") -> DoctorLine(false, "المفتاح السري غير صحيح", "Trakt رفض المفتاح السري. يُنسخ Client Secret من جديد بدون مسافات.")
                body.contains("invalid_grant") || token.status == 400 -> DoctorLine(true, "المفتاح السري صحيح", "Trakt قبل المفتاح السري.")
                body.contains("redirect", ignoreCase = true) -> DoctorLine(false, "رابط الرجوع غير مسجل", "يُضاف ${TraktConfig.REDIRECT_URI} في صفحة التطبيق على Trakt.")
                else -> DoctorLine(null, "المفتاح السري", "رد غير واضح (${token.status}).")
            }
        }
        lines += DoctorLine(
            null,
            "رابط الرجوع (Redirect URI)",
            "في صفحة التطبيق على trakt.tv/oauth/applications لازم يكون مكتوب حرفياً سطرين:\n${TraktConfig.REDIRECT_URI}\nurn:ietf:wg:oauth:2.0:oob",
        )
        running = false
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF12141C))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(26.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("فاحص Trakt 🩺", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("يفحص مفاتيح Trakt المدمجة والاتصال. لا يعرض أي مفتاح.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            lines.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(when (line.ok) { true -> "✅"; false -> "❌"; null -> "ℹ️" }, fontSize = 18.sp)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text(line.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(line.detail, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.size(10.dp))
                    Text("يتم الفحص…", color = Color.White.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoctorButton("إعادة الفحص") { run++ }
                DoctorButton("نسخ النتيجة") {
                    clipboard.setText(AnnotatedString(lines.joinToString("\n") { (if (it.ok == true) "✅ " else if (it.ok == false) "❌ " else "ℹ️ ") + it.title + ": " + it.detail }))
                }
                DoctorButton("إغلاق", onDismiss)
            }
        }
    }
}

@Composable
private fun DoctorButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFF8C7BFF), Color(0xFFFF7EB6))))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}
