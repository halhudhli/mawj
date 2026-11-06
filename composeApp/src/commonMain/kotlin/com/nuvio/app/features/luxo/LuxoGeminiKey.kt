package com.nuvio.app.features.luxo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.details.LuxoCompanionApi
import kotlinx.coroutines.launch

/** «مفتاح Gemini» in Settings → المزايا: add, test, replace or remove the key every AI feature uses. */
@Composable
fun LuxoGeminiKeyBlock() {
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(LuxoCompanionApi.loadKey()) }
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(saved == null) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🔑 مفتاح Gemini", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "تعتمد عليه: رفيق موج، ترجمة موج، البحث بالوصف وبالصورة، «أين قيلت هذه العبارة؟»، «من في المشهد؟»، " +
                "الموسيقى التصويرية، مواقع التصوير، «هل تعلم؟»، بين الرواية والشاشة، دليل الأهل، اختبار الحلقة، واقتراح المساء.",
            fontSize = 12.sp, color = Color.Gray,
        )
        val s = saved
        if (s != null && !editing) {
            Text("محفوظ: " + s.take(4) + "••••" + s.takeLast(4), fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = {
                    status = "يُختبر…"
                    scope.launch {
                        val r = LuxoCompanionApi.ask(s, "أجب بكلمة واحدة: تمام")
                        status = if (r.length < 40 && !r.startsWith("تعذّر")) "المفتاح يعمل ✓" else "لم ينجح الاختبار: ${r.take(120)}"
                    }
                }, label = { Text("اختبار") })
                FilterChip(selected = false, onClick = { editing = true; draft = ""; status = null }, label = { Text("تغيير") })
                FilterChip(selected = false, onClick = { LuxoCompanionApi.saveKey(""); saved = null; editing = true; status = "حُذف المفتاح" }, label = { Text("حذف") })
            }
        } else {
            Text("يُنشأ مجاناً من aistudio.google.com ← Get API key، ويُحفظ على هذا الجهاز فقط.", fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.trim() },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("الصق المفتاح هنا") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = draft.isNotBlank(), onClick = {
                    if (draft.isNotBlank()) { LuxoCompanionApi.saveKey(draft); saved = draft; editing = false; status = "حُفظ المفتاح ✓" }
                }, label = { Text("حفظ") })
                if (saved != null) FilterChip(selected = false, onClick = { editing = false }, label = { Text("إلغاء") })
            }
        }
        status?.let { Text(it, fontSize = 13.sp) }
    }
}
