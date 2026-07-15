package com.facegate.core.face

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaceEmbedderTest {

    private lateinit var embedder: FaceEmbedder

    private fun vec192(value: Float = 0.1f): FloatArray = FloatArray(192) { value }

    @Before
    fun setup() {
        val mockContext = mockk<Context>(relaxed = true)
        embedder = FaceEmbedder(mockContext)
    }

    @Test
    fun `initial state should not be ready`() {
        assertFalse(embedder.isReady())
    }

    @Test
    fun `default embedding dimension should be 192`() {
        assertEquals(192, embedder.getEmbeddingDim())
    }

    @Test
    fun `averageEmbeddings with empty array should return array of dim size`() {
        val result = embedder.averageEmbeddings(emptyArray())
        assertEquals(192, result.size)
    }

    @Test
    fun `averageEmbeddings with single vector should normalize correctly`() {
        val vec = vec192(0.5f)
        val result = embedder.averageEmbeddings(arrayOf(vec))
        assertEquals(vec.size, result.size)
        // L2 norm should be ~1.0
        var sumSq = 0f
        for (v in result) sumSq += v * v
        assertTrue("L2 norm should be ~1.0", kotlin.math.abs(sumSq - 1f) < 0.01f)
    }

    @Test
    fun `averageEmbeddings with multiple vectors should compute average`() {
        val v1 = vec192(1f)
        val v2 = vec192(3f)
        val v3 = vec192(5f)

        val result = embedder.averageEmbeddings(arrayOf(v1, v2, v3))
        assertEquals(192, result.size)
        // Before L2 norm, average should be 3
        var sumSq = 0f
        for (v in result) sumSq += v * v
        assertTrue("L2 norm should be ~1.0", kotlin.math.abs(sumSq - 1f) < 0.01f)
    }

    @Test
    fun `averageEmbeddings with varying sizes should handle gracefully`() {
        val v1 = vec192(1f)
        val v2 = vec192(0.5f)
        val result = embedder.averageEmbeddings(arrayOf(v1, v2))
        assertNotNull(result)
        assertEquals(192, result.size)
    }

    @Test
    fun `isReady should return false before init`() {
        assertFalse(embedder.isReady())
    }

    @Test
    fun `getEmbeddingDim should return default`() {
        assertEquals(192, embedder.getEmbeddingDim())
    }
}
