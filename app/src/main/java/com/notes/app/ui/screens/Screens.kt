package com.notes.app.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.notes.app.data.db.NoteEntity
import com.notes.app.data.model.NoteType
import com.notes.app.data.model.ThemeMode
import com.notes.app.ui.viewmodel.UiState
import java.io.File

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(900); onDone() }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("N O T E S", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Write Today, A Better Tomorrow", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun HomeScreen(
    state: UiState,
    onOpen: (NoteEntity) -> Unit,
    onSearch: () -> Unit,
    onFavorite: (String, Boolean) -> Unit,
    onTrash: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Good Morning", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, null) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("All", "HTML", "PDF", "Favorites")) { FilterChip(selected = false, onClick = {}, label = { Text(it) }) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.home.notes) { note ->
                NoteCard(note = note, onOpen = { onOpen(note) }, onFavorite = { onFavorite(note.id, !note.isFavorite) }, onDelete = { onTrash(note.id) })
            }
        }
    }
}

@Composable
fun SearchScreen(state: UiState, onQuery: (String) -> Unit, onOpen: (NoteEntity) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; onQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search notes and content") }
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.searchResults) { note ->
                NoteCard(note = note, onOpen = { onOpen(note) }, onFavorite = {}, onDelete = {})
            }
        }
    }
}

@Composable
fun CreateNoteScreen(onCreate: (NoteType, String) -> Unit, onImport: () -> Unit) {
    var title by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Create Note", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Title") })
        listOf(NoteType.HTML, NoteType.PDF, NoteType.TEXT).forEach { type ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onCreate(type, title) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(12.dp))
                    Column { Text("${type.name} Note", fontWeight = FontWeight.Medium); Text("Create and edit ${type.name.lowercase()} content") }
                }
            }
        }
        OutlinedButton(onClick = onImport) { Text("Import HTML/TXT/MD/PDF") }
    }
}

@Composable
fun HtmlEditorScreen(
    state: UiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onContent: (String) -> Unit,
    onTogglePreview: () -> Unit,
    onTag: (String, String) -> Unit,
    onExport: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            OutlinedTextField(value = state.editorTitle, onValueChange = onTitle, modifier = Modifier.weight(1f), singleLine = true)
            TextButton(onClick = onExport) { Text("Export") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            item { AssistChip(onClick = { onTag("<b>", "</b>") }, label = { Text("Bold") }) }
            item { AssistChip(onClick = { onTag("<i>", "</i>") }, label = { Text("Italic") }) }
            item { AssistChip(onClick = { onTag("<u>", "</u>") }, label = { Text("Underline") }) }
            item { AssistChip(onClick = { onTag("<h1>", "</h1>") }, label = { Text("H1") }) }
            item { AssistChip(onClick = { onTag("<ul><li>", "</li></ul>") }, label = { Text("List") }) }
            item { AssistChip(onClick = { onTag("<a href=''>", "</a>") }, label = { Text("Link") }) }
            item { AssistChip(onClick = { onTag("<img src='' alt='' />", "") }, label = { Text("Image") }) }
            item { AssistChip(onClick = { onTag("<table><tr><td>", "</td></tr></table>") }, label = { Text("Table") }) }
            item { AssistChip(onClick = { onTag("<pre><code>", "</code></pre>") }, label = { Text("Code") }) }
            item { AssistChip(onClick = { onTag("<span class='math'>", "</span>") }, label = { Text("Math") }) }
            item { AssistChip(onClick = onTogglePreview, label = { Text(if (state.editorPreview) "Edit" else "Preview") }) }
        }

        if (state.editorPreview) {
            HtmlPreview(html = state.editorContent, modifier = Modifier.fillMaxSize())
        } else {
            OutlinedTextField(
                value = state.editorContent,
                onValueChange = onContent,
                modifier = Modifier.fillMaxSize(),
                placeholder = { Text("Start writing your HTML...") }
            )
        }
    }
}

@Composable
fun HtmlReaderScreen(state: UiState, onBack: () -> Unit, onEdit: () -> Unit, onExport: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            Text(state.selectedNote?.title ?: "Reader", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null) }
            TextButton(onClick = onExport) { Text("Export") }
        }
        HtmlPreview(html = state.editorContent, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun PdfReaderScreen(
    state: UiState,
    onBack: () -> Unit,
    onBookmarkAdd: (Int) -> Unit,
    onBookmarkRemove: (Int) -> Unit,
    onExport: () -> Unit
) {
    val note = state.selectedNote
    var page by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            Text(note?.title ?: "PDF", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(onClick = { onBookmarkAdd(page) }) { Icon(Icons.Rounded.Bookmark, null) }
            IconButton(onClick = { onBookmarkRemove(page) }) { Icon(Icons.Rounded.Delete, null) }
            TextButton(onClick = onExport) { Text("Export") }
        }
        OutlinedTextField(value = search, onValueChange = {
            search = it
            page = it.toIntOrNull()?.coerceAtLeast(1)?.minus(1) ?: page
        }, label = { Text("Search page #") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))

        if (note?.filePath.isNullOrBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Missing PDF file") }
        } else {
            val render = remember(note?.filePath, page) {
                runCatching {
                    val file = File(note!!.filePath!!)
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val safePage = page.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0))
                    renderer.openPage(safePage).use { current ->
                        val bmp = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
                        current.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        Triple(bmp, safePage, renderer.pageCount)
                    }.also {
                        renderer.close()
                        pfd.close()
                    }
                }.getOrNull()
            }
            if (render == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Corrupted PDF or unreadable file") }
            } else {
                val (bmp, safePage, count) = render
                page = safePage
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items((0 until count).toList()) { idx ->
                        FilterChip(selected = idx == page, onClick = { page = idx }, label = { Text("${idx + 1}") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) { Text("Prev") }
                    Text("${page + 1} / $count", modifier = Modifier.align(Alignment.CenterVertically))
                    OutlinedButton(onClick = { page = (page + 1).coerceAtMost(count - 1) }, enabled = page < count - 1) { Text("Next") }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(state: UiState, onOpen: (NoteEntity) -> Unit, onMode: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FilterChip(selected = state.listMode == "list", onClick = { onMode("list") }, label = { Text("List") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = state.listMode == "grid", onClick = { onMode("grid") }, label = { Text("Grid") })
        }
        Spacer(Modifier.height(12.dp))
        if (state.listMode == "grid") {
            LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), contentPadding = PaddingValues(4.dp)) {
                items(state.home.notes) { note -> NoteGridCard(note = note, onOpen = { onOpen(note) }) }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.home.notes) { note -> NoteCard(note, onOpen = { onOpen(note) }, onFavorite = {}, onDelete = {}) }
            }
        }
    }
}

