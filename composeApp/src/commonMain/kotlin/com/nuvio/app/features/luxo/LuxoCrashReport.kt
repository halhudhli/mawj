package com.nuvio.app.features.luxo

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** After a crash: shows what happened once, with a copy button so it can be sent for a fix. */
@Composable
fun LuxoCrashReportDialog() {
    var report by remember { mutableStateOf(LuxoPrefsStorage.load("last_crash")?.takeIf { it.isNotBlank() }) }
    val clipboard = LocalClipboardManager.current
    val text = report ?: return
    fun close() {
        LuxoPrefsStorage.save("last_crash", "")
        report = null
    }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("صار خطأ وانقفل التطبيق") },
        text = {
            Text(
                "انسخ التفاصيل وأرسلها عشان ينصلح:\n\n$text",
                fontSize = 11.sp,
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                close()
            }) { Text("نسخ وإغلاق") }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("إغلاق") } },
    )
}
