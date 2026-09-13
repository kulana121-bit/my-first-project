package com.notes.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        PdfBookmarkEntity::class,
        SyncFolderEntity::class,
        SyncIndexEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(com.notes.app.data.db.TypeConverters::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
    abstract fun foldersDao(): FoldersDao
    abstract fun bookmarksDao(): BookmarksDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile
        private var instance: NotesDatabase? = null

        fun get(context: Context): NotesDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context,
                NotesDatabase::class.java,
                "notes.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
