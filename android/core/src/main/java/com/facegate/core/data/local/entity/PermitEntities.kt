package com.facegate.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * #135: Izin mandiri & kelompok — salinan lokal dari server utk tampil di kiosk.
 * Full-replace saat sync (pola sama dgn campus_rules).
 */
@Entity(tableName = "permits")
data class PermitEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val startDate: Long,
    val endDate: Long,
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String,
    val reason: String? = null,
    val note: String? = null,
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * #135: Anggota izin (mandiri = 1 baris, kelompok = N baris).
 * Menyimpan nama/nim agar list kiosk bisa tampil tanpa join students.
 */
@Entity(tableName = "permit_members", primaryKeys = ["permitId", "studentId"])
data class PermitMemberEntity(
    val permitId: String,
    val studentId: String,
    val name: String,
    val nim: String,
    val keluarVerifiedAt: Long? = null,
    val kembaliVerifiedAt: Long? = null
)

/**
 * #135: Antrean pengajuan izin offline — disimpan sementara sampai internet
 * kembali, lalu di-upload ke POST /api/kiosk/permits.
 */
@Entity(tableName = "permit_requests")
data class PermitRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val clientId: String,
    val memberIds: String,
    val startDate: String,
    val endDate: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

/**
 * #135: Antrean verifikasi scan keluar/kembali terhadap izin — dibuat saat
 * kiosk offline, di-upload via POST /api/sync/permits-verifications.
 */
@Entity(tableName = "permit_verifications")
data class PermitVerificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val permitId: String,
    val studentId: String,
    val studentName: String,
    val confidenceScore: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val clientId: String,
    val deviceId: String? = null,
    val isSynced: Boolean = false
)