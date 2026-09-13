package com.notes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notes.app.ui.NotesApp
import com.notes.app.ui.theme.NotesTheme
import com.notes.app.ui.viewmodel.NotesViewModel
import com.notes.app.ui.viewmodel.NotesViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as NotesApplication).repository
        setContent {
            NotesTheme {
                val vm: NotesViewModel = viewModel(factory = NotesViewModelFactory(repository))
                NotesApp(viewModel = vm)
            }
        }
    }
}
