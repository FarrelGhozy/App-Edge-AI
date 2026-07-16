package com.facegate.core.face

import android.util.Log

data class MatchResult(
    val studentId: String?,
    val confidence: Float,
    val isMatch: Boolean,
    val matchTimeMs: Long = 0L,
    val secondBestId: String? = null,
    val secondBestConfidence: Float = 0f
)

/**
 * Optimized face matcher using cosine similarity with multi-vector per student support.
 *
 * Each student can have multiple pose vectors (CENTER, LEFT, RIGHT, UP, DOWN).
 * Matching scans ALL vectors and returns the student with the best match across any pose.
 *
 * Optimizations:
 * 1. Pre-normalized storage — dot product = cosine similarity directly
 * 2. Second-best tracking — detects ambiguous matches
 */
class FaceMatcher(
    private val threshold: Float = 0.70f
) : FaceIndex {

    companion object {
        private const val TAG = "FaceMatcher"
        private const val AMBIGUITY_RATIO = 0.15f
    }

    // Flat list of (studentId, normalizedVector) — one entry per pose vector
    private val faceIndex = mutableListOf<IndexEntry>()

    override fun buildIndex(vectors: List<IndexEntry>) {
        faceIndex.clear()
        for (entry in vectors) {
            val normalized = if (entry.vector.isL2Normalized()) entry.vector
                             else normalize(entry.vector.clone())
            faceIndex.add(entry.copy(vector = normalized))
        }
        val studentCount = faceIndex.map { it.studentId }.distinct().size
        Log.d(TAG, "Index built: ${faceIndex.size} vectors for $studentCount students, dim=${vectors.firstOrNull()?.vector?.size ?: 0}")
    }

    override fun match(embedding: FloatArray): MatchResult {
        if (faceIndex.isEmpty()) {
            return MatchResult(null, 0f, false)
        }

        val startTime = System.nanoTime()
        val query = if (embedding.isL2Normalized()) embedding else normalize(embedding.clone())

        var bestId: String? = null
        var bestScore = -1f
        var secondId: String? = null
        var secondScore = -1f

        for (entry in faceIndex) {
            val sim = dotProduct(query, entry.vector)
            if (sim > bestScore) {
                secondScore = bestScore
                secondId = bestId
                bestScore = sim
                bestId = entry.studentId
            } else if (sim > secondScore) {
                secondScore = sim
                secondId = entry.studentId
            }
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000L

        // Ambiguity check
        val diff = bestScore - secondScore
        val adjustedScore = if (diff < AMBIGUITY_RATIO && bestScore > 0) {
            bestScore - (AMBIGUITY_RATIO - diff) * 0.5f
        } else {
            bestScore
        }

        return MatchResult(
            studentId = bestId,
            confidence = adjustedScore,
            isMatch = adjustedScore >= threshold,
            matchTimeMs = elapsedMs,
            secondBestId = secondId,
            secondBestConfidence = secondScore
        )
    }

    fun matchBatch(embeddings: List<FloatArray>): List<MatchResult> {
        return embeddings.map { match(it) }
    }

    override fun clear() {
        faceIndex.clear()
    }

    override fun size(): Int = faceIndex.size

    // ─── Old Map-based buildIndex removed — use List<IndexEntry> instead ───

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = kotlin.math.sqrt(norm)
        if (norm > 0) {
            for (i in v.indices) v[i] /= norm
        }
        return v
    }

    private fun FloatArray.isL2Normalized(): Boolean {
        var sqSum = 0f
        for (v in this) sqSum += v * v
        return kotlin.math.abs(sqSum - 1f) < 0.001f
    }

    fun getThreshold(): Float = threshold
}
