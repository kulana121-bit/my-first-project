package com.notes.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun observeFavorites(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY openedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND (title LIKE '%' || :query || '%' OR id IN (:contentIds)) ORDER BY updatedAt DESC")
    fun searchNotes(query: String, contentIds: List<String>): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun clearTrashRows()

    @Query("UPDATE notes SET isTrashed = :trashed, trashedAt = :trashedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTrashState(id: String, trashed: Boolean, trashedAt: Long?, updatedAt: Long)

    @Query("UPDATE notes SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET openedAt = :openedAt WHERE id = :id")
    suspend fun updateOpenedAt(id: String, openedAt: Long)
}
