package com.facegate.adminapp.register

import com.facegate.core.data.remote.ApiService
import com.facegate.core.face.FaceDetectionResult
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.LivenessDetector
import com.facegate.core.data.remote.dto.UploadFaceRequest
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
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class FaceRegisterViewModelTest {

    @MockK
    private lateinit var faceDetector: FaceDetectorWrapper

    @MockK
    private lateinit var faceEmbedder: FaceEmbedder

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

        every { faceDetector.init() } returns Unit
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
    fun `initial state should be DETECTING`() {
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertFalse(state.isSuccess)
        assertEquals(0, state.totalFramesCollected)
        assertEquals(5, state.framesRequired) // 5 poses: CENTER, LEFT, RIGHT, UP, DOWN
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
        assertEquals(0, state.totalFramesCollected)
        assertNull(state.error)
        assertFalse(state.isSuccess)
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
        assertEquals(0, state.totalFramesCollected)
        assertEquals(5, state.framesRequired)
        assertEquals(0f, state.currentQualityScore)
        assertTrue(state.qualityMessages.isEmpty())
        assertEquals(CapturePose.CENTER, state.currentPose)
        assertTrue(state.capturedPoses.isEmpty())
        assertEquals(0f, state.currentYaw)
        assertEquals(0f, state.currentPitch)
    }

    @Test
    fun `skipPose should move to next pose`() {
        // After reset, current pose is CENTER
        viewModel.setStudentId("test-123")
        viewModel.skipPose()
        val state = viewModel.state.value
        assertEquals(CapturePose.LEFT, state.currentPose)
        assertEquals(FaceRegisterStep.POSITIONING, state.step)
    }

    @Test
    fun `skipPose through all 5 poses should start embedding`() {
        viewModel.setStudentId("test-123")
        // Skip CENTER -> LEFT -> RIGHT -> UP -> DOWN -> (should proceed to embedding but fail with empty queues)
        viewModel.skipPose() // skip CENTER
        viewModel.skipPose() // skip LEFT
        viewModel.skipPose() // skip RIGHT
        viewModel.skipPose() // skip UP
        viewModel.skipPose() // skip DOWN — should trigger proceedToEmbedding
        testDispatcher.scheduler.advanceUntilIdle()
        // Since all queues are empty, state should be ERROR
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.ERROR, state.step)
        assertNotNull(state.error)
    }

    @Test
    fun `reset should clear after partial capture`() {
        viewModel.setStudentId("test-123")
        viewModel.skipPose() // move to LEFT
        viewModel.reset()
        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertEquals(CapturePose.CENTER, state.currentPose)
        assertTrue(state.capturedPoses.isEmpty())
        assertEquals(0, state.totalFramesCollected)
    }

    @Test
    fun `poseOrder should contain all 5 poses`() {
        viewModel.setStudentId("test-123")
        viewModel.skipPose()
        assertEquals(CapturePose.LEFT, viewModel.state.value.currentPose)
        viewModel.skipPose()
        assertEquals(CapturePose.RIGHT, viewModel.state.value.currentPose)
        viewModel.skipPose()
        assertEquals(CapturePose.UP, viewModel.state.value.currentPose)
        viewModel.skipPose()
        assertEquals(CapturePose.DOWN, viewModel.state.value.currentPose)
    }
}
