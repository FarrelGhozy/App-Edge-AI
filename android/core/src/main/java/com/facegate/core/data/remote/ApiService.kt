package com.facegate.core.data.remote

import com.facegate.core.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<RefreshResponse>

    @GET("api/students")
    suspend fun getStudents(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("search") search: String? = null,
        @Query("studyProgram") studyProgram: String? = null,
        @Query("academicYear") academicYear: String? = null
    ): Response<StudentListResponse>

    @GET("api/students/{id}")
    suspend fun getStudent(@Path("id") id: String): Response<StudentDto>

    @POST("api/students")
    suspend fun createStudent(@Body request: CreateStudentRequest): Response<StudentDto>

    @PUT("api/students/{id}")
    suspend fun updateStudent(
        @Path("id") id: String,
        @Body request: CreateStudentRequest
    ): Response<StudentDto>

    @DELETE("api/students/{id}")
    suspend fun deleteStudent(@Path("id") id: String): Response<Unit>

    @POST("api/students/{id}/face")
    suspend fun uploadFace(
        @Path("id") id: String,
        @Body request: UploadFaceRequest
    ): Response<Unit>

    @POST("api/attendance/scan")
    suspend fun scanAttendance(@Body request: ScanRequest): Response<Unit>

    @GET("api/attendance")
    suspend fun getAttendanceLogs(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("studentId") studentId: String? = null,
        @Query("startDate") startDate: String? = null,
        @Query("endDate") endDate: String? = null
    ): Response<AttendanceListResponse>

    @POST("api/sync/attendance")
    suspend fun syncAttendance(@Body request: AttendanceBatchRequest): Response<Unit>

    @GET("api/sync/faces")
    suspend fun syncFaces(@Query("since") since: String? = null): Response<FaceSyncResponse>

    @GET("api/sync/rules")
    suspend fun syncRules(): Response<List<CampusRuleDto>>

    @GET("api/sync/requested")
    suspend fun checkSyncRequested(): Response<SyncRequestResponse>

    @POST("api/sync/complete")
    suspend fun completeSync(@Body request: SyncCompleteRequest): Response<Unit>

    @GET("api/rules")
    suspend fun getRules(): Response<List<CampusRuleDto>>

    @GET("api/settings")
    suspend fun getSettings(): Response<Map<String, String>>

    @POST("api/devices/register")
    suspend fun registerDevice(@Body request: Map<String, String>): Response<Map<String, String>>

    @PUT("api/devices/{deviceId}/ping")
    suspend fun pingDevice(@Path("deviceId") deviceId: String): Response<Unit>
}
