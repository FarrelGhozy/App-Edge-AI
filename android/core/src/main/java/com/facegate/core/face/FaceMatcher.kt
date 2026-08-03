package com.facegate.core.face

import android.util.Log

/**
 * Optimized face matcher using cosine similarity with adaptive threshold.
 *
 * Key features:
 * - Adaptive threshold berdasarkan gap analysis (best vs second-best)
 * - Support multi-vector per student (different poses)
 * - Pre-normalized storage (dot product = cosine similarity)
 *
 * Adaptive threshold strategy:
 * - Gap > 0.15: CONFIDENT, threshold 0.70 (normal)
 * - Gap 0.08-0.15: MEDIUM, threshold 0.75 (perlu lebih yakin)
 * - Gap < 0.08: WEAK, threshold 0.85 (banyak yang mirip)
 * - Video mode: threshold bisa diturunkan 0.02 karena fusion sudah stabilkan noise
 */
class FaceMatcher(
    private val baseThreshold: Float = 0.70f,
    private val isVideoMode: Boolean = true
) : FaceIndex {

    companion object {
        private const val TAG = "FaceMatcher"
        private const val AMBIGUITY_RATIO = 0.15f
        private const val HIGH_GAP = 0.15f
        private const val MEDIUM_GAP = 0.08f
    }

    // Flat list of (studentId, normalizedVector) — one entry per pose vector.
    // CopyOnWriteArrayList: buildIndex() (from sync workers) mutates the list on
    // a background thread while VideoMatchEngine.match() reads it on
    // Dispatchers.Default — a plain MutableList causes ConcurrentModificationException
    // / half-built index reads. COW gives readers a consistent snapshot (issue #75).
    private val faceIndex = java.util.concurrent.CopyOnWriteArrayList<IndexEntry>()

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

        // Best & second-best must come from DIFFERENT students (issue #77).
        // The index holds multiple pose-vectors per student; if the runner-up is
        // another pose of the SAME student, the gap collapses and the adaptive
        // threshold rises → false reject. So: pick the best score per student,
        // then the best score among the remaining students.
        var bestId: String? = null
        var bestScore = -1f
        var secondId: String? = null
        var secondScore = -1f

        for (entry in faceIndex) {
            val sim = dotProduct(query, entry.vector)
            if (sim > bestScore) {
                bestScore = sim
                bestId = entry.studentId
            }
        }
        // Runner-up: best score from a DIFFERENT student.
        if (bestId != null) {
            for (entry in faceIndex) {
                if (entry.studentId == bestId) continue
                val sim = dotProduct(query, entry.vector)
                if (sim > secondScore) {
                    secondScore = sim
                    secondId = entry.studentId
                }
            }
        }

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000L

        // Gap analysis
        val gap = bestScore - secondScore
        val adjustedScore = if (gap < AMBIGUITY_RATIO && bestScore > 0) {
            bestScore - (AMBIGUITY_RATIO - gap) * 0.5f
        } else {
            bestScore
        }

        // Adaptive threshold
        val adaptiveThreshold = computeAdaptiveThreshold(gap)
        val isMatch = adjustedScore >= adaptiveThreshold

        // Decision level
        val decision = when {
            !isMatch -> MatchDecision.NO_MATCH
            gap > HIGH_GAP && adjustedScore >= baseThreshold + 0.05f -> MatchDecision.CONFIDENT
            gap > MEDIUM_GAP -> MatchDecision.MEDIUM
            else -> MatchDecision.WEAK
        }

        return MatchResult(
            studentId = bestId,
            confidence = adjustedScore,
            isMatch = isMatch,
            decision = decision,
            matchTimeMs = elapsedMs,
            secondBestId = secondId,
            secondBestConfidence = secondScore,
            gapScore = gap
        )
    }

    /**
     * Compute adaptive threshold based on gap between best and second-best.
     *
     * - Large gap (> 0.15): standard threshold (0.70)
     * - Medium gap (0.08-0.15): higher threshold (0.75)
     * - Small gap (< 0.08): strict threshold (0.85)
     * - Video mode: -0.02 because fusion reduces noise
     */
    private fun computeAdaptiveThreshold(gap: Float): Float {
        val rawThreshold = when {
            gap > HIGH_GAP -> baseThreshold
            gap > MEDIUM_GAP -> baseThreshold + 0.05f
            else -> baseThreshold + 0.15f
        }
        // Video fusion produces more stable embeddings → slightly lower threshold
        return if (isVideoMode) (rawThreshold - 0.02f).coerceAtLeast(0.60f) else rawThreshold
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

    fun getThreshold(): Float = baseThreshold
}
