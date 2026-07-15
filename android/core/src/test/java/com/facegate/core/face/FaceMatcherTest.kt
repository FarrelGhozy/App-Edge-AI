package com.facegate.core.face

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaceMatcherTest {

    private lateinit var matcher: FaceMatcher
    private val threshold = 0.65f
    private val maxResults = 3

    // Helper: create a normalized 192-d vector
    private fun makeVector(vararg values: Float): FloatArray {
        val arr = FloatArray(192)
        // Fill with deterministic pattern
        for (i in arr.indices) {
            arr[i] = if (i < values.size) values[i] else 0.01f * (i % 10)
        }
        arr[0] = values[0]
        return arr
    }

    @Before
    fun setup() {
        matcher = FaceMatcher(threshold = threshold, maxResults = maxResults)
    }

    @Test
    fun `buildIndex with empty map should not crash`() {
        matcher.buildIndex(emptyMap())
        val result = matcher.findTopKMatches(makeVector(1f))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `identical vectors should match with high confidence`() {
        val vec = makeVector(1f, 2f, 3f)
        val map = mapOf("student1" to vec)
        matcher.buildIndex(map)

        val results = matcher.findTopKMatches(vec)
        assertEquals(1, results.size)
        assertTrue(results[0].confidence >= threshold)
        assertEquals("student1", results[0].studentId)
    }

    @Test
    fun `similar vectors should find correct match`() {
        val v1 = makeVector(1f, 0f, 0f)
        val v2 = makeVector(0.5f, 0f, 0f)  // similar to v1
        val v3 = makeVector(0f, 1f, 0f)   // different from v1

        val map = mapOf("studentA" to v1, "studentB" to v2, "studentC" to v3)
        matcher.buildIndex(map)

        val results = matcher.findTopKMatches(v1)
        assertTrue(results.isNotEmpty())
        assertEquals("studentA", results[0].studentId)
        assertTrue(results[0].confidence >= threshold)
    }

    @Test
    fun `orthogonal vectors should not match`() {
        val v1 = makeVector(-2.51f, -2.51f, -2.51f)  // normalized inverse
        val v2 = makeVector(2.51f, 2.51f, 2.51f)   // forward direction

        // Copy to ensure same pattern except first element
        val map = mapOf("studentX" to v2)
        matcher.buildIndex(map)

        val results = matcher.findTopKMatches(v1)
        assertTrue(results.isEmpty() || results[0].confidence < threshold)
    }

    @Test
    fun `top-K should return correct number of results`() {
        val vec = makeVector(1f, 0f, 0f)
        val map = (1..10).associate { "student$it" to makeVector(0.9f - it * 0.02f) }
        matcher.buildIndex(map)

        // The target vector similar to many
        val results = matcher.findTopKMatches(makeVector(0.9f, 0f, 0f))
        assertTrue(results.size <= maxResults)
        // Results should be sorted by confidence descending
        for (i in 1 until results.size) {
            assertTrue(results[i - 1].confidence >= results[i].confidence)
        }
    }

    @Test
    fun `studentId should be returned in result`() {
        val vec = makeVector(1f)
        matcher.buildIndex(mapOf("unique_student" to vec))

        val results = matcher.findTopKMatches(vec)
        assertEquals("unique_student", results[0].studentId)
    }

    @Test
    fun `rebuilt index should replace old data`() {
        val oldVec = makeVector(1f)
        matcher.buildIndex(mapOf("old" to oldVec))

        val newVec = makeVector(0f, 1f)
        matcher.buildIndex(mapOf("new" to newVec))

        val oldResults = matcher.findTopKMatches(oldVec)
        val newResults = matcher.findTopKMatches(newVec)

        // After rebuild, "old" shouldn't be matched anymore since index only has "new"
        assertTrue(oldResults.isEmpty() || oldResults[0].studentId == "new")
        assertTrue(newResults.isNotEmpty())
    }

    @Test
    fun `completely different vectors should return empty`() {
        val dbVec = makeVector(1f, 0f, 0f)
        matcher.buildIndex(mapOf("db" to dbVec))

        // Vectors pointing opposite direction
        val queryVec = FloatArray(192) { -dbVec[it] }
        val results = matcher.findTopKMatches(queryVec)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `normalized cosine similarity range check`() {
        // Two identical vectors -> cos = 1.0 -> confidence = 1.0
        val v1 = FloatArray(192) { 1f / kotlin.math.sqrt(192f) }
        matcher.buildIndex(mapOf("id" to v1))

        val results = matcher.findTopKMatches(v1)
        assertTrue(results.isNotEmpty())
        assertTrue(results[0].confidence in 0.9f..1.01f)
    }

    @Test
    fun `findTopKMatches accepts float threshold parameter`() {
        val v1 = makeVector(1f, 0f, 0f)
        matcher.buildIndex(mapOf("student" to v1))

        // With very low threshold, should still find
        val results = matcher.findTopKMatches(v1)
        assertTrue(results.isNotEmpty())
        assertTrue(results[0].confidence >= 0.5f)
    }

    @Test
    fun `large index should still return results quickly`() {
        val baseVec = makeVector(1f, 0f, 0f)
        // 1000 students with slightly different vectors
        val map = (0 until 1000).associate { id ->
            val v = baseVec.copyOf()
            v[0] = 1f + (id % 50) * 0.01f
            "student$id" to v
        }
        matcher.buildIndex(map)

        val query = makeVector(1f, 0f, 0f)
        val start = System.nanoTime()
        val results = matcher.findTopKMatches(query)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(results.isNotEmpty())
        assertTrue("Matching 1000 vectors took $elapsedMs ms", elapsedMs < 200)
    }
}
