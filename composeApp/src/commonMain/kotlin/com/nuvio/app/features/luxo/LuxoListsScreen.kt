package com.nuvio.app.features.luxo

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.features.home.MetaPreview

/** Luxo: «قوائمي» tab — the user's own named lists plus their rated titles. */
@Composable
internal fun LuxoListsScreen(
    modifier: Modifier = Modifier,
    onPosterClick: ((MetaPreview) -> Unit)? = null,
) {
    val data by LuxoUserData.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var openList by remember { mutableStateOf<String?>(null) }
    var editingList by remember { mutableStateOf<LuxoUserList?>(null) }
    var showRatings by remember { mutableStateOf(false) }
    val rated = remember(data.ratings) {
        data.ratings.values.sortedWith(compareByDescending<LuxoRating> { it.score }.thenByDescending { it.ratedAtMs })
    }

    NuvioScreen(
        modifier = modifier.fillMaxSize(),
        horizontalPadding = 0.dp,
        listState = rememberLazyListState(),
        autoHidesNativeTabBar = true,
    ) {
        item(key = "header") {
            NuvioScreenHeader(
                title = lx("قوائمي", "My lists"),
                includeStatusBarPadding = false,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "new") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (creating) {
                    LuxoListEditorFields(
                        initialName = "",
                        initialEmoji = LuxoListEmojis.first(),
                        confirmLabel = lx("إنشاء", "Create"),
                        onConfirm = { name, emoji ->
                            LuxoUserData.createList(name, emoji)
                            creating = false
                        },
                        onCancel = { creating = false },
                    )
                } else {
                    Button(onClick = { creating = true }) { Text(lx("+ قائمة جديدة", "+ New list")) }
                }
            }
        }
        if (data.lists.isEmpty() && rated.isEmpty() && !creating) {
            item(key = "empty") {
                Text(
                    text = lx(
                        "أنشئ قائمة باسم مثل «أفلام الويكند» أو «مع الأصدقاء»، والإضافة لها من صفحة أي فيلم أو مسلسل بزر القائمة 📋",
                        "Create a list like \"Weekend movies\", then add titles from any title page with the list button 📋",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                )
            }
        }
        items(data.lists, key = { it.id }) { list ->
            val index = data.lists.indexOf(list)
            LuxoListRow(
                title = "${list.emoji}  ${list.name}",
                count = list.items.size,
                items = list.items,
                onOpen = { openList = list.id },
                onPosterClick = onPosterClick,
                menu = { dismiss ->
                    DropdownMenuItem(text = { Text(lx("تعديل الاسم", "Rename")) }, onClick = { dismiss(); editingList = list })
                    if (index > 0) DropdownMenuItem(text = { Text(lx("تحريك لفوق", "Move up")) }, onClick = { dismiss(); LuxoUserData.moveList(list.id, up = true) })
                    if (index < data.lists.lastIndex) DropdownMenuItem(text = { Text(lx("تحريك لتحت", "Move down")) }, onClick = { dismiss(); LuxoUserData.moveList(list.id, up = false) })
                    DropdownMenuItem(text = { Text(lx("حذف القائمة", "Delete list")) }, onClick = { dismiss(); LuxoUserData.deleteList(list.id) })
                },
            )
        }
        if (rated.isNotEmpty()) {
            item(key = "rated") {
                LuxoListRow(
                    title = lx("⭐  الأعلى تقييماً من المشاهدات", "⭐  My top rated"),
                    count = rated.size,
                    items = rated.map { it.title },
                    badges = rated.associate { LuxoUserData.key(it.title.type, it.title.id) to luxoStarsLabel(it.score) },
                    onOpen = { showRatings = true },
                    onPosterClick = onPosterClick,
                    menu = null,
                )
            }
        }
    }

    openList?.let { listId ->
        val list = data.lists.firstOrNull { it.id == listId }
        if (list == null) {
            openList = null
        } else {
            LuxoTitlesSheet(
                title = "${list.emoji}  ${list.name}",
                items = list.items,
                onDismiss = { openList = null },
                onPosterClick = onPosterClick,
                onRemove = { item -> LuxoUserData.removeFromList(list.id, item.type, item.id) },
            )
        }
    }
    if (showRatings) {
        LuxoTitlesSheet(
            title = lx("⭐  الأعلى تقييماً من المشاهدات", "⭐  My top rated"),
            items = rated.map { it.title },
            notes = rated.associate { LuxoUserData.key(it.title.type, it.title.id) to "★ ${luxoStarsLabel(it.score)}" + (if (it.note.isNotBlank()) " · ${it.note}" else "") },
            onDismiss = { showRatings = false },
            onPosterClick = onPosterClick,
            onRemove = null,
        )
    }
    editingList?.let { list ->
        LuxoRenameListSheet(list = list, onDismiss = { editingList = null })
    }
}

@Composable
private fun LuxoListRow(
    title: String,
    count: Int,
    items: List<LuxoSavedTitle>,
    onOpen: () -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)?,
    badges: Map<String, String> = emptyMap(),
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = LuxoDateStyle.digits(count.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (menu != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        menu { menuOpen = false }
                    }
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                text = lx("فاضية · الإضافة من صفحة أي عمل", "Empty · add titles from any title page"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { LuxoUserData.key(it.type, it.id) }) { item ->
                    LuxoSavedPoster(
                        item = item,
                        badge = badges[LuxoUserData.key(item.type, item.id)],
                        modifier = Modifier.width(112.dp),
                        onClick = { onPosterClick?.invoke(item.toPreview()) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LuxoSavedPoster(
    item: LuxoSavedTitle,
    badge: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            if (item.poster.isNullOrBlank()) {
                Text(item.name, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp), maxLines = 4)
            } else {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (badge != null) {
                Text(
                    text = "★ ${LuxoDateStyle.digits(badge)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuxoTitlesSheet(
    title: String,
    items: List<LuxoSavedTitle>,
    onDismiss: () -> Unit,
    onPosterClick: ((MetaPreview) -> Unit)?,
    onRemove: ((LuxoSavedTitle) -> Unit)?,
    notes: Map<String, String> = emptyMap(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var removeTarget by remember { mutableStateOf<LuxoSavedTitle?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (onRemove != null) {
                Text(
                    lx("الضغط المطوّل على أي عمل يشيله من القائمة", "Long-press a title to remove it"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            removeTarget?.let { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lx("أشيل «${target.name}»؟", "Remove \"${target.name}\"?"), modifier = Modifier.weight(1f))
                    TextButton(onClick = { removeTarget = null }) { Text(lx("لا", "No")) }
                    TextButton(onClick = { onRemove?.invoke(target); removeTarget = null }) { Text(lx("شيله", "Remove")) }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(items, key = { LuxoUserData.key(it.type, it.id) }) { item ->
                    Column {
                        LuxoSavedPoster(
                            item = item,
                            badge = null,
                            onClick = {
                                onDismiss()
                                onPosterClick?.invoke(item.toPreview())
                            },
                            onLongClick = onRemove?.let { { removeTarget = item } },
                        )
                        notes[LuxoUserData.key(item.type, item.id)]?.let { note ->
                            Text(
                                LuxoDateStyle.digits(note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuxoRenameListSheet(list: LuxoUserList, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            LuxoListEditorFields(
                initialName = list.name,
                initialEmoji = list.emoji,
                confirmLabel = lx("حفظ", "Save"),
                onConfirm = { name, emoji ->
                    LuxoUserData.renameList(list.id, name, emoji)
                    onDismiss()
                },
                onCancel = onDismiss,
            )
        }
    }
}
