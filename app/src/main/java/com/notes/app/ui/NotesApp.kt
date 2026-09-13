package com.notes.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.notes.app.ui.navigation.Route
import com.notes.app.ui.screens.AboutScreen
import com.notes.app.ui.screens.CreateNoteScreen
import com.notes.app.ui.screens.ExportScreen
import com.notes.app.ui.screens.FoldersScreen
import com.notes.app.ui.screens.HomeScreen
import com.notes.app.ui.screens.HtmlEditorScreen
import com.notes.app.ui.screens.HtmlReaderScreen
import com.notes.app.ui.screens.LibraryScreen
import com.notes.app.ui.screens.MoreScreen
import com.notes.app.ui.screens.PdfReaderScreen
import com.notes.app.ui.screens.SearchScreen
import com.notes.app.ui.screens.SettingsScreen
import com.notes.app.ui.screens.SplashScreen
import com.notes.app.ui.screens.SyncScreen
import com.notes.app.ui.screens.TrashScreen
import com.notes.app.ui.viewmodel.NotesViewModel

@Composable
fun NotesApp(viewModel: NotesViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val state = viewModel.uiState
    val context = LocalContext.current
    val currentEntry = nav.currentBackStackEntryAsState().value

    LaunchedEffect(state.value.error, state.value.info) {
        state.value.error?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
        state.value.info?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast(':') ?: "imported_note"
            viewModel.importFile(uri, name) {
                nav.navigate(
                    if (it.type.name == "PDF") Route.PdfReader.of(it.id) else Route.Editor.of(it.id)
                )
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val name = uri.lastPathSegment?.substringAfterLast(':') ?: "Folder"
            viewModel.addSyncFolder(uri, name)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val route = currentEntry?.destination?.route.orEmpty()
            if (route !in listOf(Route.Splash.path)) {
                NavigationBar {
                    val items = listOf(
                        Route.Home.path to Pair("Home", Icons.Rounded.Home),
                        Route.Create.path to Pair("Create", Icons.Rounded.AddCircle),
                        Route.Library.path to Pair("Library", Icons.Rounded.LibraryBooks),
                        Route.More.path to Pair("More", Icons.Rounded.Menu)
                    )
                    items.forEach { (target, meta) ->
                        NavigationBarItem(
                            selected = currentEntry?.destination?.hierarchy?.any { it.route == target } == true,
                            onClick = {
                                nav.navigate(target) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(meta.second, contentDescription = meta.first) },
                            label = { androidx.compose.material3.Text(meta.first) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Route.Splash.path,
            modifier = Modifier.padding(padding)
        ) {
            composable(Route.Splash.path) {
                SplashScreen {
                    nav.navigate(Route.Home.path) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                    }
                }
            }
            composable(Route.Home.path) {
                HomeScreen(
                    state = state.value,
                    onOpen = { note ->
                        viewModel.loadNote(note.id)
                        nav.navigate(if (note.type.name == "PDF") Route.PdfReader.of(note.id) else Route.HtmlReader.of(note.id))
                    },
                    onSearch = { nav.navigate(Route.Search.path) },
                    onFavorite = { id, fav -> viewModel.toggleFavorite(id, fav) },
                    onTrash = { viewModel.moveToTrash(it) }
                )
            }
            composable(Route.Search.path) {
                SearchScreen(
                    state = state.value,
                    onQuery = viewModel::search,
                    onOpen = { note ->
                        viewModel.loadNote(note.id)
                        nav.navigate(if (note.type.name == "PDF") Route.PdfReader.of(note.id) else Route.HtmlReader.of(note.id))
                    }
                )
            }
            composable(Route.Create.path) {
                CreateNoteScreen(
                    onCreate = { type, title ->
                        viewModel.createNote(type, title) { note ->
                            viewModel.loadNote(note.id)
                            nav.navigate(if (type.name == "PDF") Route.PdfReader.of(note.id) else Route.Editor.of(note.id))
                        }
                    },
                    onImport = { importLauncher.launch(arrayOf("text/html", "text/plain", "application/pdf", "text/markdown")) }
                )
            }
            composable(Route.Editor.path) { backStack ->
                val noteId = backStack.arguments?.getString("noteId") ?: return@composable
                LaunchedEffect(noteId) { viewModel.loadNote(noteId) }
                HtmlEditorScreen(
                    state = state.value,
                    onBack = { nav.popBackStack() },
                    onTitle = viewModel::updateEditorTitle,
                    onContent = viewModel::updateEditorContent,
                    onTogglePreview = viewModel::togglePreview,
                    onTag = viewModel::applyTag,
                    onExport = { nav.navigate(Route.Export.of(noteId)) }
                )
            }
            composable(Route.HtmlReader.path) { backStack ->
                val noteId = backStack.arguments?.getString("noteId") ?: return@composable
                LaunchedEffect(noteId) { viewModel.loadNote(noteId) }
                HtmlReaderScreen(
                    state = state.value,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Route.Editor.of(noteId)) },
                    onExport = { nav.navigate(Route.Export.of(noteId)) }
                )
            }
            composable(Route.PdfReader.path) { backStack ->
                val noteId = backStack.arguments?.getString("noteId") ?: return@composable
                LaunchedEffect(noteId) { viewModel.loadNote(noteId) }
                PdfReaderScreen(
                    state = state.value,
                    onBack = { nav.popBackStack() },
                    onBookmarkAdd = viewModel::addPdfBookmark,
                    onBookmarkRemove = viewModel::removePdfBookmark,
                    onExport = { nav.navigate(Route.Export.of(noteId)) }
                )
            }
            composable(Route.Library.path) {
                LibraryScreen(
                    state = state.value,
                    onOpen = { note ->
                        viewModel.loadNote(note.id)
                        nav.navigate(if (note.type.name == "PDF") Route.PdfReader.of(note.id) else Route.HtmlReader.of(note.id))
                    },
                    onMode = viewModel::setLibraryMode
                )
            }
            composable(Route.Folders.path) {
                FoldersScreen(
                    state = state.value,
                    onCreateFolder = { name -> viewModel.addFolder(name) }
                )
            }
            composable(Route.Trash.path) {
                TrashScreen(
                    trash = state.value.trash,
                    onRestore = viewModel::restoreFromTrash,
                    onDeleteForever = viewModel::deletePermanently,
                    onEmptyTrash = viewModel::emptyTrash
                )
            }
            composable(Route.Export.path) {
                ExportScreen(
                    onBack = { nav.popBackStack() },
                    onExportHtml = {
                        viewModel.exportHtml {
                            context.startActivity(Intent.createChooser(it, "Share HTML"))
                        }
                    },
                    onExportPdf = {
                        viewModel.exportPdf {
                            context.startActivity(Intent.createChooser(it, "Share PDF"))
                        }
                    }
                )
            }
            composable(Route.Sync.path) {
                SyncScreen(
                    state = state.value,
                    onAddFolder = { folderPicker.launch(null) },
                    onSyncNow = viewModel::syncNow,
                    onToggleAutoSync = viewModel::setAutoSync,
                    onRemoveFolder = viewModel::removeSyncFolder
                )
            }
            composable(Route.Settings.path) {
                SettingsScreen(state = state.value, onTheme = viewModel::setTheme, onAutoSync = viewModel::setAutoSync)
            }
            composable(Route.About.path) {
                AboutScreen()
            }
            composable(Route.More.path) {
                MoreScreen(
                    onFolders = { nav.navigate(Route.Folders.path) },
                    onTrash = { nav.navigate(Route.Trash.path) },
                    onSync = { nav.navigate(Route.Sync.path) },
                    onSettings = { nav.navigate(Route.Settings.path) },
                    onAbout = { nav.navigate(Route.About.path) }
                )
            }
        }
    }
}
