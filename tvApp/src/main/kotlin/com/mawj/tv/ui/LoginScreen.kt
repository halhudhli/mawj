package com.mawj.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mawj.tv.Nav
import com.mawj.tv.Route
import com.mawj.tv.data.Account
import com.mawj.tv.data.QrLogin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Mawj's sign-in page for the Nuvio account: a QR code for the phone, or email and password. */
@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<QrLogin?>(null) }
    var qrState by remember { mutableStateOf("جارٍ تجهيز الرمز…") }
    val emailFocus = remember { FocusRequester() }

    fun done() {
        Account.introSeen = true
        Nav.tab(Route.Home)
    }

    // QR: start a session, then ask every few seconds whether the phone approved it.
    LaunchedEffect(Unit) {
        while (true) {
            val q = Account.startQr()
            if (q == null) { qrState = "تعذّر تجهيز الرمز."; break }
            qr = q; qrState = "بانتظار التأكيد من الجوال…"
            var expired = false
            while (!expired) {
                delay(q.intervalSeconds.coerceAtLeast(2) * 1000L)
                when (Account.pollQr(q)) {
                    "approved" -> { done(); return@LaunchedEffect }
                    "expired" -> expired = true
                    else -> Unit
                }
            }
            qrState = "انتهت صلاحية الرمز، جارٍ تجديده…"
        }
    }
    LaunchedEffect(Unit) { delay(200); runCatching { emailFocus.requestFocus() } }

    Box(
        // A plain page: one colour, nothing drawn behind (light on the TV).
        Modifier.fillMaxSize().background(C.Sea),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(70.dp), verticalAlignment = Alignment.CenterVertically) {
            // The card (on the right in Arabic).
            Column(
                Modifier.width(470.dp).background(Color(0xEB0D1620), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0x4DD6B36A), RoundedCornerShape(24.dp)).padding(26.dp),
            ) {
                T("تسجيل الدخول", 22.sp, weight = FontWeight.Bold)
                T("بحساب نوفيو نفسه: الإيميل وكلمة السر.", 14.sp, color = C.Muted, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(150.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
                        qr?.let { QrCode(it.url) }
                    }
                    Column(Modifier.weight(1f)) {
                        T("يُصوَّر الرمز بكاميرا الجوال، ويُسجَّل الدخول من الجوال بدل الكتابة بالريموت.", 14.sp, color = C.Muted, lineHeight = 22.sp)
                        T(qrState, 13.sp, color = C.Dim, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(C.Line))
                    T("أو", 13.sp, color = C.Dim, modifier = Modifier.padding(horizontal = 10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(C.Line))
                }
                Field(email, { email = it }, "الإيميل", KeyboardType.Email, password = false, modifier = Modifier.focusRequester(emailFocus))
                Spacer(Modifier.height(10.dp))
                Field(password, { password = it }, "كلمة السر", KeyboardType.Password, password = true)
                Spacer(Modifier.height(14.dp))
                Pill(if (busy) "جارٍ الدخول…" else "دخول", primary = true, modifier = Modifier.fillMaxWidth()) {
                    if (busy || email.isBlank() || password.isBlank()) return@Pill
                    busy = true; message = null
                    scope.launch {
                        message = Account.signIn(email, password)
                        busy = false
                        if (message == null) done()
                    }
                }
                message?.let { T(it, 14.sp, color = C.Red, modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(8.dp))
                Pill("المتابعة بدون حساب", modifier = Modifier.fillMaxWidth()) { done() }
            }
            // The brand.
            Column(Modifier.width(400.dp)) {
                Box(Modifier.size(92.dp).background(C.Gold, CircleShape))
                Spacer(Modifier.height(18.dp))
                T("موج", 64.sp, color = C.GoldLight, weight = FontWeight.Bold)
                T("تُنزَّل الإضافات من الحساب تلقائياً بعد الدخول.", 18.sp, color = C.Muted, lineHeight = 30.sp)
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType, password: Boolean, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier.fillMaxWidth().height(52.dp)
                .background(C.Sea, RoundedCornerShape(14.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) C.GoldLight else Color(0xFF243243), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) T(hint, 17.sp, color = C.Dim)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.Ink, fontSize = 17.sp),
                cursorBrush = SolidColor(C.Gold),
                keyboardOptions = KeyboardOptions(keyboardType = type),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
private fun QrCode(text: String) {
    val matrix = remember(text) { runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0) }.getOrNull() } ?: return
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
