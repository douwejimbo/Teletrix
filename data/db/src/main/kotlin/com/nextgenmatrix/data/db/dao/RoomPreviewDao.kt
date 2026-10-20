package com.nextgenmatrix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextgenmatrix.data.db.entity.RoomPreviewEntity

@Dao
interface RoomPreviewDao {
    @Query("SELECT * FROM room_previews ORDER BY updatedAtMs DESC")
    suspend fun all(): List<RoomPreviewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoomPreviewEntity)
}
