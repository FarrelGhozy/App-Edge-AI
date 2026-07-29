package com.facegate.core.face

/**
 * Decision level based on confidence and gap analysis.
 * Mirip sistem grading biar matching lebih informatif.
 */
enum class MatchDecision {
    CONFIDENT,  // High confidence, large gap from 2nd best
    MEDIUM,     // OK confidence, moderate gap
    WEAK,       // Low confidence, small gap — risky
    NO_MATCH    // Below threshold
}

data class MatchResult(
    val studentId: String?,
    val confidence: Float,
    val isMatch: Boolean,
    val decision: MatchDecision = MatchDecision.NO_MATCH,
    val matchTimeMs: Long = 0L,
    val secondBestId: String? = null,
    val secondBestConfidence: Float = 0f,
    val gapScore: Float = 0f   // difference between best and second-best
)
