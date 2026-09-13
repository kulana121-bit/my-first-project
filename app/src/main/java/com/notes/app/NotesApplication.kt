package com.notes.app

import android.app.Application
import androidx.work.Configuration
import com.notes.app.data.db.NotesDatabase
import com.notes.app.data.repo.NotesRepository
import com.notes.app.data.storage.FileStorageManager
import com.notes.app.data.sync.DeviceSyncCoordinator

class NotesApplication : Application(), Configuration.Provider {
    lateinit var repository: NotesRepository
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    override fun onCreate() {
        super.onCreate()
        val db = NotesDatabase.get(this)
        val storage = FileStorageManager(this)
        repository = NotesRepository(
            context = this,
            database = db,
            storageManager = storage,
            syncCoordinator = DeviceSyncCoordinator(this, db, storage)
        )
    }
}
