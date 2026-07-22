package com.facegate.core.face

import android.util.Log

/**
 * Match decision levels sesuai planning.md:
 * CONFIDENT  → topScore ≥ 0.85 AND gap ≥ 0.05 → auto-accept
 * MEDIUM     → topScore ≥ 0.80 AND gap ≥ 0.03 → accept + flagged for review
 * WEAK       → topScore ≥ 0.70 → manual confirm (admin review)
 * NO_MATCH   → topScore < 0.70 → reject
 */
enum class MatchDecision {
    CONFIDENT,
    MEDIUM,
    WEAK,
    NO_MATCH
}

data class MatchResult(
    val studentId: String?,
    val confidence: Float,
    val isMatch: Boolean,
    val decision: MatchDecision = MatchDecision.NO_MATCH,
    val gap: Float = 0f,
    val topScore: Float = 0f,
    val runnerUpScore: Float = 0f,
    val matchTimeMs: Long = 0L,
    val secondBestId: String? = null,
    val secondBestConfidence: Float = 0f
)

/**
 * Adaptive face matcher using cosine similarity with Top-K ranking + gap analysis.
 * Sesuai planning.md section 18:
 * - Adaptive gap-based threshold (0.80-0.90)
 * - Top-K ranking + gap analysis
 *
 * Each student can have multiple pose vectors (CENTER, LEFT, RIGHT, UP, DOWN).
 * Matching scans ALL vectors and returns the student with the best match across any pose.
 */
class FaceMatcher(
    private val confidentThreshold: Float = 0.85f,
    private val mediumThreshold: Float = 0.80f,
    private val weakThreshold: Float = 0.70f,
    private val confidentGap: Float = 0.05f,
    private val mediumGap: Float = 0.03f,
    private val incrementalAlpha: Float = 0.05f
) : FaceIndex {

    companion object {
        private const val TAG = "FaceMatcher"
        private const val INCREMENTAL_MIN_CONFIDENCE = 0.90f
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
            return MatchResult(null, 0f, false, decision = MatchDecision.NO_MATCH)
        }

        val startTime = System.nanoTime()
        val query = if (embedding.isL2Normalized()) embedding else normalize(embedding.clone())

        // Track top-2 scores across ALL poses (not just per-student best)
        var bestId: String? = null
        var bestScore = -1f
        var secondId: String? = null
        var secondScore = -1f

        // Group scores per student for improved ambiguity detection
        val studentBestScores = mutableMapOf<String, Float>()

        for (entry in faceIndex) {
            val sim = dotProduct(query, entry.vector)
            // Track per-student best
            val prevBest = studentBestScores[entry.studentId] ?: -1f
            if (sim > prevBest) {
                studentBestScores[entry.studentId] = sim
            }
            // Track global top-2
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

        // Gunakan per-student best untuk gap analysis
        val sortedStudents = studentBestScores.entries.sortedByDescending { it.value }
        val topScore = sortedStudents.firstOrNull()?.value ?: 0f
        val runnerUpScore = sortedStudents.getOrNull(1)?.value ?: 0f
        val gap = topScore - runnerUpScore
        val runnerUpId = sortedStudents.getOrNull(1)?.key

        // Adaptive decision
        val decision = when {
            topScore >= confidentThreshold && gap >= confidentGap -> MatchDecision.CONFIDENT
            topScore >= mediumThreshold && gap >= mediumGap -> MatchDecision.MEDIUM
            topScore >= weakThreshold -> MatchDecision.WEAK
            else -> MatchDecision.NO_MATCH
        }

        val isMatch = decision != MatchDecision.NO_MATCH

        // Incremental learning: alpha=0.05 untuk high confidence scans (>= 0.90)
        if (isMatch && topScore >= INCREMENTAL_MIN_CONFIDENCE && bestId != null) {
            applyIncrementalLearning(bestId, query, topScore)
        }

        return MatchResult(
            studentId = sortedStudents.firstOrNull()?.key,
            confidence = topScore,
            isMatch = isMatch,
            decision = decision,
            gap = gap,
            topScore = topScore,
            runnerUpScore = runnerUpScore,
            matchTimeMs = elapsedMs,
            secondBestId = runnerUpId,
            secondBestConfidence = runnerUpScore
        )
    }

    /**
     * Incremental learning: blend existing vector dengan query baru (alpha=0.05)
     * untuk adaptasi perubahan penampilan (rambut, pencahayaan, dll).
     * Hanya untuk high confidence scans (>= 0.90).
     */
    private fun applyIncrementalLearning(studentId: String, query: FloatArray, confidence: Float) {
        val weight = incrementalAlpha * confidence // 0.05 * 0.90 = 0.045 effective
        for (i in faceIndex.indices) {
            if (faceIndex[i].studentId == studentId) {
                val vec = faceIndex[i].vector
                for (j in vec.indices) {
                    vec[j] = vec[j] * (1f - weight) + query[j] * weight
                }
                // Re-normalize
                var norm = 0f
                for (x in vec) norm += x * x
                norm = kotlin.math.sqrt(norm)
                if (norm > 0) {
                    for (j in vec.indices) vec[j] /= norm
                }
            }
        }
        Log.d(TAG, "Incremental learning applied for $studentId (conf=$confidence, alpha=$weight)")
    }

    fun matchBatch(embeddings: List<FloatArray>): List<MatchResult> {
        return embeddings.map { match(it) }
    }

    override fun clear() {
        faceIndex.clear()
    }

    override fun size(): Int = faceIndex.size

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

    fun getThresholds(): Triple<Float, Float, Float> = Triple(confidentThreshold, mediumThreshold, weakThreshold)
}
