package com.facegate.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.facegate.core.data.local.entity.PermitEntity
import com.facegate.core.data.local.entity.PermitMemberEntity
import com.facegate.core.data.local.entity.PermitRequestEntity
import com.facegate.core.data.local.entity.PermitVerificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PermitDao {

    @Query("SELECT * FROM permits ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PermitEntity>>

    @Query("SELECT * FROM permits WHERE id = :permitId")
    suspend fun getById(permitId: String): PermitEntity?

    @Query("SELECT * FROM permits WHERE id = :permitId")
    fun observeById(permitId: String): Flow<PermitEntity?>

    @Query("SELECT * FROM permit_members")
    fun observeAllMembers(): Flow<List<PermitMemberEntity>>

    @Query("SELECT * FROM permit_members WHERE permitId = :permitId")
    fun observeMembersByPermit(permitId: String): Flow<List<PermitMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPermits(permits: List<PermitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMembers(members: List<PermitMemberEntity>)

    /** Hapus semua izin (full-replace sync) + semua anggota terkait. */
    @Query("DELETE FROM permit_members")
    suspend fun clearMembers()

    @Query("DELETE FROM permits")
    suspend fun clearPermits()

    @Query("SELECT * FROM permit_members WHERE permitId = :permitId")
    suspend fun getMembers(permitId: String): List<PermitMemberEntity>

    @Query("SELECT * FROM permit_members WHERE studentId = :studentId")
    suspend fun getMembersByStudent(studentId: String): List<PermitMemberEntity>

    /** Update status verifikasi anggota setelah hasil sync/verify dari server. */
    @Query(
        """
        UPDATE permit_members
        SET keluarVerifiedAt = COALESCE(:keluarAt, keluarVerifiedAt),
            kembaliVerifiedAt = COALESCE(:kembaliAt, kembaliVerifiedAt)
        WHERE permitId = :permitId AND studentId = :studentId
        """
    )
    suspend fun updateVerification(permitId: String, studentId: String, keluarAt: Long?, kembaliAt: Long?)

    // =========== QUEUE: pengajuan izin offline ===========
    @Query("SELECT * FROM permit_requests WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedRequests(): List<PermitRequestEntity>

    @Query("UPDATE permit_requests SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markRequestsSynced(ids: List<Long>)

    @Query("DELETE FROM permit_requests WHERE id IN (:ids)")
    suspend fun deleteRequests(ids: List<Long>)

    @Insert
    suspend fun insertRequest(request: PermitRequestEntity): Long

    @Query("SELECT COUNT(*) FROM permit_requests WHERE isSynced = 0")
    fun observeUnsyncedRequestCount(): Flow<Int>

    // =========== ANTRIAN: verifikasi scan izin ===========
    @Query("SELECT * FROM permit_verifications WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedVerifications(): List<PermitVerificationEntity>

    @Query("UPDATE permit_verifications SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markVerificationsSynced(ids: List<Long>)

    @Query("DELETE FROM permit_verifications WHERE id IN (:ids)")
    suspend fun deleteVerifications(ids: List<Long>)

    @Insert
    suspend fun insertVerification(verification: PermitVerificationEntity): Long

    @Query("SELECT * FROM permit_verifications")
    suspend fun getAllVerifications(): List<PermitVerificationEntity>

    @Query("SELECT COUNT(*) FROM permit_verifications WHERE isSynced = 0")
    fun observeUnsyncedVerificationCount(): Flow<Int>
}