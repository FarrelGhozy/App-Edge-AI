package com.facegate.adminapp.register

import com.facegate.core.data.remote.ApiService
import com.facegate.core.face.FaceDetectionResult
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.LivenessDetector
import com.facegate.core.data.remote.dto.UploadFaceRequest
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
        assertEquals(0, state.framesCollected)
        assertEquals(3, state.framesRequired)
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
        assertEquals(0, state.framesCollected)
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
        assertEquals(0, state.framesCollected)
        assertEquals(3, state.framesRequired)
        assertEquals(0f, state.currentQualityScore)
        assertTrue(state.qualityMessages.isEmpty())
    }
}
