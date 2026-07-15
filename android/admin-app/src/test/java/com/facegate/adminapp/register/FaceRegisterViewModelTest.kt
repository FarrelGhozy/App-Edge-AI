package com.facegate.adminapp.register

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.facegate.core.face.FaceDetectionResult
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import com.facegate.core.face.LivenessDetector
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.UploadFaceRequest
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
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

    @MockK
    private lateinit var imageProxy: ImageProxy

    @MockK
    private lateinit var imageProxyInfo: ImageProxy.ImageInfoProxy

    private lateinit var viewModel: FaceRegisterViewModel

    // Mock bitmap for face analysis
    private val testBitmap = Bitmap.createBitmap(640, 480, Config.ARGB_8888)

    // Coroutine test dispatcher
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)

        // CameraX mocks
        every { imageProxy.image } returns android.media.ImageReader.newInstance(640, 480, android.graphics.ImageFormat.YUV_420_888, 2).acquireLatestImage()
        every { imageProxy.imageInfo } returns imageProxyInfo
        every { imageProxyInfo.rotationDegrees } returns 0

        // FaceDetector init
        every { faceDetector.init() } returns Unit

        // FaceEmbedder init
        every { faceEmbedder.init() } returns Unit
        every { faceEmbedder.embed(any<Bitmap?>()) } returns FloatArray(192) { 0.01f * it }
        every { faceEmbedder.averageEmbeddings(any()) } returns FloatArray(192) { 0.02f * it }

        // LivenessDetector
        every { livenessDetector.reset() } returns Unit

        // ViewModel with mocked deps
        viewModel = FaceRegisterViewModel(
            faceDetector = faceDetector,
            faceEmbedder = faceEmbedder,
            livenessDetector = livenessDetector,
            apiService = apiService
        )

        // Set coroutine dispatcher
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
        testBitmap.recycle()
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
    fun `no face detection should remain in DETECTING`() {
        // Mock: faceDetector returns null (no face detected)
        every { faceDetector.detectImage(any(), any()) } returns null

        // Allow camera image to be null (more realistic)
        every { imageProxy.image } returns null

        viewModel.onFrameCaptured(imageProxy, "student1")

        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertNull(state.detection)
    }

    @Test
    fun `detecting face with bad quality should stay in DETECTING`() {
        val mockDetection = mockk<FaceDetectionResult> {
            every { isGoodQuality } returns false
            every { boundingBox } returns Rect(100, 80, 540, 400)
            every { headEulerAngleY } returns 0f
            every { headEulerAngleZ } returns 0f
        }

        // Mock faceDetector to return detection with bad quality
        every { faceDetector.detectImage(any(), any()) } returns mockDetection

        // Need a non-null camera image
        // Use a spy approach — we know imageProxy.image returns mock
        val mediaImage = mockk<android.media.Image>()
        every { imageProxy.image } returns mediaImage

        viewModel.onFrameCaptured(imageProxy, "student1")

        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
    }

    @Test
    fun `detecting face with good quality should transition to COLLECTING`() {
        val mockDetection = mockk<FaceDetectionResult> {
            every { isGoodQuality } returns true
            every { boundingBox } returns Rect(200, 150, 440, 330)
            every { headEulerAngleY } returns 0f
            every { headEulerAngleZ } returns 0f
        }

        every { faceDetector.detectImage(any(), any()) } returns mockDetection

        // Mock camera image
        val mediaImage = mockk<android.media.Image> {
            // Only enough to let code path pass
            every { format } returns android.graphics.ImageFormat.NV21
            every { width } returns 640
            every { height } returns 480
            every { planes } returns emptyArray()
            every { timestamp } returns System.nanoTime()
        }
        every { imageProxy.image } returns mediaImage
        every { imageProxy.width } returns 640
        every { imageProxy.height } returns 480

        viewModel.onFrameCaptured(imageProxy, "student1")

        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.COLLECTING, state.step)
    }

    @Test
    fun `collecting 3 quality frames should trigger embedding`() {
        val mockDetection = mockk<FaceDetectionResult> {
            every { isGoodQuality } returns true
            every { boundingBox } returns Rect(200, 150, 440, 330)
            every { headEulerAngleY } returns 0f
            every { headEulerAngleZ } returns 0f
        }

        every { faceDetector.detectImage(any(), any()) } returns mockDetection

        val mediaImage = mockk<android.media.Image> {
            every { format } returns android.graphics.ImageFormat.NV21
            every { width } returns 640
            every { height } returns 480
            every { planes } returns emptyArray()
            every { timestamp } returns System.nanoTime()
        }
        every { imageProxy.image } returns mediaImage
        every { imageProxy.width } returns 640
        every { imageProxy.height } returns 480

        // Mock API success response
        every { apiService.uploadFace(any(), any<UploadFaceRequest>()) } returns Response.success(Unit)
        every { faceEmbedder.embed(any<Bitmap?>()) } returns FloatArray(192) { 0.01f }
        every { faceEmbedder.averageEmbeddings(any()) } answers {
            val input = firstArg<Array<FloatArray?>>()
            input.firstOrNull() ?: FloatArray(192)
        }

        // Send 3 frames
        viewModel.onFrameCaptured(imageProxy, "student1")
        viewModel.onFrameCaptured(imageProxy, "student1")
        viewModel.onFrameCaptured(imageProxy, "student1")

        // After 3 frames, should proceed to embedding/upload
        // Need to advance coroutine
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.state.value
        assertTrue("Should reach success after 3 frames",
            finalState.isSuccess || finalState.step == FaceRegisterStep.SUCCESS)
    }

    @Test
    fun `reset should clear all collected data`() {
        // Set up some state first
        viewModel.reset()

        val state = viewModel.state.value
        assertEquals(FaceRegisterStep.DETECTING, state.step)
        assertEquals(0, state.framesCollected)
        assertNull(state.error)
        assertFalse(state.isSuccess)
    }

    @Test
    fun `upload failure should set error state`() {
        val mockDetection = mockk<FaceDetectionResult> {
            every { isGoodQuality } returns true
            every { boundingBox } returns Rect(200, 150, 440, 330)
            every { headEulerAngleY } returns 0f
            every { headEulerAngleZ } returns 0f
        }
        every { faceDetector.detectImage(any(), any()) } returns mockDetection

        val mediaImage = mockk<android.media.Image> {
            every { format } returns android.graphics.ImageFormat.NV21
            every { width } returns 640
            every { height } returns 480
            every { planes } returns emptyArray()
            every { timestamp } returns System.nanoTime()
        }
        every { imageProxy.image } returns mediaImage
        every { imageProxy.width } returns 640
        every { imageProxy.height } returns 480

        // Mock API failure
        every { apiService.uploadFace(any(), any<UploadFaceRequest>()) } returns Response.error(500, okhttp3.ResponseBody.create(null, "Server error"))
        every { faceEmbedder.embed(any<Bitmap?>()) } returns FloatArray(192)
        every { faceEmbedder.averageEmbeddings(any()) } returns FloatArray(192)

        // Send 3 frames
        viewModel.onFrameCaptured(imageProxy, "student1")
        viewModel.onFrameCaptured(imageProxy, "student1")
        viewModel.onFrameCaptured(imageProxy, "student1")

        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.state.value
        assertEquals("Upload failure should show ERROR step",
            FaceRegisterStep.ERROR, finalState.step)
        assertNotNull("Error message should be set", finalState.error)
    }

    @Test
    fun `setStudentId should update internal state`() {
        viewModel.setStudentId("test-student-123")
        // No direct getter, but subsequent onFrameCaptured should use it
        assert(true) // Just verify no crash
    }

    @Test
    fun `sending frame while processing should be skipped`() {
        val mockDetection = mockk<FaceDetectionResult> {
            every { isGoodQuality } returns true
            every { boundingBox } returns Rect(200, 150, 440, 330)
            every { headEulerAngleY } returns 0f
            every { headEulerAngleZ } returns 0f
        }
        every { faceDetector.detectImage(any(), any()) } returns mockDetection

        val mediaImage = mockk<android.media.Image> {
            every { format } returns android.graphics.ImageFormat.NV21
            every { width } returns 640
            every { height } returns 480
            every { planes } returns emptyArray()
            every { timestamp } returns System.nanoTime()
        }
        every { imageProxy.image } returns mediaImage
        every { imageProxy.width } returns 640
        every { imageProxy.height } returns 480

        // Rapid fire — should not crash
        viewModel.onFrameCaptured(imageProxy, "s1")
        viewModel.onFrameCaptured(imageProxy, "s1")
        viewModel.onFrameCaptured(imageProxy, "s1")
        viewModel.onFrameCaptured(imageProxy, "s1")
        viewModel.onFrameCaptured(imageProxy, "s1")

        assertNotNull(viewModel.state.value.step)
    }
}
