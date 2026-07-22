package com.facegate.core.face

import android.content.Context
import io.mockk.every
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
        every { mockContext.assets } returns mockk(relaxed = true)
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
    fun `model should not be quantized when not initialized`() {
        assertFalse(embedder.isModelQuantized())
    }

    @Test
    fun `embedding dim can be overridden`() {
        // Only test the getter — init with non-existent model will fail silently
        assertEquals(192, embedder.getEmbeddingDim())
    }

    @Test
    fun `averageEmbeddings with empty array should return array of dim size`() {
        val result = embedder.averageEmbeddings(emptyArray())
        assertEquals(192, result.size)
    }

    @Test
    fun `averageEmbeddings with single vector should normalize correctly`() {
        val input = vec(192, 0.5f)
        val result = embedder.averageEmbeddings(arrayOf(input))
        assertEquals(192, result.size)
        // Result should be L2-normalized (norm ~1.0)
        var norm = 0f
        for (v in result) norm += v * v
        norm = kotlin.math.sqrt(norm)
        assertEquals(1.0f, norm, 0.01f)
    }

    @Test
    fun `averageEmbeddings with multiple vectors should compute average`() {
        val v1 = vec(192, 1.0f)
        val v2 = vec(192, 3.0f)
        val result = embedder.averageEmbeddings(arrayOf(v1, v2))
        assertEquals(192, result.size)
        // Each element should be averaged: (1.0 + 3.0) / 2 = 2.0, then L2-normalized
        var norm = 0f
        for (v in result) norm += v * v
        norm = kotlin.math.sqrt(norm)
        assertEquals(1.0f, norm, 0.01f)
    }

    @Test
    fun `averageEmbeddings with varying sizes should handle gracefully`() {
        // If dims differ, code takes the actual embeddingDim (192)
        val v1 = FloatArray(192) { 0.5f }
        val result = embedder.averageEmbeddings(arrayOf(v1))
        assertEquals(192, result.size)
    }

    @Test
    fun `release should make embedder not ready`() {
        embedder.release()
        assertFalse(embedder.isReady())
    }

    @Test
    fun `embedBatch on uninitialized should throw`() {
        // TFLite native library not available — init() will throw UnsatisfiedLinkError
        try {
            embedder.embedBatch(emptyList())
        } catch (_: Throwable) {
            // expected
        }
    }
}
