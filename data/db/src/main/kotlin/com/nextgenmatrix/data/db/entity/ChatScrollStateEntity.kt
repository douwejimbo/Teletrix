package com.nextgenmatrix.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_scroll_state")
data class ChatScrollStateEntity(
    @PrimaryKey val roomId: String,
    /** Event id of the message aligned to the top of the viewport when the user left the room. */
    val anchorEventId: String,
    val updatedAtMs: Long,
)
