package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiClient
import com.example.api.RecognizedStudent
import com.example.api.StartSessionRequest
import com.example.api.SessionInfo
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.Attendance
import com.example.data.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: AppRepository

    // ============================================================
    // RECOGNIZED STUDENTS
    // ============================================================

    private val _recognizedStudents =
        MutableStateFlow<List<RecognizedStudent>>(emptyList())

    val recognizedStudents: StateFlow<List<RecognizedStudent>> =
        _recognizedStudents.asStateFlow()

    // ============================================================
    // A8 — SESSION STATE
    // ============================================================

    private val _activeSession =
        MutableStateFlow<SessionInfo?>(null)

    val activeSession: StateFlow<SessionInfo?> =
        _activeSession.asStateFlow()

    private val _sessionLoading =
        MutableStateFlow(false)

    val sessionLoading: StateFlow<Boolean> =
        _sessionLoading.asStateFlow()

    private val _sessionError =
        MutableStateFlow<String?>(null)

    val sessionError: StateFlow<String?> =
        _sessionError.asStateFlow()

    // ============================================================
    // DATABASE
    // ============================================================

    init {
        val database =
            AppDatabase.getDatabase(application)

        repository =
            AppRepository(
                database.appDao()
            )
    }

    // ============================================================
    // TOKEN
    // ============================================================

    private fun getToken(): String? {

        val preferences =
            getApplication<Application>()
                .getSharedPreferences(
                    "edulens_session",
                    Context.MODE_PRIVATE
                )

        return preferences.getString(
            "token",
            null
        )
    }

    // ============================================================
    // A8 — START SESSION
    // ============================================================

    fun startSession(
        subject: String,
        faculty: String,
        department: String,
        semester: String,
        section: String,
        onSuccess: () -> Unit = {}
    ) {

        viewModelScope.launch(Dispatchers.IO) {

            _sessionLoading.value = true
            _sessionError.value = null

            try {

                val token = getToken()

                if (token.isNullOrBlank()) {

                    _sessionError.value =
                        "Authentication token not found. Please login again."

                    return@launch
                }

                println(
                    "EDULENS_A8: Starting attendance session"
                )

                println(
                    "EDULENS_A8: subject=${subject.trim()}, " +
                        "faculty=${faculty.trim()}, " +
                        "department=${department.trim()}, " +
                        "semester=${semester.trim()}, " +
                        "section=${section.trim()}"
                )

                val response =
                    ApiClient
                        .flaskApiService
                        .startAttendanceSession(

                            authorization =
                                "Bearer $token",

                            request =
                                StartSessionRequest(

                                    subject =
                                        subject.trim(),

                                    faculty =
                                        faculty.trim(),

                                    department =
                                        department.trim(),

                                    semester =
                                        semester.trim(),

                                    section =
                                        section.trim()
                                )
                        )

                if (
                    response.success &&
                    response.session != null
                ) {

                    _activeSession.value =
                        response.session

                    println(
                        "EDULENS_A8: Session started: " +
                            response.session.sessionId
                    )

                    repository.insertLog(
                        LogEntry(
                            event =
                                "Attendance session started: " +
                                    response.session.sessionId
                        )
                    )

                    launch(Dispatchers.Main) {
                        onSuccess()
                    }

                } else {

                    _sessionError.value =
                        response.message.ifBlank {
                            "Unable to start attendance session."
                        }
                }

            } catch (e: Exception) {

                println(
                    "EDULENS_A8: Start session error = " +
                        e.message
                )

                e.printStackTrace()

                _sessionError.value =
                    "Unable to start attendance session: " +
                        (e.message ?: "Unknown error")

            } finally {

                _sessionLoading.value = false
            }
        }
    }

    // ============================================================
    // A8 — END SESSION
    // ============================================================

    fun endSession(
        onSuccess: () -> Unit = {}
    ) {

        viewModelScope.launch(Dispatchers.IO) {

            _sessionLoading.value = true
            _sessionError.value = null

            try {

                val token = getToken()

                if (token.isNullOrBlank()) {

                    _sessionError.value =
                        "Authentication token not found."

                    return@launch
                }

                val session =
                    _activeSession.value

                if (session == null) {

                    _sessionError.value =
                        "No active attendance session."

                    return@launch
                }

                println(
                    "EDULENS_A8: Ending session " +
                        session.sessionId
                )

                val request =
                    mapOf(
                        "sessionId" to
                            session.sessionId
                    )

                val response =
                    ApiClient
                        .flaskApiService
                        .endAttendanceSession(

                            authorization =
                                "Bearer $token",

                            request =
                                request
                        )

                if (response.success) {

                    _activeSession.value =
                        response.session

                    println(
                        "EDULENS_A8: Session ended successfully"
                    )

                    repository.insertLog(
                        LogEntry(
                            event =
                                "Attendance session ended: " +
                                    session.sessionId
                        )
                    )

                    launch(Dispatchers.Main) {
                        onSuccess()
                    }

                } else {

                    _sessionError.value =
                        response.message.ifBlank {
                            "Unable to end attendance session."
                        }
                }

            } catch (e: Exception) {

                println(
                    "EDULENS_A8: End session error = " +
                        e.message
                )

                e.printStackTrace()

                _sessionError.value =
                    "Unable to end attendance session: " +
                        (e.message ?: "Unknown error")

            } finally {

                _sessionLoading.value = false
            }
        }
    }

    // ============================================================
    // CLEAR SESSION ERROR
    // ============================================================

    fun clearSessionError() {
        _sessionError.value = null
    }

    // ============================================================
    // FACE RECOGNITION
    // ============================================================

    fun processFrame(
        file: File,
        subject: String,
        faculty: String,
        department: String,
        semester: String,
        section: String
    ) {

        viewModelScope.launch(Dispatchers.IO) {

            try {

                println(
                    "EDULENS_DEBUG: ============================="
                )

                println(
                    "EDULENS_DEBUG: Starting recognition"
                )

                println(
                    "EDULENS_DEBUG: Image = " +
                        file.absolutePath
                )

                println(
                    "EDULENS_DEBUG: Image exists = " +
                        file.exists()
                )

                println(
                    "EDULENS_DEBUG: Image size = " +
                        file.length() +
                        " bytes"
                )

                // =================================================
                // A8 — REQUIRE ACTIVE SESSION
                // =================================================

                val currentSession =
                    _activeSession.value

                if (
                    currentSession == null ||
                    currentSession.status != "ACTIVE"
                ) {

                    println(
                        "EDULENS_A8: Recognition blocked - no active session"
                    )

                    repository.insertLog(
                        LogEntry(
                            event =
                                "Recognition blocked: no active session"
                        )
                    )

                    return@launch
                }

                println(
                    "EDULENS_A8: Active session ID = " +
                        currentSession.sessionId
                )

                // =================================================
                // IMAGE VALIDATION
                // =================================================

                if (
                    !file.exists() ||
                    file.length() == 0L
                ) {

                    repository.insertLog(
                        LogEntry(
                            event =
                                "Recognition failed: image missing or empty"
                        )
                    )

                    return@launch
                }

                // =================================================
                // TOKEN
                // =================================================

                val token = getToken()

                if (token.isNullOrBlank()) {

                    repository.insertLog(
                        LogEntry(
                            event =
                                "Recognition failed: No authentication token"
                        )
                    )

                    return@launch
                }

                // =================================================
                // IMAGE MULTIPART
                // =================================================

                val requestFile =
                    file.asRequestBody(
                        "image/jpeg".toMediaTypeOrNull()
                    )

                val imagePart =
                    MultipartBody.Part.createFormData(
                        "image",
                        file.name,
                        requestFile
                    )

                // =================================================
                // CLASS INFORMATION
                // =================================================

                val cleanSubject =
                    subject.trim()

                val cleanFaculty =
                    faculty.trim()

                val cleanDepartment =
                    department.trim()

                val cleanSemester =
                    semester.trim()

                val cleanSection =
                    section.trim()

                println(
                    "EDULENS_A8: Sending recognition fields -> " +
                        "subject=$cleanSubject, " +
                        "faculty=$cleanFaculty, " +
                        "department=$cleanDepartment, " +
                        "semester=$cleanSemester, " +
                        "section=$cleanSection"
                )

                val textMediaType =
                    "text/plain".toMediaTypeOrNull()

                val fields =
                    mapOf(

                        "subject" to
                            cleanSubject.toRequestBody(
                                textMediaType
                            ),

                        "faculty" to
                            cleanFaculty.toRequestBody(
                                textMediaType
                            ),

                        "department" to
                            cleanDepartment.toRequestBody(
                                textMediaType
                            ),

                        "semester" to
                            cleanSemester.toRequestBody(
                                textMediaType
                            ),

                        "section" to
                            cleanSection.toRequestBody(
                                textMediaType
                            )
                    )

                // =================================================
                // CALL FLASK
                // =================================================

                println(
                    "EDULENS_A8: Calling POST /api/recognize"
                )

                val response =
                    ApiClient
                        .flaskApiService
                        .recognizeFrame(

                            authorization =
                                "Bearer $token",

                            image =
                                imagePart,

                            fields =
                                fields
                        )

                println(
                    "EDULENS_A8: Recognition response success = " +
                        response.success
                )

                println(
                    "EDULENS_A8: Recognized students count = " +
                        response.students.size
                )

                // =================================================
                // SUCCESS
                // =================================================

                if (response.success) {

                    _recognizedStudents.value =
                        response.students

                    val dateFormat =
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        )

                    val timeFormat =
                        SimpleDateFormat(
                            "HH:mm:ss",
                            Locale.getDefault()
                        )

                    val currentDate =
                        dateFormat.format(Date())

                    val currentTime =
                        timeFormat.format(Date())

                    // =================================================
                    // SAVE RECOGNIZED STUDENTS
                    // =================================================

                    for (
                        student in response.students
                    ) {

                        println(
                            "EDULENS_A8: Recognized -> " +
                                student.studentId +
                                " / " +
                                student.name +
                                " / " +
                                student.attendanceStatus
                        )

                        repository.insertAttendance(

                            Attendance(

                                studentId =
                                    student.studentId,

                                date =
                                    currentDate,

                                time =
                                    currentTime,

                                status =
                                    student.attendanceStatus,

                                subject =
                                    cleanSubject,

                                faculty =
                                    cleanFaculty,

                                // IMPORTANT:
                                // Link attendance to active A8 session.
                                sessionId =
                                    currentSession.sessionId,

                                syncStatus =
                                    false
                            )
                        )

                        println(
                            "EDULENS_A8: Attendance saved locally -> " +
                                "student=" +
                                student.studentId +
                                ", session=" +
                                currentSession.sessionId
                        )
                    }

                    repository.insertLog(
                        LogEntry(
                            event =
                                "Session " +
                                    currentSession.sessionId +
                                    ": " +
                                    response.students.size +
                                    " students recognized"
                        )
                    )

                } else {

                    println(
                        "EDULENS_A8: Backend returned success=false"
                    )

                    repository.insertLog(
                        LogEntry(
                            event =
                                "Recognition returned success=false"
                        )
                    )
                }

            } catch (e: Exception) {

                println(
                    "EDULENS_A8: Recognition error = " +
                        e.message
                )

                e.printStackTrace()

                repository.insertLog(
                    LogEntry(
                        event =
                            "Frame processing failed: " +
                                e.message
                    )
                )

            } finally {

                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}