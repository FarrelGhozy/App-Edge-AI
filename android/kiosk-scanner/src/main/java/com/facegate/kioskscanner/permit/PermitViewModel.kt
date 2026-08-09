package com.facegate.kioskscanner.permit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.core.data.local.DevicePreferences
import com.facegate.core.data.local.dao.FaceVectorDao
import com.facegate.core.data.local.dao.PermitDao
import com.facegate.core.data.local.dao.StudentDao
import com.facegate.core.data.local.entity.PermitEntity
import com.facegate.core.data.local.entity.PermitMemberEntity
import com.facegate.core.data.local.entity.PermitRequestEntity
import com.facegate.core.data.local.entity.PermitVerificationEntity
import com.facegate.core.data.local.entity.StudentEntity
import com.facegate.core.data.remote.ApiService
import com.facegate.core.data.remote.dto.CreateKioskPermitRequest
import com.facegate.core.data.remote.dto.PermitDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * #135: Status verifikasi anggota izin — KELUAR (belum keluar), KEMBALI
 * (sudah keluar, belum kembali), SELESAI (keluar + kembali sudah).
 */
enum class PermitMemberPhase { KELUAR, KEMBALI, SELESAI }

data class VerifyTarget(
    val permitId: String,
    val studentId: String,
    val studentName: String,
    val phase: PermitMemberPhase,
    val permitNote: String? = null
)

data class PermitListItem(
    val permit: PermitEntity,
    val members: List<PermitMemberEntity>
)

sealed interface PermitSubmitState {
    data object Idle : PermitSubmitState
    data object Sending : PermitSubmitState
    data class Success(val message: String, val queuedOffline: Boolean) : PermitSubmitState
    data class Failed(val message: String) : PermitSubmitState
}

