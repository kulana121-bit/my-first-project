package com.notes.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_folders ORDER BY addedAt DESC")
    fun observeSyncFolders(): Flow<List<SyncFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(entity: SyncFolderEntity)

    @Query("DELETE FROM sync_folders WHERE treeUri = :treeUri")
    suspend fun removeFolder(treeUri: String)

    @Query("SELECT * FROM sync_index WHERE treeUri = :treeUri")
    suspend fun getIndex(treeUri: String): List<SyncIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIndex(entries: List<SyncIndexEntity>)

    @Query("DELETE FROM sync_index WHERE treeUri = :treeUri")
    suspend fun clearIndex(treeUri: String)
}
