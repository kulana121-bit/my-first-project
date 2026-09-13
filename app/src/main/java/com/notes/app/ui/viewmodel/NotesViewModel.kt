package com.notes.app.ui.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notes.app.data.db.NoteEntity
import com.notes.app.data.db.SyncFolderEntity
import com.notes.app.data.model.NoteType
import com.notes.app.data.model.ThemeMode
import com.notes.app.data.repo.HomeState
import com.notes.app.data.repo.NotesRepository
import com.notes.app.data.sync.SyncReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val home: HomeState = HomeState(emptyList(), emptyList(), emptyList(), emptyList()),
    val trash: List<NoteEntity> = emptyList(),
    val searchResults: List<NoteEntity> = emptyList(),
    val selectedNote: NoteEntity? = null,
    val editorTitle: String = "",
    val editorContent: String = "",
    val editorPreview: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val listMode: String = "list",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoSync: Boolean = false,
    val lastSync: String? = null,
    val syncReport: SyncReport? = null
    ,
    val syncFolders: List<SyncFolderEntity> = emptyList()
)

class NotesViewModel(private val repository: NotesRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val mutableState = MutableStateFlow(UiState())

    val uiState: StateFlow<UiState> = combine(
        repository.homeState,
        repository.trashNotes,
        repository.themeMode,
        repository.autoSync,
        repository.listMode,
        repository.lastSync,
        repository.syncFolders,
        mutableState
    ) { home, trash, theme, autoSync, mode, lastSync, syncFolders, local ->
        local.copy(
            home = home,
            trash = trash,
            themeMode = theme,
            autoSync = autoSync,
            listMode = mode,
            lastSync = lastSync,
            syncFolders = syncFolders
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun createNote(type: NoteType, title: String, onCreated: (NoteEntity) -> Unit) = viewModelScope.launch {
        onCreated(repository.createNote(type, title))
    }

    fun importFile(uri: Uri, displayName: String, onDone: (NoteEntity) -> Unit) = viewModelScope.launch {
        runCatching { repository.importFile(uri, displayName) }
            .onSuccess(onDone)
            .onFailure { mutableState.value = mutableState.value.copy(error = it.message) }
    }

    fun loadNote(noteId: String) = viewModelScope.launch {
        val note = repository.getNote(noteId) ?: return@launch
        repository.openNote(noteId)
        val content = if (note.type == NoteType.PDF) "" else repository.readNoteContent(note)
        mutableState.value = mutableState.value.copy(
            selectedNote = note,
            editorTitle = note.title,
            editorContent = content,
            error = null
        )
    }

    fun updateEditorTitle(value: String) {
        mutableState.value = mutableState.value.copy(editorTitle = value)
    }

    fun updateEditorContent(value: String) {
        mutableState.value = mutableState.value.copy(editorContent = value)
        autoSave()
    }

    fun togglePreview() {
        mutableState.value = mutableState.value.copy(editorPreview = !mutableState.value.editorPreview)
    }

    fun applyTag(prefix: String, suffix: String = prefix) {
        val content = mutableState.value.editorContent
        mutableState.value = mutableState.value.copy(editorContent = "$content$prefix$suffix")
        autoSave()
    }

    fun autoSave() = viewModelScope.launch {
        val note = mutableState.value.selectedNote ?: return@launch
        repository.saveNoteContent(note.id, mutableState.value.editorTitle, mutableState.value.editorContent)
    }

    fun search(queryText: String) = viewModelScope.launch {
        query.value = queryText
        val results = repository.searchByQuery(queryText)
        mutableState.value = mutableState.value.copy(searchResults = results)
    }

    fun toggleFavorite(noteId: String, favorite: Boolean) = viewModelScope.launch {
        repository.toggleFavorite(noteId, favorite)
    }

    fun moveToTrash(noteId: String) = viewModelScope.launch {
        repository.moveToTrash(noteId)
    }

    fun restoreFromTrash(noteId: String) = viewModelScope.launch {
        repository.restoreFromTrash(noteId)
    }

    fun deletePermanently(noteId: String) = viewModelScope.launch {
        val result = repository.deletePermanently(noteId)
        mutableState.value = mutableState.value.copy(
            error = result.exceptionOrNull()?.message,
            info = if (result.isSuccess) "Deleted permanently" else null
        )
    }

    fun emptyTrash() = viewModelScope.launch {
        val result = repository.emptyTrash()
        mutableState.value = mutableState.value.copy(error = result.exceptionOrNull()?.message)
    }

    fun exportHtml(onReady: (Intent) -> Unit) = viewModelScope.launch {
        val note = mutableState.value.selectedNote ?: return@launch
        val file = repository.exportHtml(note)
        onReady(repository.buildShareIntent(file))
    }

    fun exportPdf(onReady: (Intent) -> Unit) = viewModelScope.launch {
        val note = mutableState.value.selectedNote ?: return@launch
        val file = repository.exportPdf(note)
        onReady(repository.buildShareIntent(file))
    }

    fun addPdfBookmark(page: Int) = viewModelScope.launch {
        val note = mutableState.value.selectedNote ?: return@launch
        repository.addPdfBookmark(note.id, page, "Page ${page + 1}")
    }

    fun removePdfBookmark(page: Int) = viewModelScope.launch {
        val note = mutableState.value.selectedNote ?: return@launch
        repository.removePdfBookmark(note.id, page)
    }

    fun setTheme(themeMode: ThemeMode) = viewModelScope.launch { repository.setTheme(themeMode) }
    fun setLibraryMode(mode: String) = viewModelScope.launch { repository.setLibraryMode(mode) }
    fun addFolder(name: String) = viewModelScope.launch { if (name.isNotBlank()) repository.addFolder(name) }
    fun setAutoSync(enabled: Boolean) = viewModelScope.launch { repository.setAutoSync(enabled) }

    fun addSyncFolder(uri: Uri, displayName: String) = viewModelScope.launch {
        repository.addSyncFolder(uri, displayName)
    }

    fun removeSyncFolder(treeUri: String) = viewModelScope.launch {
        repository.removeSyncFolder(treeUri)
    }

    fun syncNow() = viewModelScope.launch {
        val report = repository.syncNow()
        mutableState.value = mutableState.value.copy(syncReport = report)
    }

    fun clearMessage() {
        mutableState.value = mutableState.value.copy(error = null, info = null)
    }
}

class NotesViewModelFactory(private val repository: NotesRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(repository) as T
}
