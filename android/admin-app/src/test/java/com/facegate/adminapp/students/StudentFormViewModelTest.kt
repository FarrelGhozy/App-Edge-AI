package com.facegate.adminapp.students

import androidx.lifecycle.SavedStateHandle
import android.util.Log
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.PoseVectorEntry
import com.facegate.core.data.remote.dto.StudentDto
import com.facegate.core.data.remote.dto.StatusResponse
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
class StudentFormViewModelTest {

    @MockK
    private lateinit var apiService: ApiService

    private val testDispatcher = StandardTestDispatcher()

    private val savedStudent = StudentDto(
        id = "student-1",
        nim = "2024001",
        name = "Budi",
        studyProgram = "Teknik Informatika",
        academicYear = "2024"
    )

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

    private fun createViewModel(withFaces: Boolean): StudentFormViewModel {
        val handle = SavedStateHandle().apply {
            if (withFaces) {
                set(
                    "capturedFaceVectors",
                    listOf(
                        PoseVectorEntry(pose = "FRONT_1", vector = (1..512).map { it.toFloat() }),
                        PoseVectorEntry(pose = "FRONT_2", vector = (1..512).map { it.toFloat() })
                    )
                )
            }
        }
        return StudentFormViewModel(apiService, handle)
    }

    private fun fillForm(viewModel: StudentFormViewModel) {
        viewModel.updateField("nim", "2024001")
        viewModel.updateField("name", "Budi")
        viewModel.updateField("studyProgram", "Teknik Informatika")
        viewModel.updateField("academicYear", "2024")
    }

    @Test
    fun `save with captured face should create student then upload faces`() = runTest {
        val viewModel = createViewModel(withFaces = true)
        coEvery { apiService.createStudent(any()) } returns Response.success(savedStudent)
        coEvery { apiService.uploadFaces(any(), any()) } returns Response.success(StatusResponse(success = true))

        fillForm(viewModel)
        viewModel.save(isEdit = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { apiService.createStudent(any()) }
        coVerify(exactly = 1) { apiService.uploadFaces(eq("student-1"), any()) }
        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertEquals("student-1", state.savedStudentId)
        assertNull(state.faceUploadWarning)
        assertFalse(state.isUploadingFace)
    }

    @Test
    fun `save without captured face should not call uploadFaces`() = runTest {
        val viewModel = createViewModel(withFaces = false)
        coEvery { apiService.createStudent(any()) } returns Response.success(savedStudent)

        fillForm(viewModel)
        viewModel.save(isEdit = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { apiService.createStudent(any()) }
        coVerify(exactly = 0) { apiService.uploadFaces(any(), any()) }
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `face upload failure should keep student saved with warning`() = runTest {
        val viewModel = createViewModel(withFaces = true)
        coEvery { apiService.createStudent(any()) } returns Response.success(savedStudent)
        coEvery { apiService.uploadFaces(any(), any()) } returns
            retrofit2.Response.error(500, okhttp3.ResponseBody.create(null, "{}"))

        fillForm(viewModel)
        viewModel.save(isEdit = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { apiService.uploadFaces(eq("student-1"), any()) }
        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertTrue(state.faceUploadWarning?.contains("unggah wajah gagal") == true)
        assertFalse(state.isUploadingFace)
    }

    @Test
    fun `create failure should show error and not upload faces`() = runTest {
        val viewModel = createViewModel(withFaces = true)
        coEvery { apiService.createStudent(any()) } returns
            retrofit2.Response.error(400, okhttp3.ResponseBody.create(null, """{"error":"NIM sudah terdaftar"}"""))

        fillForm(viewModel)
        viewModel.save(isEdit = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { apiService.uploadFaces(any(), any()) }
        val state = viewModel.uiState.value
        assertFalse(state.isSaved)
        assertEquals("NIM sudah terdaftar", state.error)
    }

    @Test
    fun `clearCapturedFaces should null captured vectors`() {
        val viewModel = createViewModel(withFaces = true)
        assertEquals(2, viewModel.capturedFaces.value?.size)
        viewModel.clearCapturedFaces()
        assertNull(viewModel.capturedFaces.value)
    }
}