@HiltViewModel
class PermitViewModel @Inject constructor(
    private val permitDao: PermitDao,
    private val studentDao: StudentDao,
    private val faceVectorDao: FaceVectorDao,
    private val apiService: ApiService,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    /** Semua izin + anggota (observable). */
    val permitList: Flow<List<PermitListItem>> = combine(
        permitDao.observeAll(),
        permitDao.observeAllMembers()
    ) { permits, members ->
        permits.map { p ->
            PermitListItem(p, members.filter { it.permitId == p.id })
        }
    }

    /** Banyaknya pengajuan yang belum terkirim (offline queue). */
    val unsyncedRequestCount: Flow<Int> = permitDao.observeUnsyncedRequestCount()

    private val _submitState = MutableStateFlow<PermitSubmitState>(PermitSubmitState.Idle)
    val submitState: StateFlow<PermitSubmitState> = _submitState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StudentEntity>>(emptyList())
    val searchResults: StateFlow<List<StudentEntity>> = _searchResults.asStateFlow()

    /** Cari mahasiswa aktif lokal (nama/NIM) — form izin kiosk. */
    suspend fun searchStudents(query: String): List<StudentEntity> {
        if (query.isBlank()) return emptyList()
        return studentDao.search(query.trim())
    }

    /** Cek apakah mahasiswa punya face vector lokal (syarat bisa verifikasi scan). */
    suspend fun hasFace(studentId: String): Boolean {
        return faceVectorDao.getByStudentId(studentId).isNotEmpty()
    }

    /**
     * #135: Submit pengajuan izin mandiri/kelompok.
     * Online → POST langsung. Offline → antre lokal, SyncWorker upload saat
     * koneksi kembali, dan UI diberi tahu bahwa izin akan dikirim nanti.
     */
    fun submitPermit(
        memberIds: List<String>,
        startDate: String,
        endDate: String,
        startTime: String?,
        endTime: String?,
        reason: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _submitState.value = PermitSubmitState.Sending
            val clientId = UUID.randomUUID().toString()
            try {
                val request = CreateKioskPermitRequest(
                    memberIds = memberIds,
                    startDate = startDate,
                    endDate = endDate,
                    startTime = startTime,
                    endTime = endTime,
                    reason = reason,
                    clientId = clientId
                )
                val response = apiService.createKioskPermit(request)
                if (response.isSuccessful && response.body()?.data != null) {
                    insertLocalPermit(response.body()!!.data!!)
                    _submitState.value = PermitSubmitState.Success(
                        message = "Izin berhasil diajukan, menunggu persetujuan admin",
                        queuedOffline = false
                    )
                } else {
                    queueOffline(clientId, memberIds, startDate, endDate, startTime, endTime, reason)
                }
            } catch (e: Exception) {
                // Jaringan error → jangan gagal di UI; masuk antrean offline.
                queueOffline(clientId, memberIds, startDate, endDate, startTime, endTime, reason)
            }
            onDone()
        }
    }

    /** Simpan antrean offline + update state. */
    private suspend fun queueOffline(
        clientId: String,
        memberIds: List<String>,
        startDate: String,
        endDate: String,
        startTime: String?,
        endTime: String?,
        reason: String?
    ) {
        permitDao.insertRequest(
            PermitRequestEntity(
                clientId = clientId,
                memberIds = memberIds.joinToString(","),
                startDate = startDate,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                reason = reason,
                createdAt = System.currentTimeMillis()
            )
        )
        _submitState.value = PermitSubmitState.Success(
            message = "Tidak ada koneksi — izin tersimpan, akan dikirim otomatis saat internet tersedia",
            queuedOffline = true
        )
    }

    /** Simpan hasil create dari server ke tabel lokal supaya langsung tampil. */
    private suspend fun insertLocalPermit(dto: PermitDto) {
        val entity = PermitEntity(
            id = dto.id,
            type = dto.type,
            startDate = dto.startDate.toEpochMillisOrNow(),
            endDate = dto.endDate.toEpochMillisOrNow(),
            startTime = dto.startTime,
            endTime = dto.endTime,
            status = dto.status,
            reason = dto.reason,
            note = dto.note,
            rejectionReason = dto.rejectionReason,
            createdAt = dto.createdAt?.toEpochMillisOrNow() ?: System.currentTimeMillis()
        )
        val members = dto.members.map { m ->
            PermitMemberEntity(
                permitId = dto.id,
                studentId = m.studentId,
                name = m.student?.name ?: "",
                nim = m.student?.nim ?: "",
                keluarVerifiedAt = m.keluarVerifiedAt?.toEpochMillisOrNull(),
                kembaliVerifiedAt = m.kembaliVerifiedAt?.toEpochMillisOrNull()
            )
        }
        permitDao.insertAllPermits(listOf(entity))
        if (members.isNotEmpty()) permitDao.insertAllMembers(members)
    }

    /**
     * #135: Verifikasi scan keluar/kembali terhadap izin — menyimpan antrean
     * ke Room (idempotency clientId + `akun` server match). Upload terjadi via
     * SyncWorker / SyncManager ke POST /api/sync/permits-verifications.
     */
    fun queueVerification(
        permitId: String,
        studentId: String,
        studentName: String,
        confidenceScore: Float,
        phase: PermitMemberPhase
    ) {
        viewModelScope.launch {
            val deviceId = devicePreferences.getDeviceId()
            permitDao.insertVerification(
                PermitVerificationEntity(
                    permitId = permitId,
                    studentId = studentId,
                    studentName = studentName,
                    confidenceScore = confidenceScore,
                    timestamp = System.currentTimeMillis(),
                    clientId = UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    isSynced = false
                )
            )
            // Optimistic update lokal — server akan mengonfirmasi saat upload.
            val now = System.currentTimeMillis()
            permitDao.updateVerification(
                permitId = permitId,
                studentId = studentId,
                keluarAt = if (phase == PermitMemberPhase.KELUAR) now else null,
                kembaliAt = if (phase == PermitMemberPhase.KEMBALI) now else null
            )
        }
    }

    fun resetSubmitState() {
        _submitState.value = PermitSubmitState.Idle
    }

    private fun String.toEpochMillisOrNow(): Long = toEpochMillisOrNull() ?: System.currentTimeMillis()

    private fun String.toEpochMillisOrNull(): Long? =
        try {
            java.time.Instant.parse(this).toEpochMilli()
        } catch (_: Exception) { null }
}