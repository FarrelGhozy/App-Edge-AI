package com.facegate.core.face

data class MatchResult(
    val studentId: String?,
    val confidence: Float,
    val isMatch: Boolean
)

class FaceMatcher(
    private val threshold: Float = 0.75f
) : FaceIndex {
    private val faceIndex = mutableMapOf<String, FloatArray>()

    override fun buildIndex(vectors: Map<String, FloatArray>) {
        faceIndex.clear()
        faceIndex.putAll(vectors)
    }

    override fun match(embedding: FloatArray): MatchResult {
        if (faceIndex.isEmpty()) {
            return MatchResult(null, 0f, false)
        }

        var bestId: String? = null
        var bestScore = -1f

        for ((studentId, vector) in faceIndex) {
            val similarity = cosineSimilarity(embedding, vector)
            if (similarity > bestScore) {
                bestScore = similarity
                bestId = studentId
            }
        }

        return MatchResult(
            studentId = bestId,
            confidence = bestScore,
            isMatch = bestScore >= threshold
        )
    }

    override fun clear() {
        faceIndex.clear()
    }

    override fun size(): Int = faceIndex.size

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom > 0) dotProduct / denom else 0f
    }
}
