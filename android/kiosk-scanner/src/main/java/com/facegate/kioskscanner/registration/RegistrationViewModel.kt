package com.facegate.kioskscanner.registration

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CreateStudentRequest
import com.facegate.core.data.remote.dto.UploadFaceRequest
import com.facegate.core.face.FaceDetectorWrapper
import com.facegate.core.face.FaceEmbedder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RegistrationUiState(
    val nim: String = "",
    val name: String = "",
    val studyProgram: String = "IPA",
    val academicYear: String = "",
    val phone: String = "",
    val email: String = "",
    val capturedFace: Bitmap? = null,
    val faceVector: List<Float>? = null,
    val isCapturing: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val apiService: ApiService,
    private val faceDetector: FaceDetectorWrapper,
    private val faceEmbedder: FaceEmbedder
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "RegistrationVM"
    }

    init {
        faceDetector.init()
        faceEmbedder.init()
    }

    fun updateNim(value: String) { _uiState.value = _uiState.value.copy(nim = value) }
    fun updateName(value: String) { _uiState.value = _uiState.value.copy(name = value) }
    fun updateStudyProgram(value: String) { _uiState.value = _uiState.value.copy(studyProgram = value) }
    fun updateAcademicYear(value: String) { _uiState.value = _uiState.value.copy(academicYear = value) }
    fun updatePhone(value: String) { _uiState.value = _uiState.value.copy(phone = value) }
    fun updateEmail(value: String) { _uiState.value = _uiState.value.copy(email = value) }

    /** Capture face from camera bitmap, extract embedding vector. */
    fun captureFace(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCapturing = true, error = null)
            try {
                val result = withContext(Dispatchers.Default) { faceDetector.detectSync(bitmap) }
                if (result == null) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false, error = "Wajah tidak terdeteksi. Coba lagi."
                    )
                    return@launch
                }

                val cropped = withContext(Dispatchers.Default) {
                    val faceRect = result.boundingBox
                    val safeRect = android.graphics.Rect(
                        faceRect.left.coerceAtLeast(0),
                        faceRect.top.coerceAtLeast(0),
                        faceRect.right.coerceAtMost(bitmap.width),
                        faceRect.bottom.coerceAtMost(bitmap.height)
                    )
                    android.graphics.Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top,
                        safeRect.width(), safeRect.height())
                }

                val vector = withContext(Dispatchers.Default) { faceEmbedder.embed(cropped) }
                if (vector == null || vector.size < 128) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false, error = "Gagal generate face vector."
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    capturedFace = cropped,
                    faceVector = vector.toList(),
                    isCapturing = false,
                    error = null
                )
                Log.d(TAG, "Face captured: ${vector.size} dimensions")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false, error = "Error: ${e.message}"
                )
            }
        }
    }

    /** Submit registration to backend. */
    fun submitRegistration() {
        val state = _uiState.value
        if (state.nim.isBlank() || state.name.isBlank() || state.academicYear.isBlank()) {
            _uiState.value = state.copy(error = "NIM, Nama, dan Tahun Ajaran wajib diisi!")
            return
        }
        if (state.faceVector == null) {
            _uiState.value = state.copy(error = "Ambil foto wajah terlebih dahulu!")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
            try {
                // Step 1: Create student
                val createResp = apiService.createStudent(
                    CreateStudentRequest(
                        nim = state.nim,
                        name = state.name,
                        studyProgram = state.studyProgram,
                        academicYear = state.academicYear,
                        phone = state.phone.ifBlank { null },
                        email = state.email.ifBlank { null }
                    )
                )
                if (!createResp.isSuccessful) {
                    val errBody = createResp.errorBody()?.string() ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false, error = "Gagal daftar: $errBody"
                    )
                    return@launch
                }

                val studentId = createResp.body()?.let { dto ->
                    (dto as? com.facegate.core.data.remote.dto.StudentDto)?.id
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false, error = "Gagal dapat ID santri dari respons"
                    )
                    return@launch
                }

                // Step 2: Upload face vector
                val faceResp = apiService.uploadFace(
                    studentId,
                    UploadFaceRequest(pose = "CENTER", vector = state.faceVector!!)
                )
                if (!faceResp.isSuccessful) {
                    Log.w(TAG, "Face upload skipped: ${faceResp.code()}")
                }

                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    successMessage = "✅ ${state.name} berhasil didaftarkan!",
                    nim = "", name = "", academicYear = "", phone = "", email = "",
                    capturedFace = null, faceVector = null
                )
                Log.d(TAG, "Student registered: $studentId - ${state.name}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false, error = "Error: ${e.message}"
                )
            }
        }
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.release()
    }
}
