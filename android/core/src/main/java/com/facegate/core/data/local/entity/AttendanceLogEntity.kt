package com.facegate.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_logs")
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: String,
    val studentName: String,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidenceScore: Float = 0f,
    val isViolation: Boolean = false,
    val violationType: String? = null,
    val deviceId: String? = null,
    val photoCapture: String? = null,
    // #135: terkait izin mandiri/kelompok — diisi hanya utk scan verifikasi izin.
    @ColumnInfo(name = "permit_id")
    val permitId: String? = null,
    @ColumnInfo(name = "permit_member_id")
    val permitMemberId: String? = null,
    // #119: idempotency key — UUID unik per log offline, dikirim ke server saat
    // batch sync. Retry tidak membuat duplikat (server dedup by clientId).
    // Nama kolom eksplisit `client_id` — HARUS konsisten dengan MIGRATION_2_3
    // (ALTER TABLE ADD COLUMN client_id). Tanpa @ColumnInfo, Room men-deduce
    // nama = `clientId` (nama field), menyebabkan validasi migrasi gagal:
    // "Migration didn't properly handle: attendance_logs → client_id vs clientId".
    @ColumnInfo(name = "client_id")
    val clientId: String? = null,
    val isSynced: Boolean = false
)
