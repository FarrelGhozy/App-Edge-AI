package com.facegate.adminapp.students

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CreateStudentRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class ImportCsvState(
    val result: String? = null,
    val isError: Boolean = false,
    val isUploading: Boolean = false
)

@HiltViewModel
class ImportCsvViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCsvState())
    val uiState: StateFlow<ImportCsvState> = _uiState.asStateFlow()

    fun uploadCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportCsvState(isUploading = true)
            try {
                val students = withContext(Dispatchers.IO) {
                    parseCsv(context, uri)
                }

                if (students.isEmpty()) {
                    _uiState.value = ImportCsvState(
                        result = "File kosong atau format salah",
                        isError = true
                    )
                    return@launch
                }

                var success = 0
                var failed = 0
                try {
                    // Use batch import endpoint (backend sudah support)
                    val response = apiService.importStudents(students)
                    if (response.isSuccessful) {
                        val result = response.body()
                        success = result?.data?.success ?: students.size
                        failed = result?.data?.failed ?: 0
                    } else {
                        // Fallback: individual create
                        for (student in students) {
                            try {
                                val r = apiService.createStudent(student)
                                if (r.isSuccessful) success++ else failed++
                            } catch (_: Exception) {
                                failed++
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Individual fallback
                    for (student in students) {
                        try {
                            val r = apiService.createStudent(student)
                            if (r.isSuccessful) success++ else failed++
                        } catch (_: Exception) {
                            failed++
                        }
                    }
                }

                _uiState.value = ImportCsvState(
                    result = "Berhasil: $success, Gagal: $failed dari ${students.size} data"
                )
            } catch (e: Exception) {
                _uiState.value = ImportCsvState(
                    result = "Gagal membaca file: ${e.message}",
                    isError = true
                )
            }
        }
    }

    private fun parseCsv(context: Context, uri: Uri): List<CreateStudentRequest> {
        val result = mutableListOf<CreateStudentRequest>()
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
        reader.use { r ->
            r.readLine() // skip header
            r.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size >= 4) {
                    result.add(
                        CreateStudentRequest(
                            nim = parts[0].trim(),
                            name = parts[1].trim(),
                            studyProgram = parts[2].trim(),
                            academicYear = parts[3].trim(),
                            phone = parts.getOrNull(4)?.trim() ?: "",
                            email = parts.getOrNull(5)?.trim() ?: ""
                        )
                    )
                }
            }
        }
        return result
    }
}