@Composable
fun FoldersScreen(state: UiState, onCreateFolder: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Folders", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.weight(1f), label = { Text("Folder") })
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onCreateFolder(name); name = "" }) { Text("Add") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(state.home.folders) { folder ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(folder.name)
                    }
                }
            }
        }
    }
}

@Composable
fun TrashScreen(
    trash: List<NoteEntity>,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onEmptyTrash: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trash", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onEmptyTrash) { Text("Empty Trash") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trash) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(note.title, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRestore(note.id) }) { Text("Restore") }
                        TextButton(onClick = { onDeleteForever(note.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportScreen(onBack: () -> Unit, onExportHtml: () -> Unit, onExportPdf: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
            Text("Export", style = MaterialTheme.typography.headlineSmall)
        }
        Card(Modifier.fillMaxWidth().clickable(onClick = onExportHtml)) {
            Text("Export HTML", modifier = Modifier.padding(16.dp))
        }
        Card(Modifier.fillMaxWidth().clickable(onClick = onExportPdf)) {
            Text("Export PDF", modifier = Modifier.padding(16.dp))
        }
        Card(Modifier.fillMaxWidth().clickable {
            onExportHtml(); onExportPdf()
        }) {
            Text("Export Both (HTML + PDF)", modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun SyncScreen(
    state: UiState,
    onAddFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onRemoveFolder: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sync Device Files", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto Sync", modifier = Modifier.weight(1f))
            Switch(checked = state.autoSync, onCheckedChange = onToggleAutoSync)
        }
        Text("Last Sync: ${state.lastSync ?: "Never"}")
        Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) { Text("Sync Now") }
        OutlinedButton(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) { Text("Add Folder") }
        state.syncReport?.let {
            Text("Imported ${it.imported}, Modified ${it.modified}, Removed ${it.removed}")
            if (it.errors.isNotEmpty()) Text("Errors: ${it.errors.joinToString()}")
        }
        Divider()
        Text("Selected folders are managed with SAF permissions.")
        LazyColumn {
            items(state.syncFolders) { folder ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(folder.displayName, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onRemoveFolder(folder.treeUri) }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(state: UiState, onTheme: (ThemeMode) -> Unit, onAutoSync: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK).forEach {
                FilterChip(selected = state.themeMode == it, onClick = { onTheme(it) }, label = { Text(it.name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto Sync", modifier = Modifier.weight(1f))
            Switch(checked = state.autoSync, onCheckedChange = onAutoSync)
        }
    }
}

@Composable
fun AboutScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("About", style = MaterialTheme.typography.headlineSmall)
        Text("NOTES v1.0.0")
        Text("Offline-first notes and document reader built with Kotlin + Jetpack Compose.")
    }
}

@Composable
fun MoreScreen(
    onFolders: () -> Unit,
    onTrash: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("More", style = MaterialTheme.typography.headlineSmall)
        listOf(
            "Folders" to onFolders,
            "Trash" to onTrash,
            "Sync" to onSync,
            "Settings" to onSettings,
            "About" to onAbout
        ).forEach { (label, action) ->
            Card(Modifier.fillMaxWidth().clickable(onClick = action), elevation = CardDefaults.cardElevation(2.dp)) {
                Text(label, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, onOpen: () -> Unit, onFavorite: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(note.title, fontWeight = FontWeight.Medium)
                Text(note.type.name, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (note.isFavorite) Icons.Rounded.Star else Icons.Rounded.Favorite, null)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null) }
        }
    }
}

@Composable
private fun NoteGridCard(note: NoteEntity, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.padding(6.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(14.dp).height(120.dp)) {
            Text(note.title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(note.type.name)
        }
    }
}

@Composable
private fun HtmlPreview(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> WebView(ctx).apply { settings.loadsImagesAutomatically = true } },
        update = { it.loadDataWithBaseURL(null, styledHtml(html), "text/html", "utf-8", null) }
    )
}

private fun styledHtml(raw: String): String = """
    <html><head><style>
    body { font-family: sans-serif; padding: 24px; line-height: 1.6; color: #16202A; }
    table, th, td { border:1px solid #ddd; border-collapse: collapse; padding: 8px; }
    pre { background:#F1F4F8; padding:12px; border-radius:12px; overflow:auto; }
    code { font-family: monospace; }
    .math { font-style: italic; color:#2F4C8D; }
    img { max-width:100%; border-radius:12px; }
    </style></head><body>$raw</body></html>
""".trimIndent()
