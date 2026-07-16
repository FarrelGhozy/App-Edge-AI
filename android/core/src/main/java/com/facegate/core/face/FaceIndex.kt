package com.facegate.core.face

interface FaceIndex {
    fun buildIndex(vectors: List<IndexEntry>)
    fun match(embedding: FloatArray): MatchResult
    fun clear()
    fun size(): Int
}

data class IndexEntry(
    val studentId: String,
    val vector: FloatArray
)
