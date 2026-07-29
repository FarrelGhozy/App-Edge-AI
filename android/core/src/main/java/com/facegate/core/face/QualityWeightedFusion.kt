package com.facegate.core.face

import android.util.Log
import kotlin.math.sqrt

/**
 * Quality-weighted fusion engine for video-based face recognition.
 *
 * Given N frame entries, selects K best-quality frames and computes
 * a quality-weighted average embedding, then L2-normalizes the result.
 */
class QualityWeightedFusion {

    companion object {
        private const val TAG = "Fusion"

        /** Minimum quality score to consider a frame usable. [0..1] */
        const val MIN_QUALITY = 0.4f

        /** Number of top-quality frames to keep for fusion. */
        const val K_BEST = 5

        /** Minimum frames required to produce a fused embedding. */
        const val MIN_FRAMES = 2

        /**
         * Compute a composite quality score from face detection + image quality.
         *
         * Factors (weighted heuristic):
         * - Face size ratio (bigger = better): 40%
         * - Centeredness (closer to center = better): 30%
         * - Yaw angle (less yaw = better): 30%
         */
        fun computeQualityScore(entry: VideoFrameBuffer.FrameEntry): Float {
            val rect = entry.faceRect ?: return entry.qualityScore

            val bmp = entry.bitmap
            val imageArea = (bmp.width * bmp.height).toFloat()

            // Face size score: bigger face = better quality
            val faceArea = (rect.width() * rect.height()).toFloat()
            val sizeScore = minOf(faceArea / (imageArea * 0.3f), 1.0f) // 30% of frame is ideal

            // Centeredness score
            val cx = rect.exactCenterX() / bmp.width
            val cy = rect.exactCenterY() / bmp.height
            val centerDist = sqrt((cx - 0.5f) * (cx - 0.5f) * 4 + (cy - 0.5f) * (cy - 0.5f) * 4)
            val centerScore = (1.0f - centerDist).coerceIn(0f, 1f)

            // Composite: 40% size, 30% center
            val composite = sizeScore * 0.4f + centerScore * 0.3f + entry.qualityScore * 0.3f
            return composite.coerceIn(0f, 1f)
        }
    }

    /**
     * Fuse multiple frame embeddings into a single robust embedding.
     *
     * Steps:
     * 1. Filter: remove frames below MIN_QUALITY
     * 2. K-best: keep the top K frames by quality
     * 3. Weighted average: sum(embedding × quality) / sum(quality)
     * 4. L2 normalize the result
     *
     * @param entries list of frame entries (must have embeddings computed)
     * @return fused embedding, or null if insufficient frames
     */
    fun fuse(entries: List<VideoFrameBuffer.FrameEntry>): FloatArray? {
        if (entries.isEmpty()) {
            Log.w(TAG, "Fusion: empty entries")
            return null
        }

        // 1. Assign quality scores + filter
        val scored = entries.map { entry ->
            val qs = computeQualityScore(entry)
            entry to qs
        }.filter { (_, qs) ->
            qs >= MIN_QUALITY && it.first.embedding != null
        }

        if (scored.size < MIN_FRAMES) {
            Log.w(TAG, "Fusion: insufficient frames after quality filter (${scored.size} < $MIN_FRAMES)")
            // Fallback: use whatever we have even if below MIN_QUALITY
            val fallback = entries.filter { it.embedding != null }
            if (fallback.size < MIN_FRAMES) return null
            return simpleAverage(fallback.map { it.embedding!! })
        }

        // 2. K-best selection
        val best = scored.sortedByDescending { (_, qs) -> qs }.take(K_BEST)

        Log.d(TAG, "Fusion: ${best.size} frames selected from ${entries.size} collected " +
                "(best qs=${"%.3f".format(best.first().second)}, " +
                "worst qs=${"%.3f".format(best.last().second)})")

        // 3. Quality-weighted average
        val dim = best.first().first.embedding!!.size
        val weighted = FloatArray(dim)
        var totalWeight = 0f

        for ((entry, qs) in best) {
            val emb = entry.embedding!!
            for (i in 0 until dim) {
                weighted[i] += emb[i] * qs
            }
            totalWeight += qs
        }

        if (totalWeight <= 0f) {
            return simpleAverage(best.map { it.first.embedding!! })
        }

        for (i in 0 until dim) {
            weighted[i] /= totalWeight
        }

        // 4. L2 normalize
        return l2Normalize(weighted)
    }

    /**
     * Simple equal-weight average fallback.
     */
    private fun simpleAverage(embeddings: List<FloatArray>): FloatArray {
        val dim = embeddings.first().size
        val result = FloatArray(dim)
        for (emb in embeddings) {
            for (i in 0 until dim) {
                result[i] += emb[i]
            }
        }
        val n = embeddings.size.toFloat()
        for (i in 0 until dim) {
            result[i] /= n
        }
        return l2Normalize(result)
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm > 0) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
        return vector
    }
}
