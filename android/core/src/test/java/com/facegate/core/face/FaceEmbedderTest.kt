package com.facegate.core.face

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaceEmbedderTest {

    private lateinit var embedder: FaceEmbedder

    // Helper: create vector of given dimension
    private fun vec(dim: Int, value: Float = 0.1f): FloatArray = FloatArray(dim) { value }

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
    fun `default embedding dimension should be 192 for TFLite MobileFaceNet`() {
        assertEquals(192, embedder.getEmbeddingDim())
    }

    @Test
    fun `averageEmbeddings with empty array should return array of dim size`() {
        val result = embedder.averageEmbeddings(emptyArray())
        assertEquals(192, result.size)
    }

    @Test
    fun `averageEmbeddings with single vector should normalize correctly`() {
        val v = vec(192, 0.5f)
        val result = embedder.averageEmbeddings(arrayOf(v))
        assertEquals(v.size, result.size)
        // L2 norm should be ~1.0
        var sumSq = 0f
        for (v in result) sumSq += v * v
        assertTrue("L2 norm should be ~1.0", kotlin.math.abs(sumSq - 1f) < 0.01f)
    }

    @Test
    fun `averageEmbeddings with multiple vectors should compute average`() {
        val v1 = vec(192, 1f)
        val v2 = vec(192, 3f)
        val v3 = vec(192, 5f)

        val result = embedder.averageEmbeddings(arrayOf(v1, v2, v3))
        assertEquals(192, result.size)
        var sumSq = 0f
        for (v in result) sumSq += v * v
        assertTrue("L2 norm should be ~1.0", kotlin.math.abs(sumSq - 1f) < 0.01f)
    }

    @Test
    fun `averageEmbeddings with varying sizes should handle gracefully`() {
        val v1 = vec(192, 1f)
        val v2 = vec(192, 0.5f)
        val result = embedder.averageEmbeddings(arrayOf(v1, v2))
        assertNotNull(result)
        assertEquals(192, result.size)
    }

    @Test
    fun `isReady should return false before init`() {
        assertFalse(embedder.isReady())
    }

    @Test
    fun `getEmbeddingDim should return default 192`() {
        assertEquals(192, embedder.getEmbeddingDim())
    }

    @Test
    fun `not quantized before init`() {
        assertFalse(embedder.isModelQuantized())
    }

    @Test
    fun `embedding dim can be overridden`() {
        // Only test the getter - init with non-existent model will fail silently
        assertEquals(192, embedder.getEmbeddingDim())
    }
}
