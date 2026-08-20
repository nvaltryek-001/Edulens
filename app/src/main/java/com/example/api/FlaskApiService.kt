package com.example.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Query

// ============================================================
// LOGIN
// ============================================================

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val role: String,
    val token: String
)

// ============================================================
// RECOGNITION
// ============================================================

data class RecognitionResponse(
    val success: Boolean,
    val students: List<RecognizedStudent> = emptyList(),
    val attendanceAdded: List<String> = emptyList(),
    val duplicatesSkipped: List<String> = emptyList()
)

data class RecognizedStudent(
    val name: String,
    val studentId: String,
    val usn: String,
    val department: String,
    val attendanceStatus: String
)

// ============================================================
// ENROLLMENT
// ============================================================

data class EnrollmentResponse(
    val success: Boolean,
    val message: String,
    val studentId: String? = null,
    val name: String? = null,
    val embeddingCount: Int? = null,
    val embeddingsStored: Int? = null
)

// ============================================================
// SYNC
// ============================================================

data class SyncRequest(
    val attendanceRecords: List<Map<String, String>>
)

data class SyncResponse(
    val success: Boolean,
    val message: String
)

// ============================================================
// ATTENDANCE REPORT
// ============================================================

data class AttendanceReportResponse(
    val success: Boolean,
    val filters: ReportFilters? = null,
    val reports: List<AttendanceReportItem> = emptyList(),
    val summary: AttendanceSummary? = null
)

data class ReportFilters(
    val date: String? = null,
    val department: String? = null,
    val semester: String? = null,
    val section: String? = null,
    val subject: String? = null,
    val faculty: String? = null
)

data class AttendanceSummary(
    val totalRecords: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val attendancePercentage: Double = 0.0
)

data class AttendanceReportItem(
    val attendance_id: Int,
    val student_id: String,
    val date: String,
    val time: String,
    val status: String,
    val subject: String,
    val faculty: String,
    val name: String,
    val usn: String,
    val department: String,
    val semester: String,
    val section: String
)

// ============================================================
// ANALYTICS
// ============================================================

data class AnalyticsResponse(
    val success: Boolean,
    val date: String? = null,
    val filters: AnalyticsFilters? = null,
    val overview: AnalyticsOverview = AnalyticsOverview(),
    val departmentStats: List<DepartmentStat> = emptyList(),
    val subjectStats: List<SubjectStat> = emptyList(),
    val recentAttendance: List<RecentAttendance> = emptyList()
)

data class AnalyticsFilters(
    val department: String? = null,
    val semester: String? = null,
    val section: String? = null,
    val subject: String? = null
)

data class AnalyticsOverview(
    val totalStudents: Int = 0,
    val presentToday: Int = 0,
    val absentToday: Int = 0,
    val attendancePercentage: Double = 0.0
)

data class DepartmentStat(
    val department: String? = null,
    val totalRecords: Int = 0,
    val present: Int = 0,
    val attendancePercentage: Double = 0.0
)

data class SubjectStat(
    val subject: String? = null,
    val totalRecords: Int = 0,
    val present: Int = 0,
    val attendancePercentage: Double = 0.0
)

data class RecentAttendance(
    val attendance_id: Int = 0,
    val student_id: String = "",
    val name: String = "",
    val department: String? = null,
    val semester: String? = null,
    val section: String? = null,
    val date: String = "",
    val time: String = "",
    val status: String = "",
    val subject: String = "",
    val faculty: String = ""
)

// ============================================================
// A2 — WEEKLY SUMMARY
// ============================================================

data class WeeklySummaryResponse(
    val success: Boolean,
    val filters: WeeklySummaryFilters? = null,
    val days: List<WeeklySummaryDay> = emptyList()
)

data class WeeklySummaryFilters(
    val department: String? = null,
    val semester: String? = null,
    val section: String? = null,
    val subject: String? = null
)

data class WeeklySummaryDay(
    val date: String = "",
    val day: String = "",
    val total: Int = 0,
    val present: Int = 0,
    val absent: Int = 0,
    val attendancePercentage: Double = 0.0
)

// ============================================================
// A5 — LOW ATTENDANCE
// ============================================================

data class LowAttendanceResponse(
    val success: Boolean,
    val threshold: Double = 75.0,
    val filters: LowAttendanceFilters? = null,
    val students: List<LowAttendanceStudent> = emptyList()
)

data class LowAttendanceFilters(
    val department: String? = null,
    val semester: String? = null,
    val section: String? = null,
    val subject: String? = null
)

data class LowAttendanceStudent(
    val studentId: String = "",
    val name: String = "",
    val usn: String = "",
    val department: String = "",
    val semester: String = "",
    val section: String = "",
    val totalClasses: Int = 0,
    val presentClasses: Int = 0,
    val absentClasses: Int = 0,
    val attendancePercentage: Double = 0.0
)

