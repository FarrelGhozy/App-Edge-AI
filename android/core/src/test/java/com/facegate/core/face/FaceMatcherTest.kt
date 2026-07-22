package com.facegate.core.face

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaceMatcherTest {

    private lateinit var matcher: FaceMatcher

    private fun makeVector(vararg values: Float): FloatArray {
        val arr = FloatArray(192)
        for (i in arr.indices) {
            arr[i] = if (i < values.size) values[i] else 0.01f * (i % 10)
        }
        arr[0] = values[0]
        return arr
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = kotlin.math.sqrt(norm)
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }

    private fun entry(studentId: String, v: FloatArray) = IndexEntry(studentId, v)

    @Before
    fun setup() {
        matcher = FaceMatcher(
            confidentThreshold = 0.85f,
            mediumThreshold = 0.80f,
            weakThreshold = 0.70f
        )
    }

    @Test
    fun `buildIndex with empty list should not crash`() {
        matcher.buildIndex(emptyList())
        val result = matcher.match(makeVector(1f))
        assertFalse(result.isMatch)
        assertNull(result.studentId)
    }

    @Test
    fun `identical vectors should match CONFIDENT`() {
        val vec = normalize(makeVector(1f, 2f, 3f))
        matcher.buildIndex(listOf(entry("student1", vec)))
        val result = matcher.match(vec)
        assertEquals(MatchDecision.CONFIDENT, result.decision)
        assertEquals("student1", result.studentId)
    }

    @Test
    fun `similar but different vectors should find correct match`() {
        val v1 = normalize(makeVector(1f, 0f, 0f))
        val v2 = normalize(makeVector(0.9f, 0f, 0f))
        val v3 = normalize(makeVector(0f, 1f, 0f))
        matcher.buildIndex(listOf(entry("studentA", v1), entry("studentB", v2), entry("studentC", v3)))
        val result = matcher.match(v1)
        assertTrue(result.isMatch)
        assertEquals("studentA", result.studentId)
    }

    @Test
    fun `completely different vectors should not match`() {
        val dbVec = normalize(makeVector(1f, 0f, 0f))
        matcher.buildIndex(listOf(entry("db", dbVec)))
        val queryVec = FloatArray(192) { -dbVec[it] }
        val result = matcher.match(queryVec)
        assertFalse(result.isMatch)
        assertEquals(MatchDecision.NO_MATCH, result.decision)
    }

    @Test
    fun `second best info should be available`() {
        val v1 = normalize(makeVector(1f, 0f, 0f))
        val v2 = normalize(makeVector(0.95f, 0f, 0f))
        matcher.buildIndex(listOf(entry("best", v1), entry("second", v2)))
        val result = matcher.match(v1)
        assertEquals("second", result.secondBestId)
    }

    @Test
    fun `threshold levels should produce correct decision`() {
        // CONFIDENT: topScore >= 0.85 AND gap >= 0.05
        // MEDIUM:    topScore >= 0.80 AND gap >= 0.03
        // WEAK:      topScore >= 0.70
        // NO_MATCH:  topScore < 0.70

        val base = normalize(makeVector(1f, 0f, 0f))

        // CONFIDENT: close match with big gap
        matcher.buildIndex(listOf(
            entry("confident", normalize(makeVector(0.95f, 0f, 0f))),
            entry("far", normalize(makeVector(0.1f, 1f, 0f)))
        ))
        val confidentMatch = matcher.match(base)
        assertEquals("confident", confidentMatch.studentId)
        assertEquals(MatchDecision.CONFIDENT, confidentMatch.decision)
    }

    @Test
    fun `rebuilt index should replace old data`() {
        val oldVec = normalize(makeVector(1f))
        matcher.buildIndex(listOf(entry("old", oldVec)))
        val newVec = normalize(makeVector(0f, 1f))
        matcher.buildIndex(listOf(entry("new", newVec)))
        assertEquals("new", matcher.match(oldVec).studentId)
        assertTrue(matcher.match(newVec).isMatch)
    }

    @Test
    fun `same student with multiple poses should match any pose`() {
        val center = normalize(makeVector(1f, 0f, 0f))
        val left = normalize(makeVector(0.8f, 0.1f, 0f))
        val right = normalize(makeVector(0.6f, 0.2f, 0f))
        matcher.buildIndex(listOf(
            entry("student1", center),
            entry("student1", left),
            entry("student1", right)
        ))
        val query = normalize(makeVector(0.75f, 0.15f, 0f))
        val result = matcher.match(query)
        assertTrue(result.isMatch)
        assertEquals("student1", result.studentId)
    }

    @Test
    fun `size should reflect vector count not student count`() {
        assertEquals(0, matcher.size())
        matcher.buildIndex(listOf(
            entry("s1", normalize(makeVector(1f, 0f))),
            entry("s1", normalize(makeVector(0f, 1f))),
            entry("s2", normalize(makeVector(0.5f, 0.5f)))
        ))
        assertEquals(3, matcher.size())
    }

    @Test
    fun `clear should remove all entries`() {
        matcher.buildIndex(listOf(entry("a", normalize(makeVector(1f)))))
        matcher.clear()
        assertEquals(0, matcher.size())
        val result = matcher.match(normalize(makeVector(1f)))
        assertNull(result.studentId)
    }

    @Test
    fun `getThresholds should return configured values`() {
        val (conf, med, weak) = matcher.getThresholds()
        assertEquals(0.85f, conf, 0.001f)
        assertEquals(0.80f, med, 0.001f)
        assertEquals(0.70f, weak, 0.001f)
    }

    @Test
    fun `large index should return results quickly`() {
        val baseVec = normalize(makeVector(1f, 0f, 0f))
        val entries = (0 until 1000).map { id ->
            val v = baseVec.copyOf()
            v[0] = 1f + (id % 50) * 0.01f
            entry("student$id", normalize(v))
        }
        matcher.buildIndex(entries)
        val query = normalize(makeVector(1f, 0f, 0f))
        val start = System.nanoTime()
        val result = matcher.match(query)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(result.isMatch)
        assertTrue("Matching 1000 vectors took $elapsedMs ms", elapsedMs < 500)
    }

    @Test
    fun `matchBatch should return results for multiple embeddings`() {
        val base = normalize(makeVector(1f, 0f, 0f))
        matcher.buildIndex(listOf(entry("s1", base)))
        val vecs = listOf(base, normalize(makeVector(-0.5f, 0.5f, 0.5f)))
        val results = matcher.matchBatch(vecs)
        assertEquals(2, results.size)
        assertTrue(results[0].isMatch)
        assertFalse(results[1].isMatch)
    }
}
