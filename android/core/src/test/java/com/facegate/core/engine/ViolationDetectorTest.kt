package com.facegate.core.engine

import com.facegate.core.data.local.dao.CampusRuleDao
import com.facegate.core.data.local.entity.CampusRuleEntity
import io.mockk.coEvery
import io.mockk.mockk
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ViolationDetectorTest {

    private lateinit var dao: CampusRuleDao
    private lateinit var detector: ViolationDetector
    private val student = StudentInfo(id = "s1", studyProgram = "TI", academicYear = "2026")

    private fun jakarta(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of("Asia/Jakarta"))

    private fun rule(
        id: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        isRestricted: Boolean = true,
        appliesToAll: Boolean = true
    ) = CampusRuleEntity(
        id = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        isRestricted = isRestricted,
        appliesToAll = appliesToAll
    )

    @Before
    fun setup() {
        dao = mockk()
        detector = ViolationDetector(dao)
    }

    @Test
    fun `KEMBALI is never a violation`() = runBlocking {
        coEvery { dao.getByDay(any()) } returns emptyList()
        val result = detector.check(ToggleAction.KEMBALI, student)
        assertFalse(result.isViolation)
    }

    @Test
    fun `normal rule matches inside window`() = runBlocking {
        // Rule 08:00-12:00, scan jam 10:00 → violation
        coEvery { dao.getByDay(1) } returns listOf(rule("r1", 1, "08:00", "12:00"))
        val result = detector.check(ToggleAction.KELUAR, student, jakarta(2026, 8, 3, 10, 0))
        assertTrue(result.isViolation)
        assertEquals("restricted_hours", result.violationType)
    }

    @Test
    fun `normal rule does not match outside window`() = runBlocking {
        coEvery { dao.getByDay(1) } returns listOf(rule("r1", 1, "08:00", "12:00"))
        val result = detector.check(ToggleAction.KELUAR, student, jakarta(2026, 8, 3, 13, 0))
        assertFalse(result.isViolation)
    }

    @Test
    fun `overnight rule 22-05 matches after midnight`() = runBlocking {
        // #64 REG: 22:00-05:00, jam 00:30 (hari berikutnya) HARUS violation.
        // Logika lama `!current.isBefore(start) && current.isBefore(end)` gagal
        // di rentang lintas-midnight.
        coEvery { dao.getByDay(2) } returns listOf(rule("r1", 2, "22:00", "05:00"))
        val result = detector.check(ToggleAction.KELUAR, student, jakarta(2026, 8, 4, 0, 30))
        assertTrue("22:00-05:00 rule must catch 00:30 (overnight)", result.isViolation)
    }

    @Test
    fun `overnight rule 22-05 matches before midnight`() = runBlocking {
        coEvery { dao.getByDay(1) } returns listOf(rule("r1", 1, "22:00", "05:00"))
        val result = detector.check(ToggleAction.KELUAR, student, jakarta(2026, 8, 3, 23, 0))
        assertTrue(result.isViolation)
    }

    @Test
    fun `overnight rule 22-05 does not match at 15-00`() = runBlocking {
        coEvery { dao.getByDay(1) } returns listOf(rule("r1", 1, "22:00", "05:00"))
        val result = detector.check(ToggleAction.KELUAR, student, jakarta(2026, 8, 3, 15, 0))
        assertFalse(result.isViolation)
    }

    @Test
    fun `non-restricted rule is ignored`() = runBlocking {
        coEvery { dao.getByDay(1) } returns listOf(rule("r1", 1, "00:00", "23:59", isRestricted = false))
        val result = detector.check(ToggleAction.KELUAR, student, jakarta(2026, 8, 3, 10, 0))
        assertFalse(result.isViolation)
    }

    @Test
    fun `rule for another study program is skipped`() = runBlocking {
        coEvery { dao.getByDay(1) } returns listOf(
            CampusRuleEntity(
                id = "r1", dayOfWeek = 1, startTime = "00:00", endTime = "23:59",
                isRestricted = true, appliesToAll = false,
                studyProgram = "Hukum", academicYear = null
            )
        )
        val result = detector.check(ToggleAction.KELUAR, student, jakarta(2026, 8, 3, 10, 0))
        assertFalse(result.isViolation)
    }
}
