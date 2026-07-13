package com.facegate.adminapp.holiday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CreateHolidayRequest
import com.facegate.core.data.remote.dto.UpdateHolidayRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HolidayFormState(
    val name: String = "",
    val date: String = "",
    val type: String = "national",
    val description: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HolidayFormViewModel @Inject constructor(
    private val apiService: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HolidayFormState())
    val uiState: StateFlow<HolidayFormState> = _uiState.asStateFlow()

    private var editingId: String? = null

    fun load(holidayId: String) {
        editingId = holidayId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val response = apiService.getHolidays()
                if (response.isSuccessful && response.body() != null) {
                    val holiday = response.body()!!.find { it.id == holidayId }
                    if (holiday != null) {
                        _uiState.value = HolidayFormState(
                            name = holiday.name,
                            date = holiday.date,
                            type = holiday.type,
                            description = holiday.description.orEmpty(),
                            isLoading = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Data tidak ditemukan")
                    }
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Gagal memuat data")
            }
        }
    }

    fun updateName(value: String) { _uiState.value = _uiState.value.copy(name = value) }
    fun updateDate(value: String) { _uiState.value = _uiState.value.copy(date = value) }
    fun updateType(value: String) { _uiState.value = _uiState.value.copy(type = value) }
    fun updateDescription(value: String) { _uiState.value = _uiState.value.copy(description = value) }

    fun save(onSuccess: () -> Unit) {
        val s = _uiState.value
        if (s.name.isBlank() || s.date.isBlank()) {
            _uiState.value = s.copy(error = "Nama dan tanggal harus diisi")
            return
        }

        _uiState.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val id = editingId
                if (id != null) {
                    val response = apiService.updateHoliday(
                        id = id,
                        request = UpdateHolidayRequest(
                            name = s.name,
                            date = s.date,
                            type = s.type,
                            description = s.description.ifBlank { null }
                        )
                    )
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        _uiState.value = _uiState.value.copy(isSaving = false, error = "Gagal menyimpan")
                    }
                } else {
                    val response = apiService.createHoliday(
                        request = CreateHolidayRequest(
                            name = s.name,
                            date = s.date,
                            type = s.type,
                            description = s.description.ifBlank { null }
                        )
                    )
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        _uiState.value = _uiState.value.copy(isSaving = false, error = "Gagal menyimpan")
                    }
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Gagal terhubung ke server")
            }
        }
    }
}
