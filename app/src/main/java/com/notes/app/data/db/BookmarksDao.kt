package com.notes.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarksDao {
    @Query("SELECT * FROM pdf_bookmarks WHERE noteId = :noteId ORDER BY page ASC")
    fun observeForNote(noteId: String): Flow<List<PdfBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PdfBookmarkEntity)

    @Query("DELETE FROM pdf_bookmarks WHERE noteId = :noteId AND page = :page")
    suspend fun delete(noteId: String, page: Int)

    @Query("DELETE FROM pdf_bookmarks WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)
}
