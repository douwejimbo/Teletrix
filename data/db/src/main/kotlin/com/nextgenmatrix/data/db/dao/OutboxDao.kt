package com.nextgenmatrix.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextgenmatrix.data.db.entity.OutboxEntity

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox ORDER BY createdAtMs ASC")
    suspend fun pending(): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OutboxEntity)

    @Query("DELETE FROM outbox WHERE localId = :localId")
    suspend fun delete(localId: String)
}
