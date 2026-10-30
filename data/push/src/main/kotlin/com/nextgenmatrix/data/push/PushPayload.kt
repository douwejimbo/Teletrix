package com.nextgenmatrix.data.push

import org.json.JSONObject

internal data class PushIds(val roomId: String, val eventId: String)

internal fun parsePushIds(bytes: ByteArray): PushIds? {
    val text = bytes.toString(Charsets.UTF_8).trim()
    if (text.isEmpty()) return null
    return parsePushJson(text) ?: runCatching {
        val wrapped = JSONObject(text).optString("message")
        if (wrapped.isNullOrBlank()) null else parsePushJson(wrapped)
    }.getOrNull()
}

private fun parsePushJson(text: String): PushIds? {
    val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
    val note = root.optJSONObject("notification") ?: root
    val room = note.optString("room_id").ifBlank { note.optString("roomId") }
    val event = note.optString("event_id").ifBlank { note.optString("eventId") }
    if (room.isBlank() || event.isBlank()) return null
    return PushIds(room, event)
}
