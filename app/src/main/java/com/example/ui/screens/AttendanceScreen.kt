package com.example.ui.screens

import android.Manifest
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class
)
@Composable
fun AttendanceScreen(
    subject: String,
    faculty: String,
    department: String,
    semester: String,
    section: String,
    onNavigateBack: () -> Unit,
    viewModel: AttendanceViewModel = viewModel()
) {

    // ============================================================
    // CAMERA PERMISSION
    // ============================================================

    val cameraPermissionState =
        rememberPermissionState(
            Manifest.permission.CAMERA
        )

    // ============================================================
    // STATES
    // ============================================================

    var isScanning by remember {
        mutableStateOf(false)
    }

    var imageCapture: ImageCapture? by remember {
        mutableStateOf(null)
    }

    var scanJob by remember {
        mutableStateOf<Job?>(null)
    }

    var showEndSessionDialog by remember {
        mutableStateOf(false)
    }

    val recognizedStudents by
        viewModel.recognizedStudents.collectAsState()

    val activeSession by
        viewModel.activeSession.collectAsState()

    val sessionLoading by
        viewModel.sessionLoading.collectAsState()

    val sessionError by
        viewModel.sessionError.collectAsState()

    val reviewOpen by
        viewModel.reviewOpen.collectAsState()

    val reviewStudents by
        viewModel.reviewStudents.collectAsState()

    val presentIds by
        viewModel.presentIds.collectAsState()

    val confirmMessage by
        viewModel.confirmMessage.collectAsState()

    var manualReason by remember {
        mutableStateOf("")
    }

    var showSaveConfirm by remember {
        mutableStateOf(false)
    }

    val context =
        LocalContext.current

    val coroutineScope =
        rememberCoroutineScope()

    // ============================================================
    // START SCANNING
    // ============================================================

    fun startScanning() {

        if (activeSession == null) {

            return
        }

        if (imageCapture == null) {

            return
        }

        isScanning = true

        scanJob?.cancel()

        scanJob =
            coroutineScope.launch(
                Dispatchers.IO
            ) {

                while (isScanning) {

                    try {

                        val photoFile =
                            File(
                                context.cacheDir,
                                "frame_${System.currentTimeMillis()}.jpg"
                            )

                        val executor =
                            ContextCompat
                                .getMainExecutor(
                                    context
                                )

                        takePhoto(

                            imageCapture!!,

                            executor,

                            photoFile,

                            onImageCaptured = { file ->

                                viewModel.processFrame(

                                    file =
                                        file,

                                    subject =
                                        subject,

                                    faculty =
                                        faculty,

                                    department =
                                        department,

                                    semester =
                                        semester,

                                    section =
                                        section
                                )
                            },

                            onError = {

                                println(
                                    "EDULENS_CAMERA: " +
                                        it.message
                                )
                            }
                        )

                    } catch (e: Exception) {

                        println(
                            "EDULENS_CAMERA: " +
                                e.message
                        )
                    }

                    delay(3000)
                }
            }
    }

    // ============================================================
    // STOP SCANNING
    // ============================================================

    fun stopScanning() {

        isScanning = false

        scanJob?.cancel()

        scanJob = null
    }

    // ============================================================
    // START SESSION
    // ============================================================

    fun startSession() {

        viewModel.startSession(

            subject =
                subject,

            faculty =
                faculty,

            department =
                department,

            semester =
                semester,

            section =
                section,

            onSuccess = {

                println(
                    "EDULENS_A8: Android session started"
                )
            }
        )
    }

    // ============================================================
    // END SESSION
    // ============================================================

    fun endSession() {

        stopScanning()

        viewModel.endSession(

            onSuccess = {

                println(
                    "EDULENS_A8: Android session ended"
                )

                showEndSessionDialog = false
            }
        )
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    DisposableEffect(Unit) {

        onDispose {

            stopScanning()
        }
    }

    // ============================================================
    // UI
    // ============================================================

    Scaffold(

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        "Attendance Session"
                    )
                },

                navigationIcon = {

                    IconButton(

                        onClick = {

                            if (
                                activeSession != null
                            ) {

                                showEndSessionDialog =
                                    true

                            } else {

                                onNavigateBack()
                            }
                        }
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
                },

                colors =
                    TopAppBarDefaults
                        .topAppBarColors(
                            containerColor =
                                Color.Black,

                            titleContentColor =
                                Color.White,

                            navigationIconContentColor =
                                Color.White
                        )
            )
        }

    ) { padding ->

        Box(

            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black
                    )
                    .padding(padding),

            contentAlignment =
                Alignment.Center
        ) {

            // ====================================================
            // CAMERA
            // ====================================================

            if (
                cameraPermissionState
                    .status
                    .isGranted
            ) {

                Box(

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                3f / 4f
                            )
                            .clip(
                                RoundedCornerShape(
                                    16.dp
                                )
                            )
                            .background(
                                Color(
                                    0xFF1E293B
                                )
                            )
                ) {

                    CameraPreview(

                        onImageCapture = {

                            capture ->

                            imageCapture =
                                capture
                        }
                    )

                    // ====================================================
                    // SESSION STATUS
                    // ====================================================

                    Column(

                        modifier =
                            Modifier
                                .align(
                                    Alignment.TopStart
                                )
                                .padding(16.dp)
                                .background(
                                    Color(
                                        0x99000000
                                    )
                                )
                                .padding(10.dp)
                    ) {

                        Text(

                            text =
                                if (
                                    activeSession != null
                                )
                                    "SESSION ACTIVE"
                                else
                                    "NO ACTIVE SESSION",

                            color =
                                if (
                                    activeSession != null
                                )
                                    Color(
                                        0xFF10B981
                                    )
                                else
                                    Color(
                                        0xFFF87171
                                    ),

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                "CLASS: $subject",

                            color =
                                Color.White
                        )

                        Text(
                            text =
                                "$department | Sem $semester | Sec $section",

                            color =
                                Color.White,

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )

                        Text(
                            text =
                                "FACULTY: $faculty",

                            color =
                                Color.White,

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall
                        )

                        activeSession
                            ?.let {

                                Text(

                                    text =
                                        "SESSION ID: ${it.sessionId}",

                                    color =
                                        Color.White,

                                    style =
                                        MaterialTheme
                                            .typography
                                            .labelSmall
                                )
                            }
                    }

                    // ====================================================
                    // SCANNING OVERLAY
                    // ====================================================

                    if (isScanning) {

                        Box(

                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 2.dp,

                                        color =
                                            Color(
                                                0xFF10B981
                                            ),

                                        shape =
                                            RoundedCornerShape(
                                                16.dp
                                            )
                                    )
                        )

                        Text(

                            text =
                                "Processing frames...",

                            color =
                                Color(
                                    0xFF10B981
                                ),

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,

                            fontWeight =
                                FontWeight.Bold,

                            modifier =
                                Modifier
                                    .align(
                                        Alignment
                                            .BottomStart
                                    )
                                    .padding(
                                        16.dp
                                    )
                        )
                    }
                }

            } else {

                Button(

                    onClick = {

                        cameraPermissionState
                            .launchPermissionRequest()
                    }
                ) {

                    Text(
                        "Grant Camera Permission"
                    )
                }
            }

            // ====================================================
            // BOTTOM CONTROLS
            // ====================================================

            Column(

                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter
                        )
                        .padding(
                            bottom = 16.dp
                        ),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                // =================================================
                // RECOGNIZED STUDENTS
                // =================================================

                if (
                    recognizedStudents
                        .isNotEmpty()
                ) {

                    Card(

                        modifier =
                            Modifier
                                .fillMaxWidth(
                                    0.9f
                                )
                                .height(
                                    150.dp
                                )
                                .padding(
                                    bottom = 16.dp
                                ),

                        colors =
                            CardDefaults
                                .cardColors(
                                    containerColor =
                                        MaterialTheme
                                            .colorScheme
                                            .surface
                                            .copy(
                                                alpha =
                                                    0.9f
                                            )
                                )
                    ) {

                        LazyColumn(

                            modifier =
                                Modifier
                                    .padding(
                                        8.dp
                                    )
                        ) {

                            items(
                                recognizedStudents
                                    .size
                            ) { index ->

                                val student =
                                    recognizedStudents[
                                        index
                                    ]

                                Row(

                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                4.dp
                                            ),

                                    horizontalArrangement =
                                        Arrangement
                                            .SpaceBetween,

                                    verticalAlignment =
                                        Alignment
                                            .CenterVertically
                                ) {

                                    Column {

                                        Text(

                                            text =
                                                student.name,

                                            fontWeight =
                                                FontWeight.Bold,

                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .onSurface
                                        )

                                        Text(

                                            text =
                                                "${student.studentId} • ${student.department}",

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

                                    Icon(

                                        imageVector =
                                            Icons
                                                .Default
                                                .Check,

                                        tint =
                                            Color(
                                                0xFF10B981
                                            ),

                                        contentDescription =
                                            "Present"
                                    )
                                }
                            }
                        }
                    }
                }

                // =================================================
                // SESSION BUTTON
                // =================================================

                if (
                    activeSession == null
                ) {

                    Button(

                        onClick = {

                            startSession()
                        },

                        enabled =
                            !sessionLoading &&
                                cameraPermissionState
                                    .status
                                    .isGranted
                    ) {

                        Text(
                            if (sessionLoading)
                                "Starting Session..."
                            else
                                "Start Attendance Session"
                        )
                    }

                } else {

                    Row(

                        horizontalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            ),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        // =========================================
                        // START / STOP SCAN
                        // =========================================

                        FloatingActionButton(

                            onClick = {

                                if (isScanning) {

                                    stopScanning()

                                } else {

                                    startScanning()
                                }
                            },

                            containerColor =
                                if (
                                    isScanning
                                )
                                    MaterialTheme
                                        .colorScheme
                                        .error
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .primary,

                            shape =
                                CircleShape,

                            modifier =
                                Modifier
                                    .size(
                                        72.dp
                                    )
                        ) {

                            Icon(

                                imageVector =
                                    if (
                                        isScanning
                                    )
                                        Icons
                                            .Default
                                            .Stop
                                    else
                                        Icons
                                            .Default
                                            .CameraAlt,

                                contentDescription =
                                    if (
                                        isScanning
                                    )
                                        "Stop Scan"
                                    else
                                        "Start Scan",

                                modifier =
                                    Modifier
                                        .size(
                                            32.dp
                                        )
                            )
                        }

                        // =========================================
                        // END SESSION
                        // =========================================

                        Button(

                            onClick = {

                                showEndSessionDialog =
                                    true
                            },

                            enabled =
                                !sessionLoading
                        ) {

                            Icon(

                                imageVector =
                                    Icons
                                        .Default
                                        .Stop,

                                contentDescription =
                                    "End Session"
                            )

                            Spacer(
                                Modifier.width(
                                    6.dp
                                )
                            )

                            Text(
                                "End Session"
                            )
                        }
                    }
                }

                // =================================================
                // ERROR
                // =================================================

                sessionError?.let { error ->

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    Text(

                        text =
                            error,

                        color =
                            Color(
                                0xFFF87171
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            }
        }
    }

    // ============================================================
    // TEACHER REVIEW: CHECK / UNCHECK, THEN SAVE
    // ============================================================

    if (reviewOpen) {

        val presentCount =
            reviewStudents.count { it.studentId in presentIds }

        AlertDialog(

            onDismissRequest = {
                viewModel.closeReview()
            },

            title = {
                Text("Review attendance")
            },

            text = {

                Column {

                    Text(
                        "On roster: ${reviewStudents.size}   " +
                            "Present: $presentCount   " +
                            "Absent: ${reviewStudents.size - presentCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        OutlinedButton(onClick = { viewModel.markAll(true) }) {
                            Text("All present")
                        }
                        OutlinedButton(onClick = { viewModel.markAll(false) }) {
                            Text("All absent")
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(reviewStudents) { student ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = student.studentId in presentIds,
                                    onCheckedChange = {
                                        viewModel.togglePresent(student.studentId)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        student.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        (student.usn ?: student.studentId) +
                                            if (student.detected) "  ·  camera match" else "  ·  not seen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = manualReason,
                        onValueChange = { manualReason = it },
                        label = { Text("Reason for manual changes (optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            },

            confirmButton = {
                Button(
                    enabled = !sessionLoading,
                    onClick = { showSaveConfirm = true }
                ) {
                    Text("Save attendance")
                }
            },

            dismissButton = {
                TextButton(onClick = { viewModel.closeReview() }) {
                    Text("Back")
                }
            }
        )
    }

    if (showSaveConfirm) {

        val presentCount =
            reviewStudents.count { it.studentId in presentIds }

        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("Save and lock?") },
            text = {
                Text(
                    "Present: $presentCount   Absent: ${reviewStudents.size - presentCount}   " +
                        "Total: ${reviewStudents.size}\n\n" +
                        "The register will be locked and parents of absent students will be notified."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveConfirm = false
                        viewModel.confirmAttendance(manualReason) {
                            manualReason = ""
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    confirmMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearConfirmMessage() },
            title = { Text("Attendance saved") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { viewModel.clearConfirmMessage() }) { Text("OK") }
            }
        )
    }

    // ============================================================
    // END SESSION CONFIRMATION
    // ============================================================

    if (
        showEndSessionDialog
    ) {

        AlertDialog(

            onDismissRequest = {

                showEndSessionDialog =
                    false
            },

            title = {

                Text(
                    "Finish and review?"
                )
            },

            text = {

                Text(
                    "Scanning stops and the class list opens. Check or uncheck students, then save. Nothing is saved until you confirm."
                )
            },

            confirmButton = {

                Button(

                    onClick = {

                        stopScanning()
                        showEndSessionDialog = false
                        viewModel.openReview()
                    }
                ) {

                    Text(
                        "Review list"
                    )
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showEndSessionDialog =
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
}