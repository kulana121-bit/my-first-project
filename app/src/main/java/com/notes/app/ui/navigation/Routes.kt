package com.notes.app.ui.navigation

sealed class Route(val path: String) {
    data object Splash : Route("splash")
    data object Home : Route("home")
    data object Search : Route("search")
    data object Create : Route("create")
    data object Editor : Route("editor/{noteId}") {
        fun of(noteId: String) = "editor/$noteId"
    }
    data object HtmlReader : Route("reader/{noteId}") {
        fun of(noteId: String) = "reader/$noteId"
    }
    data object PdfReader : Route("pdf/{noteId}") {
        fun of(noteId: String) = "pdf/$noteId"
    }
    data object Library : Route("library")
    data object Folders : Route("folders")
    data object Trash : Route("trash")
    data object Export : Route("export/{noteId}") {
        fun of(noteId: String) = "export/$noteId"
    }
    data object Sync : Route("sync")
    data object Settings : Route("settings")
    data object About : Route("about")
    data object More : Route("more")
}
