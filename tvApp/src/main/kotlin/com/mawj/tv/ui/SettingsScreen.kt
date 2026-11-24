package com.mawj.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mawj.tv.BuildConfig
import com.mawj.tv.data.Addons
import com.mawj.tv.data.Store
import com.mawj.tv.link.PhoneLink
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val names = remember { mutableStateMapOf<String, String>() }
    val urls = Store.addonUrls.toList()
    LaunchedEffect(urls) { urls.forEach { u -> Addons.manifest(u)?.let { names[u] = it.name } } }
    Row(Modifier.fillMaxSize().padding(start = 24.dp, end = 32.dp, top = 36.dp)) {
        Column(Modifier.weight(1f)) {
            T("الإعدادات", 34.sp, weight = FontWeight.Bold)
            AccountCard()
            T("الإضافات المثبّتة، بترتيب ظهورها في الرئيسية", 15.sp, color = C.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(urls, key = { it }) { u ->
                    Row(
                        Modifier.fillMaxWidth().background(C.Raised, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(names[u] ?: "…", 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
                            T(u.substringAfter("://").substringBefore("/"), 12.sp, color = C.Dim, maxLines = 1)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("أعلى", false, onClick = { Store.moveAddon(u, up = true) })
                            Chip("أسفل", false, onClick = { Store.moveAddon(u, up = false) })
                            Chip("حذف", false, onClick = { Store.removeAddon(u) })
                        }
                    }
                }
                item {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("استعادة الإضافات الأساسية") { Store.DEFAULT_ADDONS.forEach(Store::addAddon) }
                    }
                }
                item { T("الإصدار ${BuildConfig.VERSION_NAME}", 13.sp, color = C.Dim, modifier = Modifier.padding(top = 18.dp)) }
            }
        }
        Spacer(Modifier.width(30.dp))
        PhoneLinkCard()
    }
}

@Composable
private fun PhoneLinkCard() {
    val address = remember { PhoneLink.address }
    Column(
        Modifier.width(300.dp).background(C.Raised, RoundedCornerShape(18.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("الربط بالجوال", 20.sp, weight = FontWeight.Bold)
        T("يُصوَّر الرمز بكاميرا الجوال على نفس شبكة المنزل، لإضافة الإضافات والبحث من الجوال.", 13.sp, color = C.Muted, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        if (address == null) {
            T("الشاشة غير متصلة بشبكة منزلية.", 14.sp, color = C.Red)
        } else {
            Box(Modifier.size(200.dp).background(Color.White, RoundedCornerShape(10.dp)).padding(10.dp)) { Qr(address) }
            T(address, 15.sp, color = C.GoldLight, modifier = Modifier.padding(top = 12.dp))
            T("رمز الربط مع ريموت التابلت: ${com.mawj.tv.link.RemoteLink.code}", 14.sp, color = C.Muted, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun Qr(text: String) {
    val matrix = remember(text) { runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0) }.getOrNull() } ?: return
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** The Nuvio account: who is signed in, refresh the addons from it, or sign out / in. */
@Composable
private fun AccountCard() {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var note by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).background(C.Raised, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("الحساب", 15.sp, color = C.Muted)
            T(com.mawj.tv.data.Account.email ?: "غير مسجَّل", 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
            note?.let { T(it, 13.sp, color = C.Green) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (com.mawj.tv.data.Account.signedIn) {
                Chip("تحديث الإضافات من الحساب", false, onClick = {
                    scope.launch { val n = com.mawj.tv.data.Account.pullAddons(); note = if (n > 0) "نُزّلت $n إضافة من الحساب" else "لم تُعثر على إضافات في الحساب" }
                })
                Chip("تسجيل الخروج", false, onClick = { com.mawj.tv.data.Account.signOut(); note = null })
            } else {
                Chip("تسجيل الدخول", false, onClick = { com.mawj.tv.Nav.go(com.mawj.tv.Route.Login) })
            }
        }
    }
}

/** When a tablet asks to pair, the 4-digit code shows here by itself (it closes once paired). */
@Composable
fun PairingOverlay() {
    val from = com.mawj.tv.link.RemoteLink.pairingFrom ?: return
    Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.background(Color(0xF20D1620), RoundedCornerShape(20.dp)).padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T("طلب ربط من «$from»", 16.sp, color = C.Muted)
            T("يُدخَل هذا الرمز في التابلت", 14.sp, color = C.Dim, modifier = Modifier.padding(bottom = 10.dp))
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.mawj.tv.link.RemoteLink.code.forEach { d ->
                        Box(Modifier.size(54.dp, 64.dp).background(C.Sea, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            T(d.toString(), 32.sp, color = C.GoldLight, weight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
