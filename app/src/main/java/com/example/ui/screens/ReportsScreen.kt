package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.api.ApiClient
import com.example.api.AttendanceReportItem
import com.example.api.AttendanceReportResponse
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// A7 — ATTENDANCE REPORTS
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit
) {

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    // ========================================================
    // FILTER STATE
    // ========================================================

    var date by remember {

        mutableStateOf(

            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            ).format(Date())
        )
    }

    var department by remember {
        mutableStateOf("")
    }

    var semester by remember {
        mutableStateOf("")
    }

    var section by remember {
        mutableStateOf("")
    }

    var subject by remember {
        mutableStateOf("")
    }

    var faculty by remember {
        mutableStateOf("")
    }

    // ========================================================
    // REPORT STATE
    // ========================================================

    var report by remember {
        mutableStateOf<AttendanceReportResponse?>(null)
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    // ========================================================
    // LOAD REPORT
    // ========================================================

    suspend fun loadReport() {

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

                return
            }

            val response =
                ApiClient
                    .flaskApiService
                    .getAttendanceReport(

                        authorization =
                            "Bearer $token",

                        date =
                            date
                                .trim()
                                .ifBlank {
                                    null
                                },

                        department =
                            department
                                .trim()
                                .ifBlank {
                                    null
                                },

                        semester =
                            semester
                                .trim()
                                .ifBlank {
                                    null
                                },

                        section =
                            section
                                .trim()
                                .ifBlank {
                                    null
                                },

                        subject =
                            subject
                                .trim()
                                .ifBlank {
                                    null
                                },

                        faculty =
                            faculty
                                .trim()
                                .ifBlank {
                                    null
                                }
                    )

            if (response.success) {

                report =
                    response

            } else {

                errorMessage =
                    "Unable to load attendance report."
            }

        } catch (e: Exception) {

            errorMessage =
                e.message
                    ?: "Unable to connect to EduLens server."

            println(
                "EDULENS_REPORTS: ${e.message}"
            )

        } finally {

            isLoading = false
        }
    }

    // ========================================================
    // INITIAL LOAD
    // ========================================================

    LaunchedEffect(Unit) {

        loadReport()
    }

    // ========================================================
    // UI
    // ========================================================

    Scaffold(

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        text = "Attendance Reports"
                    )
                },

                navigationIcon = {

                    IconButton(

                        onClick =
                            onNavigateBack
                    ) {

                        Icon(

                            imageVector =
                                Icons
                                    .AutoMirrored
                                    .Filled
                                    .ArrowBack,

                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }

    ) { padding ->

        LazyColumn(

            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            // =================================================
            // FILTER HEADER
            // =================================================

            item {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.FilterAlt,

                        contentDescription =
                            "Filters"
                    )

                    Spacer(
                        modifier =
                            Modifier.widthSafe(
                                8.dp
                            )
                    )

                    Text(

                        text =
                            "Report Filters",

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            // =================================================
            // DATE
            // =================================================

            item {

                OutlinedTextField(

                    value =
                        date,

                    onValueChange = {

                        date = it
                    },

                    label = {

                        Text(
                            "Date (YYYY-MM-DD)"
                        )
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true
                )
            }

            // =================================================
            // DEPARTMENT
            // =================================================

            item {

                OutlinedTextField(

                    value =
                        department,

                    onValueChange = {

                        department = it
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
            }

            // =================================================
            // SEMESTER + SECTION
            // =================================================

            item {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    OutlinedTextField(

                        value =
                            semester,

                        onValueChange = {

                            semester = it
                        },

                        label = {

                            Text(
                                "Semester"
                            )
                        },

                        modifier =
                            Modifier.weight(1f),

                        singleLine = true
                    )

                    OutlinedTextField(

                        value =
                            section,

                        onValueChange = {

                            section = it
                        },

                        label = {

                            Text(
                                "Section"
                            )
                        },

                        modifier =
                            Modifier.weight(1f),

                        singleLine = true
                    )
                }
            }

            // =================================================
            // SUBJECT
            // =================================================

            item {

                OutlinedTextField(

                    value =
                        subject,

                    onValueChange = {

                        subject = it
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
            }

            // =================================================
            // FACULTY
            // =================================================

            item {

                OutlinedTextField(

                    value =
                        faculty,

                    onValueChange = {

                        faculty = it
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
            }

            // =================================================
            // APPLY + CLEAR
            // =================================================

            item {

                Row(

                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(

                        onClick = {

                            coroutineScope.launch {

                                loadReport()
                            }
                        },

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "Apply Filters"
                        )
                    }

                    OutlinedButton(

                        onClick = {

                            date =
                                SimpleDateFormat(
                                    "yyyy-MM-dd",
                                    Locale.getDefault()
                                ).format(Date())

                            department = ""
                            semester = ""
                            section = ""
                            subject = ""
                            faculty = ""

                            coroutineScope.launch {

                                loadReport()
                            }
                        },

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Icon(

                            imageVector =
                                Icons.Default.Clear,

                            contentDescription =
                                "Clear"
                        )

                        Spacer(
                            modifier =
                                Modifier.widthSafe(
                                    4.dp
                                )
                        )

                        Text(
                            "Clear"
                        )
                    }
                }
            }

            // =================================================
            // LOADING
            // =================================================

            if (isLoading) {

                item {

                    Row(

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),

                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        CircularProgressIndicator()
                    }
                }
            }

            // =================================================
            // ERROR
            // =================================================

            if (errorMessage.isNotBlank()) {

                item {

                    Card(

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(

                            text =
                                errorMessage,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .error,

                            modifier =
                                Modifier.padding(
                                    16.dp
                                )
                        )
                    }
                }
            }

            // =================================================
            // REPORT
            // =================================================

            report?.let { currentReport ->

                val summary =
                    currentReport.summary

                // =================================================
                // SUMMARY TITLE
                // =================================================

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

                // =================================================
                // SUMMARY
                // =================================================

                item {

                    Row(

                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        SummaryCard(
                            title = "Total",
                            value =
                                "${summary?.totalRecords ?: 0}",
                            modifier =
                                Modifier.weight(1f)
                        )

                        SummaryCard(
                            title = "Present",
                            value =
                                "${summary?.present ?: 0}",
                            modifier =
                                Modifier.weight(1f)
                        )

                        SummaryCard(
                            title = "Absent",
                            value =
                                "${summary?.absent ?: 0}",
                            modifier =
                                Modifier.weight(1f)
                        )
                    }
                }

                // =================================================
                // PERCENTAGE
                // =================================================

                item {

                    Card(

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Column(

                            modifier =
                                Modifier.padding(16.dp)
                        ) {

                            Text(

                                text =
                                    "Overall Attendance",

                                style =
                                    MaterialTheme
                                        .typography
                                        .labelLarge
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            Text(

                                text =
                                    "${summary?.attendancePercentage ?: 0.0}%",

                                style =
                                    MaterialTheme
                                        .typography
                                        .headlineMedium,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }

                // =================================================
                // ACTIVE FILTERS
                // =================================================

                item {

                    Card(

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Column(

                            modifier =
                                Modifier.padding(16.dp)
                        ) {

                            Text(

                                text =
                                    "Applied Filters",

                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(6.dp)
                            )

                            Text(
                                "Date: ${
                                    currentReport
                                        .filters
                                        ?.date
                                        ?: "-"
                                }"
                            )

                            Text(
                                "Department: ${
                                    currentReport
                                        .filters
                                        ?.department
                                        ?: "-"
                                }"
                            )

                            Text(
                                "Semester: ${
                                    currentReport
                                        .filters
                                        ?.semester
                                        ?: "-"
                                }"
                            )

                            Text(
                                "Section: ${
                                    currentReport
                                        .filters
                                        ?.section
                                        ?: "-"
                                }"
                            )

                            Text(
                                "Subject: ${
                                    currentReport
                                        .filters
                                        ?.subject
                                        ?: "-"
                                }"
                            )

                            Text(
                                "Faculty: ${
                                    currentReport
                                        .filters
                                        ?.faculty
                                        ?: "-"
                                }"
                            )
                        }
                    }
                }

                // =================================================
                // EXPORT / SHARE
                // =================================================

                item {

                    Button(

                        onClick = {

                            generateAndSharePdf(
                                context,
                                currentReport
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Icon(

                            imageVector =
                                Icons.Default.Share,

                            contentDescription =
                                "Share report"
                        )

                        Spacer(
                            modifier =
                                Modifier.widthSafe(
                                    8.dp
                                )
                        )

                        Text(
                            "Export & Share PDF"
                        )
                    }
                }

                // =================================================
                // RECORD COUNT
                // =================================================

                item {

                    Text(

                        text =
                            "Attendance Records (${currentReport.reports.size})",

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,

                        fontWeight =
                            FontWeight.Bold
                    )
                }

                // =================================================
                // EMPTY REPORT
                // =================================================

                if (currentReport.reports.isEmpty()) {

                    item {

                        Card(

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(

                                text =
                                    "No attendance records found for the selected filters.",

                                modifier =
                                    Modifier.padding(
                                        20.dp
                                    )
                            )
                        }
                    }

                } else {

                    // =================================================
                    // RECORDS
                    // =================================================

                    items(
                        currentReport.reports
                    ) { record ->

                        AttendanceReportCard(
                            record = record
                        )
                    }
                }
            }

            // =================================================
            // BOTTOM SPACE
            // =================================================

            item {

                Spacer(
                    modifier =
                        Modifier.height(80.dp)
                )
            }
        }
    }
}

// ============================================================
// SAFE WIDTH SPACER
// ============================================================

private fun Modifier.widthSafe(
    value: androidx.compose.ui.unit.Dp
): Modifier {

    return this.then(
        Modifier.padding(
            horizontal = value
        )
    )
}

// ============================================================
// SUMMARY CARD
// ============================================================

@Composable
private fun SummaryCard(

    title: String,

    value: String,

    modifier: Modifier =
        Modifier
) {

    Card(

        modifier =
            modifier
    ) {

        Column(

            modifier =
                Modifier.padding(12.dp)
        ) {

            Text(

                text =
                    title,

                style =
                    MaterialTheme
                        .typography
                        .labelMedium
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(

                text =
                    value,

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,

                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

// ============================================================
// ATTENDANCE RECORD CARD
// ============================================================

@Composable
private fun AttendanceReportCard(

    record: AttendanceReportItem
) {

    Card(

        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(

            modifier =
                Modifier.padding(16.dp)
        ) {

            Text(

                text =
                    record.name.ifBlank {
                        record.student_id
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
                    Modifier.height(6.dp)
            )

            Text(
                "Student ID: ${record.student_id}"
            )

            Text(
                "USN: ${
                    record.usn.ifBlank {
                        "-"
                    }
                }"
            )

            Text(
                "Department: ${
                    record.department
                }"
            )

            Text(
                "Semester: ${
                    record.semester
                }"
            )

            Text(
                "Section: ${
                    record.section
                }"
            )

            Text(
                "Subject: ${
                    record.subject
                }"
            )

            Text(
                "Faculty: ${
                    record.faculty
                }"
            )

            Text(
                "Date: ${
                    record.date
                }"
            )

            Text(
                "Time: ${
                    record.time
                }"
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(

                text =
                    "Status: ${
                        record.status
                    }",

                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

// ============================================================
// A7 — GENERATE PDF AND SHARE
// ============================================================

private fun generateAndSharePdf(

    context: Context,

    report: AttendanceReportResponse
) {

    try {

        val pdfDocument =
            PdfDocument()

        val pageWidth =
            595

        val pageHeight =
            842

        var pageNumber =
            1

        var pageInfo =
            PdfDocument.PageInfo
                .Builder(
                    pageWidth,
                    pageHeight,
                    pageNumber
                )
                .create()

        var page =
            pdfDocument.startPage(
                pageInfo
            )

        var canvas =
            page.canvas

        val paint =
            Paint()

        paint.textSize =
            18f

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "EduLens Attendance Report",
            40f,
            50f,
            paint
        )

        paint.textSize =
            11f

        paint.isFakeBoldText =
            false

        var y =
            80f

        val summary =
            report.summary

        canvas.drawText(
            "Total Records: ${
                summary?.totalRecords ?: 0
            }",
            40f,
            y,
            paint
        )

        y += 18f

        canvas.drawText(
            "Present: ${
                summary?.present ?: 0
            }",
            40f,
            y,
            paint
        )

        y += 18f

        canvas.drawText(
            "Absent: ${
                summary?.absent ?: 0
            }",
            40f,
            y,
            paint
        )

        y += 18f

        canvas.drawText(
            "Attendance: ${
                summary?.attendancePercentage ?: 0.0
            }%",
            40f,
            y,
            paint
        )

        y += 30f

        paint.isFakeBoldText =
            true

        canvas.drawText(
            "Student",
            40f,
            y,
            paint
        )

        canvas.drawText(
            "Subject",
            220f,
            y,
            paint
        )

        canvas.drawText(
            "Status",
            390f,
            y,
            paint
        )

        y += 20f

        paint.isFakeBoldText =
            false

        for (record in report.reports) {

            if (y > 790f) {

                pdfDocument.finishPage(
                    page
                )

                pageNumber++

                pageInfo =
                    PdfDocument.PageInfo
                        .Builder(
                            pageWidth,
                            pageHeight,
                            pageNumber
                        )
                        .create()

                page =
                    pdfDocument.startPage(
                        pageInfo
                    )

                canvas =
                    page.canvas

                y =
                    50f
            }

            val studentName =
                record.name
                    .ifBlank {
                        record.student_id
                    }

            canvas.drawText(
                studentName.take(24),
                40f,
                y,
                paint
            )

            canvas.drawText(
                record.subject.take(22),
                220f,
                y,
                paint
            )

            canvas.drawText(
                record.status.take(12),
                390f,
                y,
                paint
            )

            y += 20f
        }

        pdfDocument.finishPage(
            page
        )

        val documentsDirectory =
            context.getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS
            )

        if (documentsDirectory == null) {

            throw Exception(
                "Unable to access documents directory."
            )
        }

        if (!documentsDirectory.exists()) {

            documentsDirectory.mkdirs()
        }

        val file =
            File(
                documentsDirectory,
                "edulens_attendance_report.pdf"
            )

        file.outputStream().use { output ->

            pdfDocument.writeTo(
                output
            )
        }

        pdfDocument.close()

        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

        val shareIntent =
            Intent(
                Intent.ACTION_SEND
            ).apply {

                type =
                    "application/pdf"

                putExtra(
                    Intent.EXTRA_STREAM,
                    uri
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        context.startActivity(
            Intent.createChooser(
                shareIntent,
                "Share Attendance Report"
            )
        )

    } catch (e: Exception) {

        e.printStackTrace()
    }
}