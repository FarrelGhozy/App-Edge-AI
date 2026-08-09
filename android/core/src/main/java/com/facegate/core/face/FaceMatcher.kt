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
        // Ambiguity penalty ratio (issue #66): 0.15 too aggressive for a large
        // multi-pose index (10k+ students) — genuine matches with a thin gap
        // were penalized into false-reject. Tuned down to 0.08 per improvement-plan.
        private const val AMBIGUITY_RATIO = 0.08f
        private const val HIGH_GAP = 0.15f
        private const val MEDIUM_GAP = 0.08f
    }

    // Immutable snapshot of (studentId, normalizedVector) — one entry per pose.
    // Written only by swapping a fresh immutable list (atomically, via @Volatile),
    // so readers on Dispatchers.Default always observe a complete, consistent
    // index. This is the correct fix for the buildIndex-vs-match race (#75): a
    // CopyOnWriteArrayList with clear()+add() is NOT atomic — two concurrent
    // builds could interleave and end with doubled entries.
    @Volatile
    private var faceIndex: List<IndexEntry> = emptyList()

    override fun buildIndex(vectors: List<IndexEntry>) {
        // #137: lewati vektor berdimensi salah (mis. 192-d era TFLite lama).
        // Query scan = 512-d; jika entry 192-d masuk index, dotProduct 512×192
        // → IndexOutOfBoundsException di SETIAP match → selalu "Wajah tidak dikenal".
        // Dimensi target = dimensi terbesar di store (campuran lama+baru → pakai baru).
        val targetDim = vectors.maxOfOrNull { it.vector.size } ?: 0
        val built = ArrayList<IndexEntry>(vectors.size)
        for (entry in vectors) {
            if (entry.vector.size != targetDim) {
                Log.w(TAG, "Skip vector student=${entry.studentId} dim=${entry.vector.size} (target $targetDim)")
                continue
            }
            val normalized = if (entry.vector.isL2Normalized()) entry.vector
                             else normalize(entry.vector.clone())
            built.add(entry.copy(vector = normalized))
        }
        // Atomic swap: readers see either the old complete snapshot or the new
        // complete one — never a half-built index.
        faceIndex = built
        val studentCount = built.map { it.studentId }.distinct().size
        Log.d(TAG, "Index built: ${built.size} vectors for $studentCount students, dim=$targetDim")
    }

    override fun clear() {
        faceIndex = emptyList()
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

    override fun size(): Int = faceIndex.size

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        // #137: defensive — dimensi beda (mis. 192-d lama vs 512-d baru) →
        // bukan match (skor 0), bukan crash.
        if (a.size != b.size) return 0f
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
