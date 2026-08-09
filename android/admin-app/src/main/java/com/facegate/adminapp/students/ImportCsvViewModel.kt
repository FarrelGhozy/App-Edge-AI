package com.facegate.adminapp.students

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CreateStudentRequest
import com.facegate.core.data.remote.dto.ImportStudentRequest
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

                // #99: SATU request batch untuk ratusan baris (sebelumnya
                // 1 HTTP request/baris + tiap baris memicu sync trigger backend).
                val response = apiService.importStudents(
                    ImportStudentRequest(
                        filename = uri.lastPathSegment ?: "csv",
                        students = students
                    )
                )
                if (response.isSuccessful && response.body() != null) {
                    val res = response.body()!!
                    val detail = if (res.errors.isNotEmpty()) {
                        val contoh = res.errors.take(3).joinToString("; ") { "baris ${it.row}: ${it.error}" }
                        " | contoh error: $contoh"
                    } else ""
                    _uiState.value = ImportCsvState(
                        result = "Berhasil: ${res.successRows}, Gagal: ${res.failedRows} dari ${res.total} data$detail",
                        isError = res.failedRows > 0 && res.successRows == 0
                    )
                } else {
                    _uiState.value = ImportCsvState(
                        result = "Gagal mengimpor data (HTTP ${response.code()})",
                        isError = true
                    )
                }
            } catch (e: Exception) {
                _uiState.value = ImportCsvState(
                    result = "Gagal membaca file: ${e.message}",
                    isError = true
                )
            }
        }
    }

    /**
     * #99: parser CSV proper (RFC 4180) — handle kutip ganda `"a,b",c`
     * dan baris ber-enter di dalam kolom, bukan split(",") naif.
     */
    private fun parseCsv(context: Context, uri: Uri): List<CreateStudentRequest> {
        val result = mutableListOf<CreateStudentRequest>()
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri)))
        reader.use { r ->
            var header = true
            for (fields in readCsvRecords(r)) {
                if (header) { header = false; continue } // skip header
                if (fields.size >= 4) {
                    result.add(
                        CreateStudentRequest(
                            nim = fields[0].trim(),
                            name = fields[1].trim(),
                            studyProgram = fields[2].trim(),
                            academicYear = fields[3].trim(),
                            phone = fields.getOrNull(4)?.trim() ?: "",
                            email = fields.getOrNull(5)?.trim() ?: ""
                        )
                    )
                }
            }
        }
        return result
    }

    /** Baca record CSV dengan dukungan quoted field (RFC 4180). */
    private fun readCsvRecords(reader: BufferedReader): Sequence<List<String>> = sequence {
        var current = StringBuilder()
        val fields = mutableListOf<String>()
        var inQuotes = false

        for (line in reader.lineSequence()) {
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    inQuotes -> {
                        if (ch == '"') {
                            if (i + 1 < line.length && line[i + 1] == '"') {
                                current.append('"'); i += 2; continue
                            }
                            inQuotes = false
                        } else {
                            current.append(ch)
                        }
                    }
                    ch == '"' -> inQuotes = true
                    ch == ',' -> { fields.add(current.toString()); current = StringBuilder() }
                    else -> current.append(ch)
                }
                i++
            }
            if (inQuotes) {
                current.append('\n') // baris lanjutan di dalam kolom ber-quote
            } else {
                fields.add(current.toString())
                yield(fields.toList())
                current = StringBuilder()
                fields.clear()
            }
        }
    }
}
