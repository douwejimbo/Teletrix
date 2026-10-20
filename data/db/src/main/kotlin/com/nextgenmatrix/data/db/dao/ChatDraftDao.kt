package com.nextgenmatrix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextgenmatrix.data.db.entity.ChatDraftEntity

@Dao
interface ChatDraftDao {
    @Query("SELECT * FROM chat_drafts WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: String): ChatDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatDraftEntity)

    @Query("DELETE FROM chat_drafts WHERE roomId = :roomId")
    suspend fun clear(roomId: String)
}
