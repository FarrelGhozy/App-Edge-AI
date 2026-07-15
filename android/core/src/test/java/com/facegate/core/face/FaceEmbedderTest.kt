package com.facegate.core.face

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaceEmbedderTest {

    private lateinit var embedder: FaceEmbedder

    @Before
    fun setup() {
        embedder = FaceEmbedder()
    }

    @Test
    fun `initial state should not be initialized`() {
        assertFalse(embedder.isInitialized)
    }

    @Test
    fun `null model init should not crash and remain not initialized`() {
        // Since no actual TFLite model exists in test environment,
        // init should handle gracefully
        embedder.init("nonexistent_model.tflite", 192, 112)
        // Model not found — should stay uninitialized
        assertFalse(embedder.isInitialized)
    }

    @Test
    fun `embedding dimension should be configurable`() {
        // Test the dim is stored correctly even without model init
        embedder.init("test.tflite", embeddingDim = 512, inputSize = 112)
        // Model load failed, but the embedding dim config should be stored
        assertEquals(512, embedder.embeddingDim)
        assertEquals(112, embedder.inputSize)
    }

    @Test
    fun `embed with uninitialized embedder should not crash`() {
        // Embedding without init should handle gracefully
        val bitmap = null
        val result = embedder.embed(bitmap)
        assertNull("Embedding null bitmap should return null", result)
    }

    @Test
    fun `default embedding dimension should be 192`() {
        assertEquals(192, embedder.embeddingDim)
    }

    @Test
    fun `192 dimension should be settable`() {
        embedder.init("test.tflite", embeddingDim = 192, inputSize = 112)
        assertEquals(192, embedder.embeddingDim)
    }

    @Test
    fun `512 dimension should be settable`() {
        embedder.init("test.tflite", embeddingDim = 512, inputSize = 112)
        assertEquals(512, embedder.embeddingDim)
    }

    @Test
    fun `different input sizes should be accepted`() {
        embedder.init("test.tflite", embeddingDim = 192, inputSize = 160)
        assertEquals(160, embedder.inputSize)

        embedder.init("test2.tflite", embeddingDim = 192, inputSize = 112)
        assertEquals(112, embedder.inputSize)
    }

    @Test
    fun `averageEmbeddings should return null for empty list`() {
        val result = embedder.averageEmbeddings(emptyList())
        assertNull(result)
    }

    @Test
    fun `averageEmbeddings with single vector should return same vector`() {
        val vectors = listOf(floatArrayOf(1f, 2f, 3f))
        val result = embedder.averageEmbeddings(vectors)
        assertNotNull(result)
        assertArrayEquals(vectors[0], result, 0.001f)
    }

    @Test
    fun `averageEmbeddings with multiple vectors should compute average`() {
        val v1 = floatArrayOf(1f, 2f, 3f)
        val v2 = floatArrayOf(3f, 4f, 5f)
        val v3 = floatArrayOf(5f, 6f, 7f)

        val result = embedder.averageEmbeddings(listOf(v1, v2, v3))
        assertNotNull(result)
        assertEquals(3, result!!.size)
        // Expected: (1+3+5)/3 = 3, (2+4+6)/3 = 4, (3+5+7)/3 = 5
        assertEquals(3f, result[0], 0.001f)
        assertEquals(4f, result[1], 0.001f)
        assertEquals(5f, result[2], 0.001f)
    }

    @Test
    fun `averageEmbeddings with null entries should skip them`() {
        val v1 = floatArrayOf(1f, 2f)
        val v2: FloatArray? = null
        val v3 = floatArrayOf(3f, 4f)

        val result = embedder.averageEmbeddings(listOf(v1, v2, v3))
        assertNotNull(result)
        assertArrayEquals(floatArrayOf(2f, 3f), result, 0.001f)
    }

    @Test
    fun `averageEmbeddings with varying sizes should not crash`() {
        val v1 = floatArrayOf(1f, 2f, 3f)
        val v2 = floatArrayOf(4f, 5f)  // different size

        val result = embedder.averageEmbeddings(listOf(v1, v2))
        // Should gracefully handle or return null for incompatible sizes
        // Current implementation may crash, so let's check
        assertNotNull("Should handle mixed-size vectors gracefully", result)
    }

    @Test
    fun `reinit with new model should update config`() {
        embedder.init("model_v1.tflite", embeddingDim = 192, inputSize = 112)
        assertEquals(192, embedder.embeddingDim)

        embedder.init("model_v2_arcface.tflite", embeddingDim = 512, inputSize = 112)
        assertEquals(512, embedder.embeddingDim)
        assertEquals(112, embedder.inputSize)
    }

    @Test
    fun `batch average should handle large number of vectors`() {
        val vectors = (1..100).map { i ->
            FloatArray(192) { j -> (i + j) % 100 / 100f }
        }
        val result = embedder.averageEmbeddings(vectors)
        assertNotNull(result)
        assertEquals(192, result!!.size)
        // Verify all values are finite
        result.forEach { assertFalse("Average should not produce NaN", it.isNaN()) }
    }
}
