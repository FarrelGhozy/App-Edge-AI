package com.facegate.adminapp.students

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ImportCsvState(
    val result: String? = null
)

@HiltViewModel
class ImportCsvViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCsvState())
    val uiState: StateFlow<ImportCsvState> = _uiState.asStateFlow()

    fun pickFile() {
        _uiState.value = ImportCsvState(result = "Fitur import CSV akan diimplementasikan")
    }
}
