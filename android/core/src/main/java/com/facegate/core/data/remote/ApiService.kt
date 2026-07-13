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

    // =========== PERMITS ===========
    @GET("api/permits")
    suspend fun getPermits(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null,
        @Query("studentId") studentId: String? = null
    ): Response<PermitListResponse>

    @GET("api/permits/{id}")
    suspend fun getPermit(@Path("id") id: String): Response<ApiResponse<PermitDto>>

    @POST("api/permits")
    suspend fun createPermit(@Body request: CreatePermitRequest): Response<ApiResponse<PermitDto>>

    @PUT("api/permits/{id}/status")
    suspend fun updatePermitStatus(
        @Path("id") id: String,
        @Body request: UpdatePermitStatusRequest
    ): Response<ApiResponse<PermitDto>>

    @GET("api/permits/quota")
    suspend fun getPermitQuota(
        @Query("studentId") studentId: String
    ): Response<ApiResponse<PermitQuotaResponse>>

    // =========== VIOLATIONS ===========
    @GET("api/violations")
    suspend fun getViolations(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("type") type: String? = null,
        @Query("studentId") studentId: String? = null
    ): Response<ViolationListResponse>

    @PUT("api/violations/{id}/resolve")
    suspend fun resolveViolation(
        @Path("id") id: String,
        @Body request: ResolveViolationRequest
    ): Response<ApiResponse<ViolationDto>>

    // =========== NOTIFICATIONS ===========
    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): Response<NotificationListResponse>

    @PUT("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Response<ApiResponse<Unit>>

    @PUT("api/notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<ApiResponse<Unit>>

    // =========== DEVICES ===========
    @GET("api/devices")
    suspend fun getDevices(): Response<List<DeviceDto>>

    @PUT("api/devices/{deviceId}/ping")
    suspend fun pingDeviceWithBattery(
        @Path("deviceId") deviceId: String,
        @Body request: DevicePingRequest
    ): Response<ApiResponse<Unit>>

    // =========== REPORTS ===========
    @GET("api/reports/daily")
    suspend fun getDailyReport(
        @Query("date") date: String? = null
    ): Response<DailyReportResponse>

    @GET("api/reports/monthly")
    suspend fun getMonthlyReport(
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null
    ): Response<MonthlyReportResponse>

    @GET("api/reports/violations")
    suspend fun getViolationReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<ViolationReportResponse>

    @GET("api/reports/outside-now")
    suspend fun getOutsideNow(): Response<OutsideNowResponse>

    // =========== DASHBOARD ===========
    @GET("api/dashboard/summary")
    suspend fun getDashboardSummary(): Response<DashboardSummaryResponse>
}
