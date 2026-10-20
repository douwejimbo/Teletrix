package com.nextgenmatrix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextgenmatrix.data.db.entity.ChatScrollStateEntity

@Dao
interface ChatScrollStateDao {
    @Query("SELECT * FROM chat_scroll_state WHERE roomId = :roomId LIMIT 1")
    suspend fun get(roomId: String): ChatScrollStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatScrollStateEntity)

    @Query("DELETE FROM chat_scroll_state WHERE roomId = :roomId")
    suspend fun clear(roomId: String)
}
