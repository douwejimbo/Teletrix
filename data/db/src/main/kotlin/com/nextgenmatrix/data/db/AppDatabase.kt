package com.nextgenmatrix.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nextgenmatrix.data.db.dao.ChatDraftDao
import com.nextgenmatrix.data.db.dao.ChatScrollStateDao
import com.nextgenmatrix.data.db.dao.OutboxDao
import com.nextgenmatrix.data.db.dao.RoomPreviewDao
import com.nextgenmatrix.data.db.entity.ChatDraftEntity
import com.nextgenmatrix.data.db.entity.ChatScrollStateEntity
import com.nextgenmatrix.data.db.entity.OutboxEntity
import com.nextgenmatrix.data.db.entity.RoomPreviewEntity

@Database(
    entities = [
        RoomPreviewEntity::class,
        ChatDraftEntity::class,
        OutboxEntity::class,
        ChatScrollStateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomPreviewDao(): RoomPreviewDao
    abstract fun chatDraftDao(): ChatDraftDao
    abstract fun chatScrollStateDao(): ChatScrollStateDao
    abstract fun outboxDao(): OutboxDao
}