// ============================================================
// A6 — USER PROFILE
// ============================================================

data class ProfileResponse(
    val success: Boolean,
    val profile: UserProfile? = null,
    val error: String? = null
)

data class UserProfile(
    val userId: String = "",
    val username: String = "",
    val role: String = "",
    val status: String = ""
)

// ============================================================
// A8 — ATTENDANCE SESSION
// ============================================================

data class StartSessionRequest(
    val subject: String,
    val faculty: String,
    val department: String,
    val semester: String,
    val section: String
)

data class SessionInfo(
    val sessionId: Int = 0,
    val subject: String = "",
    val faculty: String = "",
    val department: String = "",
    val semester: String = "",
    val section: String = "",
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String = ""
)

data class SessionResponse(
    val success: Boolean = false,
    val message: String = "",
    val session: SessionInfo? = null
)

data class SessionListResponse(
    val success: Boolean = false,
    val count: Int = 0,
    val sessions: List<SessionInfo> = emptyList()
)
// ============================================================
// FLASK API SERVICE
// ============================================================

interface FlaskApiService {

    // ========================================================
    // LOGIN
    // ========================================================

    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    // ========================================================
    // STUDENT FACE ENROLLMENT
    // ========================================================

    @Multipart
    @POST("api/enroll")
    suspend fun enrollStudent(
        @Header("Authorization")
        authorization: String,

        @PartMap
        fields: Map<
            String,
            @JvmSuppressWildcards RequestBody
        >,

        @Part
        images: List<MultipartBody.Part>
    ): EnrollmentResponse

    // ========================================================
    // FACE RECOGNITION
    // ========================================================

    @Multipart
    @POST("api/recognize")
    suspend fun recognizeFrame(
        @Header("Authorization")
        authorization: String,

        @Part
        image: MultipartBody.Part,

        @PartMap
        fields: Map<
            String,
            @JvmSuppressWildcards RequestBody
        >
    ): RecognitionResponse

    // ========================================================
    // ATTENDANCE SYNC
    // ========================================================

    @POST("api/sync")
    suspend fun syncAttendance(
        @Header("Authorization")
        authorization: String,

        @Body request: SyncRequest
    ): SyncResponse

    // ========================================================
    // ATTENDANCE REPORT
    // ========================================================

    @GET("api/reports")
    suspend fun getAttendanceReport(
        @Header("Authorization")
        authorization: String,

        @Query("date")
        date: String? = null,

        @Query("department")
        department: String? = null,

        @Query("semester")
        semester: String? = null,

        @Query("section")
        section: String? = null,

        @Query("subject")
        subject: String? = null,

        @Query("faculty")
        faculty: String? = null
    ): AttendanceReportResponse

    // ========================================================
    // ANALYTICS
    // ========================================================

    @GET("api/analytics")
    suspend fun getAnalytics(
        @Header("Authorization")
        authorization: String,

        @Query("department")
        department: String? = null,

        @Query("semester")
        semester: String? = null,

        @Query("section")
        section: String? = null,

        @Query("subject")
        subject: String? = null
    ): AnalyticsResponse

    // ========================================================
    // A2 — WEEKLY SUMMARY
    // ========================================================

    @GET("api/weekly-summary")
    suspend fun getWeeklySummary(
        @Header("Authorization")
        authorization: String,

        @Query("department")
        department: String? = null,

        @Query("semester")
        semester: String? = null,

        @Query("section")
        section: String? = null,

        @Query("subject")
        subject: String? = null
    ): WeeklySummaryResponse

    // ========================================================
    // A5 — LOW ATTENDANCE
    // ========================================================

    @GET("api/low-attendance")
    suspend fun getLowAttendance(
        @Header("Authorization")
        authorization: String,

        @Query("threshold")
        threshold: Double = 75.0,

        @Query("department")
        department: String? = null,

        @Query("semester")
        semester: String? = null,

        @Query("section")
        section: String? = null,

        @Query("subject")
        subject: String? = null
    ): LowAttendanceResponse

    // ========================================================
    // A6 — USER PROFILE
    // ========================================================

    @GET("api/profile")
    suspend fun getProfile(
        @Header("Authorization")
        authorization: String
    ): ProfileResponse
    // ========================================================
// A8 — ATTENDANCE SESSION
// ========================================================

    @POST("api/sessions/start")
    suspend fun startAttendanceSession(
        @Header("Authorization")
        authorization: String,

        @Body
        request: StartSessionRequest
    ): SessionResponse


    @POST("api/sessions/end")
    suspend fun endAttendanceSession(
        @Header("Authorization")
        authorization: String,

        @Body
        request: Map<String, Int>
    ): SessionResponse


    @GET("api/sessions")
    suspend fun getAttendanceSessions(
        @Header("Authorization")
        authorization: String
    ): SessionListResponse
}