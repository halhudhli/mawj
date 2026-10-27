package com.nuvio.app.features.luxo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal val LuxoListEmojis = listOf("🎬", "🍿", "❤️", "👯‍♀️", "🌙", "😂", "😱", "🔥", "⭐", "📺", "🧠", "☕")

/** Luxo: «قيّمه» and «أضف لقائمة» on a title's page. */
@Composable
internal fun LuxoDetailsActions(title: LuxoSavedTitle, modifier: Modifier = Modifier) {
    val data by LuxoUserData.state.collectAsStateWithLifecycle()
    val rating = data.ratings[LuxoUserData.key(title.type, title.id)]
    val inLists = data.lists.count { list ->
        list.items.any { LuxoUserData.key(it.type, it.id) == LuxoUserData.key(title.type, title.id) }
    }
    var showRating by remember { mutableStateOf(false) }
    var showLists by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    val features = rememberLuxoFeatures()

    Column(
        modifier = modifier.navigationBarsPadding().padding(start = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallFloatingActionButton(onClick = { showLists = true }) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                if (inLists > 0) Text(LuxoDateStyle.digits(inLists.toString()))
            }
        }
        SmallFloatingActionButton(onClick = { showRating = true }) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (rating != null) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                )
                if (rating != null) Text(LuxoDateStyle.digits(luxoStarsLabel(rating.score)))
            }
        }
        if (features.shareCard) {
            SmallFloatingActionButton(onClick = { showShare = true }) {
                Icon(Icons.Rounded.Share, contentDescription = lx("مشاركة", "Share"))
            }
        }
    }
    if (showShare) LuxoShareCardDialog(title = title, onDismiss = { showShare = false })

    if (showRating) LuxoRatingSheet(title = title, existing = rating, onDismiss = { showRating = false })
    if (showLists) LuxoAddToListSheet(title = title, onDismiss = { showLists = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LuxoRatingSheet(title: LuxoSavedTitle, existing: LuxoRating?, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var score by remember { mutableIntStateOf(existing?.score ?: 0) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticsOn = rememberLuxoFeatures().haptics
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(lx("تقييمي", "My rating"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                for (star in 1..5) {
                    val full = score >= star * 2
                    val half = !full && score == star * 2 - 1
                    Icon(
                        imageVector = when {
                            full -> Icons.Rounded.Star
                            half -> Icons.AutoMirrored.Rounded.StarHalf
                            else -> Icons.Rounded.StarBorder
                        },
                        contentDescription = null,
                        tint = if (full || half) androidx.compose.ui.graphics.Color(0xFFFFC94D) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                // Tap once for a full star, again on the same star for a half star.
                                score = if (score == star * 2) star * 2 - 1 else star * 2
                                if (hapticsOn) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            },
                    )
                }
            }
            Text(
                text = if (score == 0) {
                    lx("الضغط على النجمة يعطي نجمة كاملة · ومرتين نص نجمة", "Tap the stars · twice for a half star")
                } else {
                    LuxoDateStyle.digits("${luxoStarsLabel(score)} / 5")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = { Text(lx("ملاحظتي (اختياري)", "My note (optional)")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (existing != null) {
                    TextButton(onClick = {
                        LuxoUserData.clearRating(title.type, title.id)
                        onDismiss()
                    }) { Text(lx("حذف التقييم", "Remove")) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = score > 0,
                    onClick = {
                        LuxoUserData.rate(title, score, note)
                        onDismiss()
                    },
                ) { Text(lx("حفظ", "Save")) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LuxoAddToListSheet(title: LuxoSavedTitle, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val data by LuxoUserData.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(data.lists.isEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(lx("أضف لقائمة", "Add to list"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(title.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            data.lists.forEach { list ->
                val checked = list.items.any { LuxoUserData.key(it.type, it.id) == LuxoUserData.key(title.type, title.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { LuxoUserData.toggleInList(list.id, title) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { LuxoUserData.toggleInList(list.id, title) })
                    Text("${list.emoji}  ${list.name}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        LuxoDateStyle.digits(list.items.size.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (creating) {
                LuxoListEditorFields(
                    initialName = "",
                    initialEmoji = LuxoListEmojis.first(),
                    confirmLabel = lx("إنشاء وإضافة", "Create & add"),
                    onConfirm = { name, emoji ->
                        val list = LuxoUserData.createList(name, emoji)
                        LuxoUserData.toggleInList(list.id, title)
                        creating = false
                    },
                    onCancel = if (data.lists.isNotEmpty()) ({ creating = false }) else null,
                )
            } else {
                TextButton(onClick = { creating = true }) { Text(lx("+ قائمة جديدة", "+ New list")) }
            }
        }
    }
}

/** Name + emoji fields used when creating or renaming a list. */
@Composable
internal fun LuxoListEditorFields(
    initialName: String,
    initialEmoji: String,
    confirmLabel: String,
    onConfirm: (name: String, emoji: String) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text(lx("اسم القائمة", "List name")) },
            placeholder = { Text(lx("مثلاً: أفلام الويكند", "e.g. Weekend movies")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LuxoListEmojis.forEach { option ->
                FilterChip(selected = emoji == option, onClick = { emoji = option }, label = { Text(option) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text(lx("إلغاء", "Cancel")) }
                Spacer(Modifier.width(8.dp))
            }
            Button(enabled = name.isNotBlank(), onClick = { onConfirm(name, emoji) }) { Text(confirmLabel) }
        }
    }
}
