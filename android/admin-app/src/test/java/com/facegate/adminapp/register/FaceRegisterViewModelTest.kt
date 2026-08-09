package com.facegate.adminapp.register

import com.facegate.core.data.remote.ApiService
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedderProvider
import com.facegate.core.face.LivenessDetector
import android.util.Log
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FaceRegisterViewModelTest {

    @MockK
    private lateinit var faceDetector: FaceDetectorWrapper

    @MockK
    private lateinit var faceEmbedder: FaceEmbedderProvider

    @MockK
    private lateinit var livenessDetector: LivenessDetector

    @MockK
    private lateinit var apiService: ApiService

    private lateinit var viewModel: FaceRegisterViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        every { faceDetector.init() } returns true
        every { faceEmbedder.init() } returns true
        every { livenessDetector.reset() } returns Unit

        viewModel = FaceRegisterViewModel(
            faceDetector = faceDetector,
            faceEmbedder = faceEmbedder,
            livenessDetector = livenessDetector,
            apiService = apiService
        )

        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state should be DETECTING with new video capture defaults`() {
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertFalse(state.isSuccess)
        // #132: 5–10 frame FRONT (bukan 5 pose)
        assertEquals(5, state.framesRequired)
        assertEquals(10, state.framesMax)
        assertEquals(0f, state.recordingProgress)
    }

    @Test
    fun `setStudentId should not crash`() {
        viewModel.setStudentId("test-student-123")
        assert(true)
    }

    @Test
    fun `reset should return to DETECTING step`() {
        viewModel.reset()
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertNull(state.error)
        assertFalse(state.isSuccess)
        assertEquals(0f, state.recordingProgress)
        assertTrue(state.selectedFrames.isEmpty())
    }

    @Test
    fun `state defaults should be correct`() {
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertEquals("Arahkan wajah ke dalam oval", state.message)
        assertNull(state.error)
        assertFalse(state.isUploading)
        assertFalse(state.isSuccess)
        assertNull(state.detection)
        assertEquals(5, state.framesRequired)
        assertEquals(10, state.framesMax)
        assertEquals(0f, state.currentQualityScore)
        assertTrue(state.qualityMessages.isEmpty())
        assertEquals(0f, state.currentYaw)
        assertEquals(0f, state.currentPitch)
        assertEquals(10, state.recordingSeconds)
    }

    @Test
    fun `retryRecording should return to DETECTING and clear selection`() {
        viewModel.setStudentId("test-123")
        viewModel.retryRecording()
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertTrue(state.selectedFrames.isEmpty())
        assertEquals(0f, state.recordingProgress)
    }

    @Test
    fun `confirmRecording with empty selection should not crash`() {
        viewModel.setStudentId("test-123")
        viewModel.confirmRecording()
        val state = viewModel.state.value
        // Tidak ada frame terpilih → tetap di step yang sama (tidak crash)
        assertTrue(state.step != FaceRegisterStep.UPLOADING)
    }

    @Test
    fun `reset should clear after partial capture`() {
        viewModel.setStudentId("test-123")
        viewModel.retryRecording()
        viewModel.reset()
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertTrue(state.selectedFrames.isEmpty())
        assertEquals(0f, state.recordingProgress)
    }

    @Test
    fun `capture mode set resets studentId and clears captured vectors on reset`() {
        viewModel.setStudentId("test-123")
        viewModel.setCaptureMode(true)
        // reset() dalam capture mode harus membersihkan capturedVectors
        viewModel.reset()
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertNull(viewModel.capturedVectors.value)
    }

    @Test
    fun `capture mode should never call uploadFaces api`() {
        viewModel.setCaptureMode(true)
        viewModel.reset()
        coVerify(exactly = 0) { apiService.uploadFaces(any(), any()) }
        coVerify(exactly = 0) { apiService.uploadFace(any(), any()) }
    }
}
