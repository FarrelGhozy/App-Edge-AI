package com.facegate.core.face

interface FaceIndex {
    fun buildIndex(vectors: Map<String, FloatArray>)
    fun match(embedding: FloatArray): MatchResult
    fun clear()
    fun size(): Int
}
