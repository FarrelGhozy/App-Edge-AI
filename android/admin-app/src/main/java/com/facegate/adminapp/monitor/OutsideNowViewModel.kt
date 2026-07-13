package com.facegate.adminapp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.OutsideStudent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutsideNowState(
    val count: Int = 0,
    val students: List<OutsideStudent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OutsideNowViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutsideNowState())
    val uiState: StateFlow<OutsideNowState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = OutsideNowState(isLoading = true)
            try {
                val response = apiService.getOutsideNow()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _uiState.value = OutsideNowState(
                        count = body.count,
                        students = body.students
                    )
                } else {
                    _uiState.value = OutsideNowState(error = "Gagal memuat data")
                }
            } catch (e: Exception) {
                _uiState.value = OutsideNowState(error = "Gagal terhubung")
            }
        }
    }
}
