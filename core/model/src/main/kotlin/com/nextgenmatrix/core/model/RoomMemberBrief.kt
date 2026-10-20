package com.nextgenmatrix.core.model

/** Joined room member for @-mention autocomplete and display-name resolution. */
data class RoomMemberBrief(
    val userId: String,
    val displayName: String,
    /** Room-scoped avatar `mxc://` when the homeserver exposes it. */
    val avatarMxc: String? = null,
)

/** A person tagged in a draft: [label] is the visible "@name" text without the "@". */
data class MentionTag(
    val userId: String,
    val label: String,
)
