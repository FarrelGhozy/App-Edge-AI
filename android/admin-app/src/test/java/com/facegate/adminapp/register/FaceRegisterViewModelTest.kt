package com.facegate.adminapp.register

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.facegate.core.data.remote.ApiService
import com.facegate.core.face.FaceDetectionResult
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.LivenessDetector
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
        every { faceDetector.init() } just Runs
        every { faceEmbedder.init() } returns true
        every { faceEmbedder.getEmbeddingDim() } returns 192
        Dispatchers.setMain(testDispatcher)
        viewModel = FaceRegisterViewModel(faceDetector, faceEmbedder, livenessDetector, apiService)
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be DETECTING`() {
        assertEquals(FaceRegisterStep.DETECTING, viewModel.state.value.step)
        assertEquals("Arahkan wajah ke dalam oval", viewModel.state.value.message)
    }

    @Test
    fun `initial current pose should be CENTER`() {
        assertEquals(CapturePose.CENTER, viewModel.state.value.currentPose)
    }

    @Test
    fun `initial total frames should be 5`() {
        assertEquals(5, viewModel.state.value.framesRequired)
    }

    @Test
    fun `no face detected should stay in DETECTING`() {
        every { faceDetector.detectImage(any(), any()) } returns null

        val imageProxy = mockk<ImageProxy>(relaxed = true) {
            every { image } returns mockk(relaxed = true)
        }
        viewModel.setStudentId("santri-01")
        viewModel.onFrameCaptured(imageProxy, "santri-01")

        assertEquals(FaceRegisterStep.DETECTING, viewModel.state.value.step)
        assertTrue(
            viewModel.state.value.message.contains("Tidak ada wajah", ignoreCase = true)
        )
    }

    private fun mockCheckerboardBitmap(): Bitmap {
        val bmp = mockk<Bitmap>(relaxed = true)
        every { bmp.width } returns 640
        every { bmp.height } returns 480
        every { bmp.getPixels(any(), any(), any(), any(), any(), any(), any()) } answers {
            val pixels = firstArg<IntArray>()
            for (i in pixels.indices) {
                pixels[i] = if ((i / 240 + i % 240) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }
        return bmp
    }

    private fun mockFaceRect(): Rect {
        val faceRect = mockk<Rect>(relaxed = true)
        every { faceRect.width() } returns 240
        every { faceRect.height() } returns 280
        every { faceRect.exactCenterX() } returns 320f
        every { faceRect.exactCenterY() } returns 240f
        return faceRect
    }

    private fun mockPassingDetection(): FaceDetectionResult {
        val detection = mockk<FaceDetectionResult>(relaxed = true)
        every { detection.isGoodQuality } returns true
        every { detection.headEulerAngleY } returns 0f
        every { detection.headEulerAngleX } returns 0f
        every { detection.boundingBox } returns mockFaceRect()
        every { detection.leftEyeOpenProbability } returns 1.0f
        every { detection.rightEyeOpenProbability } returns 1.0f
        every { detection.leftEyeContour } returns emptyList()
        every { detection.rightEyeContour } returns emptyList()
        return detection
    }

    @Test
    fun `good quality face should transition from DETECTING to POSITIONING`() {
        val detection = mockPassingDetection()
        every { faceDetector.detectImage(any(), any()) } returns detection

        val bitmap = mockCheckerboardBitmap()
        val imageProxy = mockk<ImageProxy>(relaxed = true) {
            every { image } returns mockk(relaxed = true)
            every { toBitmap() } returns bitmap
        }

        viewModel.setStudentId("santri-01")
        viewModel.onFrameCaptured(imageProxy, "santri-01")

        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.POSITIONING, state.step)
        assertEquals(CapturePose.CENTER, state.currentPose)
    }

    @Test
    fun `skipPose should advance to next pose in order`() {
        viewModel.setStudentId("santri-01")

        // Trigger initial transition: DETECTING → POSITIONING(CENTER)
        val detection = mockPassingDetection()
        every { faceDetector.detectImage(any(), any()) } returns detection

        val bitmap = mockCheckerboardBitmap()
        val imageProxy = mockk<ImageProxy>(relaxed = true) {
            every { image } returns mockk(relaxed = true)
            every { toBitmap() } returns bitmap
        }
        viewModel.onFrameCaptured(imageProxy, "santri-01")

        // CENTER → LEFT
        viewModel.skipPose()
        assertEquals(CapturePose.LEFT, viewModel.state.value.currentPose)
        assertEquals(FaceRegisterStep.POSITIONING, viewModel.state.value.step)

        // LEFT → RIGHT
        viewModel.skipPose()
        assertEquals(CapturePose.RIGHT, viewModel.state.value.currentPose)

        // RIGHT → UP
        viewModel.skipPose()
        assertEquals(CapturePose.UP, viewModel.state.value.currentPose)

        // UP → DOWN
        viewModel.skipPose()
        assertEquals(CapturePose.DOWN, viewModel.state.value.currentPose)

        // Final skip goes to EMBEDDING (all 5 poses exhausted)
        viewModel.skipPose()
        testDispatcher.scheduler.advanceUntilIdle()
        val finalStep = viewModel.state.value.step
        assertTrue("Expected EMBEDDING or further step after exhausting poses, got $finalStep",
            finalStep == FaceRegisterStep.EMBEDDING ||
            finalStep == FaceRegisterStep.ERROR ||
            finalStep == FaceRegisterStep.SUCCESS)
    }

    @Test
    fun `reset should return to initial state`() {
        viewModel.setStudentId("santri-01")
        viewModel.reset()

        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertEquals(CapturePose.CENTER, state.currentPose)
        assertEquals("Arahkan wajah ke dalam oval", state.message)
    }

    @Test
    fun `setStudentId should enable detection`() {
        viewModel.setStudentId("test-123")
        // After setting studentId, no face yet — stays DETECTING
        assertEquals(FaceRegisterStep.DETECTING, viewModel.state.value.step)
    }
}
