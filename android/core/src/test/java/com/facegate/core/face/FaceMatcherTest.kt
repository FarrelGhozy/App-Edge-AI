package com.facegate.core.face

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FaceMatcherTest {

    private lateinit var matcher: FaceMatcher
    private val threshold = 0.70f
    private val dim = 512

    private fun makeVector(vararg values: Float): FloatArray {
        val arr = FloatArray(dim)
        for (i in arr.indices) {
            arr[i] = if (i < values.size) values[i] else 0.01f * (i % 10)
        }
        arr[0] = values[0]
        return arr
    }

    /** Zero-filled vector (no shared noise pattern) — for similarity tests where
     *  unrelated dimensions must NOT contribute cosine similarity. */
    private fun cleanVector(vararg values: Float): FloatArray {
        val arr = FloatArray(dim)
        for (i in values.indices) arr[i] = values[i]
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
        matcher = FaceMatcher(baseThreshold = threshold)
    }

    @Test
    fun `buildIndex with empty list should not crash`() {
        matcher.buildIndex(emptyList())
        val result = matcher.match(makeVector(1f))
        assertFalse(result.isMatch)
        assertNull(result.studentId)
    }

    @Test
    fun `identical vectors should match with high confidence`() {
        val vec = normalize(makeVector(1f, 2f, 3f))
        matcher.buildIndex(listOf(entry("student1", vec)))
        val result = matcher.match(vec)
        assertTrue(result.isMatch)
        assertTrue(result.confidence >= threshold)
        assertEquals("student1", result.studentId)
    }

    @Test
    fun `similar vectors should find correct match`() {
        val v1 = normalize(makeVector(1f, 0f, 0f))
        val v2 = normalize(makeVector(0.9f, 0f, 0f))
        val v3 = normalize(makeVector(0f, 1f, 0f))
        matcher.buildIndex(listOf(entry("studentA", v1), entry("studentB", v2), entry("studentC", v3)))
        val result = matcher.match(v1)
        assertTrue(result.isMatch)
        assertEquals("studentA", result.studentId)
    }

    @Test
    fun `studentId should be returned in result`() {
        val vec = normalize(makeVector(1f))
        matcher.buildIndex(listOf(entry("unique_student", vec)))
        val result = matcher.match(vec)
        assertEquals("unique_student", result.studentId)
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
    fun `completely different vectors should not match`() {
        val dbVec = normalize(makeVector(1f, 0f, 0f))
        matcher.buildIndex(listOf(entry("db", dbVec)))
        val queryVec = FloatArray(192) { -dbVec[it] }
        val result = matcher.match(queryVec)
        assertFalse(result.isMatch)
    }

    @Test
    fun `match should set isMatch false for low similarity`() {
        val dbVec = normalize(makeVector(1f, 0f, 0f))
        matcher.buildIndex(listOf(entry("db", dbVec)))
        val diffVec = FloatArray(192) { -1f / kotlin.math.sqrt(192f) }
        val result = matcher.match(diffVec)
        assertFalse(result.isMatch)
    }

    @Test
    fun `large index should still return results`() {
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
    fun `second best info should be available`() {
        val v1 = normalize(makeVector(1f, 0f, 0f))
        val v2 = normalize(makeVector(0.95f, 0f, 0f))
        matcher.buildIndex(listOf(entry("best", v1), entry("second", v2)))
        val result = matcher.match(v1)
        assertEquals("second", result.secondBestId)
    }

    @Test
    fun `size should reflect vector count`() {
        assertEquals(0, matcher.size())
        matcher.buildIndex(listOf(entry("a", normalize(makeVector(1f)))))
        assertEquals(1, matcher.size())
        matcher.buildIndex(listOf(
            entry("b", normalize(makeVector(1f))),
            entry("c", normalize(makeVector(0f, 1f)))
        ))
        assertEquals(2, matcher.size())
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
    fun `getThreshold should return configured threshold`() {
        assertEquals(threshold, matcher.getThreshold(), 0.001f)
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
        // Match against a near-pose
        val query = normalize(makeVector(0.75f, 0.15f, 0f))
        val result = matcher.match(query)
        assertTrue(result.isMatch)
        assertEquals("student1", result.studentId)
    }

    @Test
    fun `size should count vectors not students`() {
        matcher.buildIndex(listOf(
            entry("s1", normalize(makeVector(1f, 0f))),
            entry("s1", normalize(makeVector(0f, 1f))),
            entry("s2", normalize(makeVector(0.5f, 0.5f)))
        ))
        assertEquals(3, matcher.size())
    }

    @Test
    fun `multi-pose same student should not collapse gap - no false reject`() {
        // Issue #77: best & second-best must be from DIFFERENT students.
        // Student A has 5 near-identical pose vectors; student B is far away.
        // Runner-up must be B (not another pose of A), keeping the gap large
        // so the adaptive threshold stays low and A is accepted.
        val poseA = (0 until 5).map { normalize(cleanVector(0.95f - it * 0.01f, 0.1f, 0f)) }
        val poseB = normalize(cleanVector(0.2f, 0.9f, 0f))
        val index = poseA.map { entry("studentA", it) } + entry("studentB", poseB)
        matcher.buildIndex(index)

        // Match against A's pose 1
        val result = matcher.match(normalize(cleanVector(0.97f, 0.1f, 0f)))
        assertTrue("studentA must match (false reject due to same-student runner-up)", result.isMatch)
        assertEquals("studentA", result.studentId)
        assertNotEquals("second-best must be a different student", "studentA", result.secondBestId)
        assertTrue("gap must stay large", result.gapScore > 0.3f)
    }

    @Test
    fun `stress - concurrent buildIndex and match should never crash`() {
        // Issue #75: index must be consistent under concurrent rebuild (sync
        // worker) and reads (VideoMatchEngine, Dispatchers.Default). With a
        // clear()+add() COW list, two racing builds could interleave and leave
        // the index doubled (5+5=10) or half-built — the volatile snapshot swap
        // guarantees every reader sees a complete, correct list.
        val vectors = (1..5).map { entry("s$it", normalize(makeVector(it.toFloat() / 5f, 0.2f))) }
        matcher.buildIndex(vectors)
        val query = normalize(makeVector(0.8f, 0.1f))

        val threads = (1..8).map { t ->
            Thread {
                repeat(400) { i ->
                    if (i % 5 == 0) {
                        // Simulate sync rebuild: rebuild from a shuffled copy
                        matcher.buildIndex(vectors.shuffled())
                    } else {
                        // Simulate live match
                        try {
                            matcher.match(query)
                        } catch (e: java.util.ConcurrentModificationException) {
                            throw AssertionError("ConcurrentModificationException during concurrent match/buildIndex", e)
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Index must remain EXACTLY consistent afterwards (never doubled/interleaved)
        assertEquals("concurrent rebuild must not double entries", 5, matcher.size())
        // Each original vector still present and matchable
        val res = matcher.match(query)
        assertNotNull(res.studentId)
    }

    @Test
    fun `thin gap no longer false-rejects genuine match (issue 66)`() {
        // Issue #66: AMBIGUITY_RATIO 0.15 penalized genuine matches with a thin
        // (but real) gap. With 0.08, best=0.75 vs second=0.65 (gap 0.10) must
        // MATCH. Under the old ratio (0.15): adjusted = 0.75-(0.15-0.10)*0.5 =
        // 0.725 < adaptive 0.73 (video) → false reject. Now: gap ≥ 0.08 → no
        // ambiguity penalty → adjusted = 0.75 ≥ 0.73 → match.
        val q = normalize(cleanVector(1f, 0f, 0f))
        val vBest = normalize(cleanVector(0.75f, kotlin.math.sqrt(1f - 0.75f * 0.75f), 0f))
        val vSecond = normalize(cleanVector(0.65f, 0f, kotlin.math.sqrt(1f - 0.65f * 0.65f)))
        matcher.buildIndex(listOf(
            entry("genuine", vBest),
            entry("other", vSecond)
        ))
        val result = matcher.match(q)
        assertTrue("genuine match with gap 0.10 must not be false-rejected", result.isMatch)
        assertEquals("genuine", result.studentId)
    }

    @Test
    fun `averageEmbeddings produces L2-normalized centroid`() {
        // Issue #66: enrollment must store a robust averaged template per pose.
        // The shared FaceEmbedderProvider default must output an L2-normalized
        // vector so downstream dot-product matching stays valid.
        val provider = object : FaceEmbedderProvider {
            override fun init() = true
            override fun embed(faceCrop: Bitmap) = FloatArray(embeddingDim)
            override val embeddingDim = 512
            override val inputSize = 112
            override fun release() {}
            override fun isReady() = true
        }
        val a = FloatArray(512).also { it[0] = 1f }  // unit e0
        val b = FloatArray(512).also { it[1] = 1f }  // unit e1
        val avg = provider.averageEmbeddings(arrayOf(a, b))
        // Centroid (0.5, 0.5) → L2 → (0.7071, 0.7071)
        assertEquals(0.7071f, avg[0], 1e-3f)
        assertEquals(0.7071f, avg[1], 1e-3f)
        var norm = 0.0
        for (x in avg) norm += x * x
        assertEquals("averaged template must be L2-normalized", 1.0, kotlin.math.sqrt(norm), 1e-4)
    }
}
