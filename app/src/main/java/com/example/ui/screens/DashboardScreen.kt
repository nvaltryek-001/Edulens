package com.example.ui.screens

import com.example.api.UserProfile
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.api.AnalyticsResponse
import com.example.api.ApiClient
import com.example.api.LowAttendanceResponse
import com.example.api.LowAttendanceStudent
import com.example.api.WeeklySummaryResponse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

// ============================================================
// DASHBOARD SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(

    onNavigateToAttendance: (
        subject: String,
        faculty: String,
        department: String,
        semester: String,
        section: String
    ) -> Unit,

    onNavigateToEnrollment: () -> Unit,

    onNavigateToReports: () -> Unit

) {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    // ========================================================
    // UI STATE
    // ========================================================

    var showStartSessionDialog by remember {
        mutableStateOf(false)
    }

    var showProfileDialog by remember {
        mutableStateOf(false)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    // ========================================================
    // A1 — ANALYTICS
    // ========================================================

    var analyticsData by remember {
        mutableStateOf<AnalyticsResponse?>(null)
    }

    // ========================================================
    // A2 — WEEKLY SUMMARY
    // ========================================================

    var weeklySummaryData by remember {
        mutableStateOf<WeeklySummaryResponse?>(null)
    }

    // ========================================================
    // A5 — LOW ATTENDANCE
    // ========================================================

    var lowAttendanceData by remember {
        mutableStateOf<LowAttendanceResponse?>(null)
    }

    var lowAttendanceLoading by remember {
        mutableStateOf(false)
    }

// ========================================================
// A6 — USER PROFILE
// ========================================================

var profileData by remember {
    mutableStateOf<UserProfile?>(null)
}

var profileLoading by remember {
    mutableStateOf(false)
}

var profileError by remember {
    mutableStateOf("")
}
    // ========================================================
    // SESSION FORM
    // ========================================================

    var selectedSubject by remember {
        mutableStateOf("Computer Science")
    }

    var selectedFaculty by remember {
        mutableStateOf("Admin")
    }

    var selectedDepartment by remember {
        mutableStateOf("Computer Science")
    }

    var selectedSemester by remember {
        mutableStateOf("6")
    }

    var selectedSection by remember {
        mutableStateOf("A")
    }

    // ========================================================
    // LOAD DASHBOARD DATA
    // ========================================================

    fun loadDashboardData() {

        coroutineScope.launch {

            isLoading = true
            errorMessage = ""

            try {

                val preferences =
                    context.getSharedPreferences(
                        "edulens_session",
                        Context.MODE_PRIVATE
                    )

                val token =
                    preferences.getString(
                        "token",
                        null
                    )

                if (token.isNullOrBlank()) {

                    errorMessage =
                        "Authentication token not found. Please login again."

                    isLoading = false

                    return@launch
                }

                val authorization =
                    "Bearer $token"

                // =================================================
                // A1 — REAL ANALYTICS
                // =================================================

                val analyticsResponse =
                    ApiClient
                        .flaskApiService
                        .getAnalytics(

                            authorization =
                                authorization,

                            department =
                                selectedDepartment
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    },

                            semester =
                                selectedSemester
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    },

                            section =
                                selectedSection
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    },

                            subject =
                                selectedSubject
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    }
                        )

                if (
                    analyticsResponse.success
                ) {

                    analyticsData =
                        analyticsResponse

                } else {

                    errorMessage =
                        "Unable to load dashboard analytics."
                }

                // =================================================
                // A2 — REAL WEEKLY SUMMARY
                // =================================================

                val weeklyResponse =
                    ApiClient
                        .flaskApiService
                        .getWeeklySummary(

                            authorization =
                                authorization,

                            department =
                                selectedDepartment
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    },

                            semester =
                                selectedSemester
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    },

                            section =
                                selectedSection
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    },

                            subject =
                                selectedSubject
                                    .trim()
                                    .takeIf {
                                        it.isNotBlank()
                                    }
                        )

                if (
                    weeklyResponse.success
                ) {

                    weeklySummaryData =
                        weeklyResponse
                }

                // =================================================
                // A5 — LOW ATTENDANCE
                // =================================================

                lowAttendanceLoading =
                    true

                try {

                    val lowAttendanceResponse =
                        ApiClient
                            .flaskApiService
                            .getLowAttendance(

                                authorization =
                                    authorization,

                                threshold =
                                    75.0,

                                department =
                                    selectedDepartment
                                        .trim()
                                        .takeIf {
                                            it.isNotBlank()
                                        },

                                semester =
                                    selectedSemester
                                        .trim()
                                        .takeIf {
                                            it.isNotBlank()
                                        },

                                section =
                                    selectedSection
                                        .trim()
                                        .takeIf {
                                            it.isNotBlank()
                                        },

                                subject =
                                    selectedSubject
                                        .trim()
                                        .takeIf {
                                            it.isNotBlank()
                                        }
                            )

                    if (
                        lowAttendanceResponse.success
                    ) {

                        lowAttendanceData =
                            lowAttendanceResponse
                    }

                } catch (e: Exception) {

                    println(
                        "EDULENS_LOW_ATTENDANCE: ${e.message}"
                    )

                } finally {

                    lowAttendanceLoading =
                        false
                }

            } catch (e: Exception) {

                errorMessage =
                    "Unable to connect to EduLens server."

                println(
                    "EDULENS_DASHBOARD: ${e.message}"
                )

            } finally {

                isLoading =
                    false
            }
        }
    }

    // ========================================================
    // A6 — LOAD REAL USER PROFILE
    // ========================================================

    fun loadProfile() {

        coroutineScope.launch {

            profileLoading = true
            profileError = ""

            try {

                val preferences =
                    context.getSharedPreferences(
                        "edulens_session",
                        Context.MODE_PRIVATE
                    )

                val token =
                    preferences.getString(
                        "token",
                        null
                    )

                if (token.isNullOrBlank()) {

                    profileError =
                        "Authentication token not found. Please login again."

                    profileLoading = false

                    return@launch
                }

                val response =
                    ApiClient
                        .flaskApiService
                        .getProfile(
                            authorization =
                                "Bearer $token"
                        )

                if (response.success) {

                    profileData =
                        response.profile

                } else {

                    profileError =
                        response.error
                            ?: "Unable to load profile."
                }

            } catch (e: Exception) {

                profileError =
                    "Unable to connect to profile service."

                println(
                    "EDULENS_PROFILE: ${e.message}"
                )

            } finally {

                profileLoading = false
            }
        }
    }

    // ========================================================
    // INITIAL LOAD
    // ========================================================

    LaunchedEffect(Unit) {

        loadDashboardData()
    }

    // ========================================================
    // START ATTENDANCE SESSION DIALOG
    // ========================================================

    if (
        showStartSessionDialog
    ) {

        AlertDialog(

            onDismissRequest = {

                showStartSessionDialog =
                    false
            },

            title = {

                Text(
                    text =
                        "Start Attendance Session"
                )
            },

            text = {

                Column {

                    OutlinedTextField(

                        value =
                            selectedSubject,

                        onValueChange = {

                            selectedSubject =
                                it
                        },

                        label = {

                            Text(
                                "Subject"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedTextField(

                        value =
                            selectedFaculty,

                        onValueChange = {

                            selectedFaculty =
                                it
                        },

                        label = {

                            Text(
                                "Faculty"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedTextField(

                        value =
                            selectedDepartment,

                        onValueChange = {

                            selectedDepartment =
                                it
                        },

                        label = {

                            Text(
                                "Department"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedTextField(

                        value =
                            selectedSemester,

                        onValueChange = {

                            selectedSemester =
                                it
                        },

                        label = {

                            Text(
                                "Semester"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true
                    )

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    OutlinedTextField(

                        value =
                            selectedSection,

                        onValueChange = {

                            selectedSection =
                                it
                        },

                        label = {

                            Text(
                                "Section"
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true
                    )
                }
            },

            confirmButton = {

                Button(

                    onClick = {

                        if (
                            selectedSubject.isNotBlank() &&
                            selectedFaculty.isNotBlank() &&
                            selectedDepartment.isNotBlank() &&
                            selectedSemester.isNotBlank() &&
                            selectedSection.isNotBlank()
                        ) {

                            showStartSessionDialog =
                                false

                            onNavigateToAttendance(

                                selectedSubject.trim(),

                                selectedFaculty.trim(),

                                selectedDepartment.trim(),

                                selectedSemester.trim(),

                                selectedSection.trim()
                            )
                        }
                    }
                ) {

                    Text(
                        "Start"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showStartSessionDialog =
                            false
                    }
                ) {

                    Text(
                        "Cancel"
                    )
                }
            }
        )
    }

    // ========================================================
    // A6 — PROFILE DIALOG
    // ========================================================

    if (showProfileDialog) {

        AlertDialog(

            onDismissRequest = {

                showProfileDialog =
                    false
            },

            title = {

                Text(
                    text = "Profile",
                    fontWeight = FontWeight.Bold
                )
            },

            text = {

                if (profileLoading) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        CircularProgressIndicator()
                    }

                } else if (profileData != null) {

                    val profile =
                        profileData!!

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        bottom = 16.dp
                                    ),

                            contentAlignment =
                                Alignment.Center
                        ) {

                            Box(
                                modifier =
                                    Modifier
                                        .size(72.dp)
                                        .background(
                                            MaterialTheme
                                                .colorScheme
                                                .primaryContainer,
                                            RoundedCornerShape(
                                                20.dp
                                            )
                                        ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text =
                                        profile.username
                                            .take(2)
                                            .uppercase(),

                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleLarge,

                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Username",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium
                        )

                        Text(
                            text =
                                profile.username
                                    .ifBlank {
                                        "Unknown"
                                    },

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,

                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Text(
                            text = "Role",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium
                        )

                        Text(
                            text =
                                profile.role
                                    .replaceFirstChar {
                                        it.uppercase()
                                    },

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyLarge
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Text(
                            text = "Account Status",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium
                        )

                        Text(
                            text =
                                profile.status
                                    .ifBlank {
                                        "Unknown"
                                    },

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .secondary,

                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Text(
                            text = "User ID",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium
                        )

                        Text(
                            text =
                                profile.userId
                                    .ifBlank {
                                        "N/A"
                                    },

                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Text(
                            text = "System: EduLens",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }

                } else {

                    Column {

                        Text(
                            text =
                                if (profileError.isBlank()) {
                                    "Profile information unavailable."
                                } else {
                                    profileError
                                },

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error
                        )

                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        Text(
                            text =
                                "Please check your connection and try again."
                        )
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        showProfileDialog = false
                    }
                ) {
                    Text("Close")
                }
            },

            dismissButton = {

                if (!profileLoading) {

                    TextButton(
                        onClick = {
                            loadProfile()
                        }
                    ) {
                        Text("Refresh")
                    }
                }
            }
        )
    }

    // ========================================================
    // ANALYTICS VALUES
    // ========================================================

    val overview =
        analyticsData?.overview

    val totalStudents =
        overview
            ?.totalStudents
            ?: 0

    val presentCount =
        overview
            ?.presentToday
            ?: 0

    val absentCount =
        overview
            ?.absentToday
            ?: 0

    val attendancePercentage =
        overview
            ?.attendancePercentage
            ?: 0.0

    val recentAttendance =
        analyticsData
            ?.recentAttendance
            ?: emptyList()

    // ========================================================
    // A2 — WEEKLY SUMMARY
    // ========================================================

    val weeklyDays =
        weeklySummaryData
            ?.days
            ?: emptyList()

    val trendValues =
        weeklyDays
            .map {
                it.attendancePercentage
                    .toFloat()
            }
            .ifEmpty {
                listOf(0f)
            }

    // ========================================================
    // A4 — RECENT SESSIONS
    // ========================================================

    val recentSessions =
        recentAttendance
            .groupBy {

                SessionKey(

                    date =
                        it.date,

                    subject =
                        it.subject,

                    faculty =
                        it.faculty
                )
            }
            .entries
            .sortedWith(

                compareByDescending {

                    it.key.date
                }
            )
            .take(5)

    // ========================================================
    // A5 — LOW ATTENDANCE LIST
    // ========================================================

    val lowAttendanceStudents =
        lowAttendanceData
            ?.students
            ?: emptyList()

    // ========================================================
    // MAIN SCAFFOLD
    // ========================================================

    Scaffold(

        floatingActionButton = {

            Column(

                horizontalAlignment =
                    Alignment.End
            ) {

                // =================================================
                // REFRESH
                // =================================================

                SmallFloatingActionButton(

                    onClick = {

                        loadDashboardData()
                    }
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Refresh,

                        contentDescription =
                            "Refresh Dashboard"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                // =================================================
                // REPORTS
                // =================================================

                FloatingActionButton(

                    onClick = {

                        onNavigateToReports()
                    }
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Assessment,

                        contentDescription =
                            "Attendance Reports"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                // =================================================
                // ENROLLMENT
                // =================================================

                SmallFloatingActionButton(

                    onClick =
                        onNavigateToEnrollment,

                    containerColor =
                        Color(0xFF2E7D32),

                    contentColor =
                        Color.White

                ) {

                    Icon(

                        imageVector =
                            Icons.Default.PersonAdd,

                        contentDescription =
                            "Face Enrollment"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                // =================================================
                // START SESSION
                // =================================================

                ExtendedFloatingActionButton(

                    onClick = {

                        showStartSessionDialog =
                            true
                    },

                    containerColor =
                        Color(0xFFFF9800),

                    contentColor =
                        Color.White,

                    icon = {

                        Icon(

                            imageVector =
                                Icons.Default.CameraAlt,

                            contentDescription =
                                "Start Session"
                        )
                    },

                    text = {

                        Text(
                            text =
                                "Start Session"
                        )
                    }
                )
            }
        }

    ) { padding ->

        LazyColumn(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),

            contentPadding =
                PaddingValues(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            // =====================================================
            // HEADER
            // =====================================================

            item {

                Row(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 8.dp
                            ),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Column {

                        Text(

                            text =
                                "EduLens",

                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(

                            text =
                                if (isLoading)
                                    "LOADING DATA"

                                else if (
                                    errorMessage.isBlank()
                                )
                                    "SYSTEM LIVE • BACKEND CONNECTED"

                                else
                                    "BACKEND CONNECTION ERROR",

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,

                            color =
                                if (
                                    errorMessage.isBlank()
                                )
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .error
                        )
                    }

                    IconButton(

                        onClick = {

                        showProfileDialog =
                            true

                        loadProfile()
                        }
                    ) {

                        Box(

                            modifier =
                                Modifier
                                    .size(42.dp)
                                    .background(

                                        MaterialTheme
                                            .colorScheme
                                            .primaryContainer,

                                        RoundedCornerShape(
                                            14.dp
                                        )
                                    ),

                            contentAlignment =
                                Alignment.Center
                        ) {

                            val username =
                                context
                                    .getSharedPreferences(
                                        "edulens_session",
                                        Context.MODE_PRIVATE
                                    )
                                    .getString(
                                        "username",
                                        "AD"
                                    )
                                    ?: "AD"

                            Text(

                                text =
                                    username
                                        .take(2)
                                        .uppercase(),

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // =====================================================
            // HEADER IMAGE
            // =====================================================

            item {

                Image(

                    painter =
                        painterResource(
                            id =
                                R.drawable
                                    .img_dashboard_header_1785561910973
                        ),

                    contentDescription =
                        "Dashboard Header",

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(
                                RoundedCornerShape(
                                    16.dp
                                )
                            ),

                    contentScale =
                        ContentScale.Crop
                )
            }

            // =====================================================
            // ERROR
            // =====================================================

            if (
                errorMessage.isNotBlank()
            ) {

                item {

                    Card(

                        colors =
                            CardDefaults
                                .cardColors(

                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .errorContainer
                                )
                    ) {

                        Text(

                            text =
                                errorMessage,

                            modifier =
                                Modifier.padding(
                                    16.dp
                                ),

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onErrorContainer
                        )
                    }
                }
            }

            // =====================================================
            // ATTENDANCE SUMMARY
            // =====================================================

            item {

                Text(

                    text =
                        "Attendance Summary",

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,

                    fontWeight =
                        FontWeight.Bold
                )
            }

            // =====================================================
            // SUMMARY ROW 1
            // =====================================================

            item {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {

                    SummaryCard(

                        title =
                            "Attendance %",

                        value =
                            if (isLoading)
                                "..."

                            else
                                String.format(
                                    Locale.US,
                                    "%.1f%%",
                                    attendancePercentage
                                ),

                        icon =
                            Icons.AutoMirrored.Filled
                                .TrendingUp,

                        modifier =
                            Modifier.weight(1f)
                    )

                    SummaryCard(

                        title =
                            "Present Today",

                        value =
                            if (isLoading)
                                "..."

                            else
                                presentCount.toString(),

                        icon =
                            Icons.Default.CheckCircle,

                        modifier =
                            Modifier.weight(1f)
                    )
                }
            }

            // =====================================================
            // SUMMARY ROW 2
            // =====================================================

            item {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {

                    SummaryCard(

                        title =
                            "Absent Today",

                        value =
                            if (isLoading)
                                "..."

                            else
                                absentCount.toString(),

                        icon =
                            Icons.Default.Warning,

                        modifier =
                            Modifier.weight(1f),

                        color =
                            MaterialTheme
                                .colorScheme
                                .error
                    )

                    SummaryCard(

                        title =
                            "Total Students",

                        value =
                            if (isLoading)
                                "..."

                            else
                                totalStudents.toString(),

                        icon =
                            Icons.Default.People,

                        modifier =
                            Modifier.weight(1f)
                    )
                }
            }

            // =====================================================
            // ATTENDANCE TRENDS
            // =====================================================

            item {

                Card(

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(

                        modifier =
                            Modifier.padding(
                                16.dp
                            )
                    ) {

                        Row(

                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Text(

                                text =
                                    "Attendance Trends",

                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium,

                                fontWeight =
                                    FontWeight.SemiBold
                            )

                            Text(

                                text =
                                    "Last 7 Days",

                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    16.dp
                                )
                        )

                        if (
                            weeklyDays.isEmpty()
                        ) {

                            Box(

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    "No weekly attendance data yet."
                                )
                            }

                        } else {

                            AttendanceTrendChart(

                                values =
                                    trendValues,

                                labels =
                                    weeklyDays.map {
                                        it.day
                                    }
                            )
                        }
                    }
                }
            }

            // =====================================================
            // A5 — LOW ATTENDANCE
            // =====================================================

            item {

                Card(

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Column(

                        modifier =
                            Modifier.padding(
                                16.dp
                            )
                    ) {

                        // -----------------------------------------
                        // TITLE
                        // -----------------------------------------

                        Row(

                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Row(

                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {

                                Icon(

                                    imageVector =
                                        Icons.Default.Warning,

                                    contentDescription =
                                        "Low Attendance",

                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .error
                                )

                                Spacer(
                                    modifier =
                                        Modifier.width(
                                            8.dp
                                        )
                                )

                                Text(

                                    text =
                                        "Low Attendance",

                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,

                                    fontWeight =
                                        FontWeight.SemiBold
                                )
                            }

                            Text(

                                text =
                                    "Below 75%",

                                style =
                                    MaterialTheme
                                        .typography
                                        .labelMedium,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    12.dp
                                )
                        )

                        // -----------------------------------------
                        // LOADING
                        // -----------------------------------------

                        if (
                            lowAttendanceLoading
                        ) {

                            Box(

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            20.dp
                                        ),

                                contentAlignment =
                                    Alignment.Center
                            ) {

                                CircularProgressIndicator(

                                    modifier =
                                        Modifier.size(
                                            28.dp
                                        )
                                )
                            }

                        } else {

                            // -------------------------------------
                            // NO STUDENTS
                            // -------------------------------------

                            if (
                                lowAttendanceStudents.isEmpty()
                            ) {

                                Box(

                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                vertical = 20.dp
                                            ),

                                    contentAlignment =
                                        Alignment.Center
                                ) {

                                    Column(

                                        horizontalAlignment =
                                            Alignment.CenterHorizontally
                                    ) {

                                        Icon(

                                            imageVector =
                                                Icons.Default.CheckCircle,

                                            contentDescription =
                                                null,

                                            tint =
                                                MaterialTheme
                                                    .colorScheme
                                                    .secondary,

                                            modifier =
                                                Modifier.size(
                                                    32.dp
                                                )
                                        )

                                        Spacer(
                                            modifier =
                                                Modifier.height(
                                                    8.dp
                                                )
                                        )

                                        Text(

                                            text =
                                                "No students below 75%",

                                            fontWeight =
                                                FontWeight.Medium
                                        )

                                        Text(

                                            text =
                                                "Attendance is looking good.",

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall,

                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onSurfaceVariant
                                        )
                                    }
                                }

                            } else {

                                // ---------------------------------
                                // COUNT
                                // ---------------------------------

                                Text(

                                    text =
                                        "${lowAttendanceStudents.size} student(s) need attention",

                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium,

                                    fontWeight =
                                        FontWeight.Medium
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(
                                            10.dp
                                        )
                                )

                                // ---------------------------------
                                // STUDENTS
                                // ---------------------------------

                                lowAttendanceStudents
                                    .take(5)
                                    .forEach { student ->

                                        LowAttendanceItem(
                                            student =
                                                student
                                        )

                                        Spacer(
                                            modifier =
                                                Modifier.height(
                                                    10.dp
                                                )
                                        )
                                    }

                                if (
                                    lowAttendanceStudents.size > 5
                                ) {

                                    Text(

                                        text =
                                            "+ ${
                                                lowAttendanceStudents.size - 5
                                            } more students",

                                        style =
                                            MaterialTheme
                                                .typography
                                                .labelMedium,

                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // =====================================================
            // RECENT SESSIONS
            // =====================================================

            item {

                Text(

                    text =
                        "Recent Sessions",

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,

                    fontWeight =
                        FontWeight.Bold
                )
            }

            if (
                recentSessions.isEmpty()
            ) {

                item {

                    Card(

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Box(

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        24.dp
                                    ),

                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                "No attendance sessions yet."
                            )
                        }
                    }
                }

            } else {

                items(
                    recentSessions
                ) { session ->

                    SessionItem(

                        courseName =
                            session
                                .key
                                .subject
                                .ifBlank {
                                    "Attendance Session"
                                },

                        date =
                            session.key.date,

                        attendanceCount =
                            session.value.count {

                                it.status.equals(
                                    "Present",
                                    ignoreCase = true
                                )
                            },

                        totalCount =
                            session.value.size,

                        faculty =
                            session.key.faculty
                    )
                }
            }

            // =====================================================
            // END SPACING
            // =====================================================

            item {

                Spacer(
                    modifier =
                        Modifier.height(
                            120.dp
                        )
                )
            }
        }
    }
}

// ============================================================
// SESSION KEY
// ============================================================

private data class SessionKey(

    val date: String,

    val subject: String,

    val faculty: String
)

// ============================================================
// SUMMARY CARD
// ============================================================

@Composable
fun SummaryCard(

    title: String,

    value: String,

    icon:
        androidx.compose.ui.graphics.vector.ImageVector,

    modifier: Modifier = Modifier,

    color: Color =
        MaterialTheme
            .colorScheme
            .primary

) {

    Card(

        modifier =
            modifier
    ) {

        Column(

            modifier =
                Modifier.padding(
                    16.dp
                )
        ) {

            Row(

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(

                    imageVector =
                        icon,

                    contentDescription =
                        null,

                    modifier =
                        Modifier.size(
                            18.dp
                        ),

                    tint =
                        color
                )

                Spacer(
                    modifier =
                        Modifier.width(
                            6.dp
                        )
                )

                Text(

                    text =
                        title,

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            Text(

                text =
                    value,

                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,

                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

// ============================================================
// A5 — LOW ATTENDANCE ITEM
// ============================================================

@Composable
fun LowAttendanceItem(

    student: LowAttendanceStudent

) {

    val percentage =
        student
            .attendancePercentage
            .coerceIn(
                0.0,
                100.0
            )

    val progress =
        (
            percentage / 100.0
        )
            .toFloat()
            .coerceIn(
                0f,
                1f
            )

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(

                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
            )
    ) {

        Column(

            modifier =
                Modifier.padding(
                    12.dp
                )
        ) {

            // =================================================
            // NAME + PERCENTAGE
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(

                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(

                        text =
                            student.name
                                .ifBlank {
                                    "Unknown Student"
                                },

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                2.dp
                            )
                    )

                    Text(

                        text =
                            if (
                                student.usn.isBlank()
                            )
                                student.studentId
                            else
                                student.usn,

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                Text(

                    text =
                        String.format(
                            Locale.US,
                            "%.1f%%",
                            percentage
                        ),

                    fontWeight =
                        FontWeight.Bold,

                    color =
                        MaterialTheme
                            .colorScheme
                            .error
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        8.dp
                    )
            )

            // =================================================
            // PRESENT / ABSENT / TOTAL
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(

                    text =
                        "Present: ${student.presentClasses}",

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )

                Text(

                    text =
                        "Absent: ${student.absentClasses}",

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )

                Text(

                    text =
                        "Total: ${student.totalClasses}",

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        7.dp
                    )
            )

            // =================================================
            // PROGRESS BAR
            // =================================================

            LinearProgressIndicator(

                progress = {
                    progress
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(
                            RoundedCornerShape(
                                50.dp
                            )
                        ),

                color =
                    MaterialTheme
                        .colorScheme
                        .error
            )
        }
    }
}

// ============================================================
// ATTENDANCE TREND CHART
// ============================================================

@Composable
private fun AttendanceTrendChart(

    values: List<Float>,

    labels: List<String> =
        emptyList()

) {

    val primaryColor =
        MaterialTheme
            .colorScheme
            .primary

    val secondaryColor =
        MaterialTheme
            .colorScheme
            .secondary

    val gridColor =
        MaterialTheme
            .colorScheme
            .outlineVariant

    val labelColor =
        MaterialTheme
            .colorScheme
            .onSurfaceVariant

    val safeValues =
        values.map {
            it.coerceIn(
                0f,
                100f
            )
        }

    if (
        safeValues.isEmpty()
    ) {

        Box(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        190.dp
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text =
                    "No attendance data available",

                color =
                    labelColor
            )
        }

        return
    }

    Column(

        modifier =
            Modifier.fillMaxWidth()
    ) {

        // ====================================================
        // CHART
        // ====================================================

        Canvas(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        190.dp
                    )
        ) {

            val leftPadding =
                38f

            val rightPadding =
                12f

            val topPadding =
                12f

            val bottomPadding =
                12f

            val chartWidth =
                size.width -
                    leftPadding -
                    rightPadding

            val chartHeight =
                size.height -
                    topPadding -
                    bottomPadding

            // ------------------------------------------------
            // GRID
            // ------------------------------------------------

            listOf(
                100f,
                75f,
                50f,
                25f,
                0f
            ).forEach { percentage ->

                val y =
                    topPadding +
                        (
                            1f -
                                percentage /
                                    100f
                        ) *
                        chartHeight

                drawLine(

                    color =
                        gridColor,

                    start =
                        Offset(
                            leftPadding,
                            y
                        ),

                    end =
                        Offset(
                            size.width -
                                rightPadding,
                            y
                        ),

                    strokeWidth =
                        1.5f
                )
            }

            // ------------------------------------------------
            // POINTS
            // ------------------------------------------------

            val points =
                if (
                    safeValues.size == 1
                ) {

                    listOf(

                        Offset(

                            leftPadding +
                                chartWidth /
                                    2f,

                            topPadding +
                                (
                                    1f -
                                        safeValues[0] /
                                            100f
                                ) *
                                chartHeight
                        )
                    )

                } else {

                    safeValues.mapIndexed {

                        index,
                        value ->

                        val x =
                            leftPadding +
                                (
                                    index.toFloat() /
                                        (
                                            safeValues.size -
                                                1
                                        )
                                ) *
                                chartWidth

                        val y =
                            topPadding +
                                (
                                    1f -
                                        value /
                                            100f
                                ) *
                                chartHeight

                        Offset(
                            x,
                            y
                        )
                    }
                }

            // ------------------------------------------------
            // TREND LINE
            // ------------------------------------------------

            for (
                index in
                0 until points.size - 1
            ) {

                drawLine(

                    color =
                        primaryColor,

                    start =
                        points[index],

                    end =
                        points[index + 1],

                    strokeWidth =
                        6f
                )
            }

            // ------------------------------------------------
            // DATA POINTS
            // ------------------------------------------------

            points.forEach { point ->

                drawCircle(

                    color =
                        primaryColor,

                    radius =
                        8f,

                    center =
                        point
                )

                drawCircle(

                    color =
                        secondaryColor,

                    radius =
                        3.5f,

                    center =
                        point
                )
            }
        }

        // ====================================================
        // DAY + PERCENTAGE LABELS
        // ====================================================

        Row(

            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            safeValues.forEachIndexed {

                index,
                value ->

                Column(

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(

                        text =
                            labels
                                .getOrNull(
                                    index
                                )
                                ?: "",

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,

                        color =
                            labelColor
                    )

                    Text(

                        text =
                            "${value.toInt()}%",

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    8.dp
                )
        )

        // ====================================================
        // LEGEND
        // ====================================================

        Row(

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(

                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(
                            RoundedCornerShape(
                                50
                            )
                        )
                        .background(
                            primaryColor
                        )
            )

            Spacer(
                modifier =
                    Modifier.width(
                        6.dp
                    )
            )

            Text(

                text =
                    "Attendance percentage",

                style =
                    MaterialTheme
                        .typography
                        .labelSmall,

                color =
                    labelColor
            )
        }
    }
}

// ============================================================
// RECENT SESSION ITEM
// ============================================================

@Composable
fun SessionItem(

    courseName: String,

    date: String,

    attendanceCount: Int,

    totalCount: Int,

    faculty: String

) {

    val formattedDate =
        remember(date) {

            val inputFormat =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                )

            val outputFormat =
                SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.US
                )

            try {

                val parsedDate =
                    inputFormat.parse(
                        date
                    )

                if (
                    parsedDate != null
                ) {

                    outputFormat.format(
                        parsedDate
                    )

                } else {

                    date
                }

            } catch (
                e: Exception
            ) {

                date
            }
        }

    val percentage =
        if (
            totalCount > 0
        ) {

            (
                attendanceCount.toFloat() /
                    totalCount.toFloat()
            ) * 100f

        } else {

            0f
        }

    val percentageText =
        "${percentage.toInt()}%"

    Card(

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 4.dp
                )
    ) {

        Column(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        16.dp
                    )
        ) {

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(

                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(
                                RoundedCornerShape(
                                    12.dp
                                )
                            )
                            .background(
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.CalendarMonth,

                        contentDescription =
                            "Attendance Session",

                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(
                            14.dp
                        )
                )

                Column(

                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(

                        text =
                            courseName,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                3.dp
                            )
                    )

                    Text(

                        text =
                            formattedDate,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )

                    if (
                        faculty.isNotBlank()
                    ) {

                        Text(

                            text =
                                "Faculty: $faculty",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }

                Column(

                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(

                        text =
                            percentageText,

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Text(

                        text =
                            "Attendance",

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp
                    )
            )

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text =
                        "Present",

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )

                Text(

                    text =
                        "$attendanceCount / $totalCount",

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,

                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        6.dp
                    )
            )

            LinearProgressIndicator(

                progress = {

                    (
                        percentage /
                            100f
                    ).coerceIn(
                        0f,
                        1f
                    )
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            7.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                50.dp
                            )
                        )
            )
        }
    }
}