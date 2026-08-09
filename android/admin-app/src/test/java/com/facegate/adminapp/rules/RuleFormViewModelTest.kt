package com.facegate.adminapp.rules

import android.util.Log
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.ApiResponse
import com.facegate.core.data.remote.dto.CampusRuleDto
import com.facegate.core.data.remote.dto.RuleRequest
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RuleFormViewModelTest {

    @MockK
    private lateinit var apiService: ApiService

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── TimeFormatter ──

    @Test
    fun `format should insert colon after 2 digits`() {
        assertEquals("12:00", TimeFormatter.format("1200"))
        assertEquals("12:34", TimeFormatter.format("1234"))
        assertEquals("12:3", TimeFormatter.format("123"))
        assertEquals("12", TimeFormatter.format("12"))
        assertEquals("1", TimeFormatter.format("1"))
    }

    @Test
    fun `format should ignore non digits and cap at 4 digits`() {
        assertEquals("12:00", TimeFormatter.format("12:00"))
        assertEquals("08:30", TimeFormatter.format("08:30"))
        assertEquals("12:34", TimeFormatter.format("12:34:56"))
        assertEquals("", TimeFormatter.format("abc"))
        assertEquals("", TimeFormatter.format(""))
    }

    @Test
    fun `format should clamp hour and minute`() {
        assertEquals("23:59", TimeFormatter.format("2559"))
        assertEquals("12:39", TimeFormatter.format("12399"))
    }

    @Test
    fun `isValid should enforce HH mm 24h`() {
        assertTrue(TimeFormatter.isValid("00:00"))
        assertTrue(TimeFormatter.isValid("08:30"))
        assertTrue(TimeFormatter.isValid("23:59"))
        assertFalse(TimeFormatter.isValid("24:00"))
        assertFalse(TimeFormatter.isValid("12:60"))
        assertFalse(TimeFormatter.isValid("8:30"))
        assertFalse(TimeFormatter.isValid(""))
    }

    // ── ViewModel setters ──

    @Test
    fun `setStartTime should auto format input`() {
        val viewModel = RuleFormViewModel(apiService)
        viewModel.setStartTime("1200")
        assertEquals("12:00", viewModel.uiState.value.startTime)
        viewModel.setStartTime("8:30")
        assertEquals("08:30", viewModel.uiState.value.startTime)
    }

    @Test
    fun `setEndTime should auto format input`() {
        val viewModel = RuleFormViewModel(apiService)
        viewModel.setEndTime("900")
        assertEquals("09:0", viewModel.uiState.value.endTime)
        viewModel.setEndTime("0900")
        assertEquals("09:00", viewModel.uiState.value.endTime)
    }

    // ── Submit validation ──

    @Test
    fun `submit with invalid time should show local error and not call api`() = runTest {
        val viewModel = RuleFormViewModel(apiService)
        viewModel.setStartTime("12:00")
        viewModel.setEndTime("12")
        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 0) { apiService.createRule(any()) }
        coVerify(exactly = 0) { apiService.updateRule(any(), any()) }
        assertEquals("Format jam harus HH:mm (contoh: 08:30)", viewModel.uiState.value.error)
    }

    @Test
    fun `submit with blank time should show error`() {
        val viewModel = RuleFormViewModel(apiService)
        viewModel.submit()
        assertEquals("Isi jam mulai dan jam selesai", viewModel.uiState.value.error)
    }

    @Test
    fun `submit with valid time should call create api`() = runTest {
        val viewModel = RuleFormViewModel(apiService)
        viewModel.setStartTime("0700")
        viewModel.setEndTime("2100")
        coEvery { apiService.createRule(any()) } returns
            Response.success(ApiResponse(success = true, data = CampusRuleDto("r1", 1, "07:00", "21:00")))

        viewModel.submit()
        advanceUntilIdle()

        coVerify(exactly = 1) { apiService.createRule(any()) }
        val captured = slot<RuleRequest>()
        coVerify { apiService.createRule(capture(captured)) }
        assertEquals("07:00", captured.captured.startTime)
        assertEquals("21:00", captured.captured.endTime)
    }
}
