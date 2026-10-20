package com.nextgenmatrix.core.model

/** One SAS emoji: the glyph plus the short name shown under it. */
data class VerificationEmoji(
    val symbol: String,
    val description: String,
)

/**
 * Lightweight UI state for Matrix cross-device / session verification (emoji SAS).
 * Driven by [com.nextgenmatrix.matrix.MatrixRepository.sessionVerificationPrompt].
 */
data class SessionVerificationPrompt(
    val phase: SessionVerificationPhase,
    val emojis: List<VerificationEmoji> = emptyList(),
    val message: String? = null,
)

enum class SessionVerificationPhase {
    Idle,
    /** Waiting for the user to confirm on another device, or for SAS data. */
    Pending,
    SasCompare,
    Finished,
    Failed,
}
