package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
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
import com.example.api.ApiClient
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val ENROLLMENT_SAMPLE_COUNT = 8

private val enrollmentInstructions = listOf(
    "Look straight at the camera",
    "Slightly turn your face LEFT",
    "Slightly turn your face RIGHT",
    "Look slightly UP",
    "Look slightly DOWN",
    "Keep a natural expression",
    "Move slightly closer",
    "Move slightly farther away"
)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class
)
@Composable
fun EnrollmentScreen(
    onNavigateBack: () -> Unit
) {
    val cameraPermissionState =
        rememberPermissionState(Manifest.permission.CAMERA)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var imageCapture: ImageCapture? by remember {
        mutableStateOf(null)
    }

    var studentName by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var usn by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    var isCapturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableStateOf(0f) }
    var currentSample by remember { mutableStateOf(0) }

    var currentInstruction by remember {
        mutableStateOf(enrollmentInstructions.first())
    }

    var enrollmentSuccess by remember {
        mutableStateOf(false)
    }

    var enrollmentError by remember {
        mutableStateOf<String?>(null)
    }

    var serverMessage by remember {
        mutableStateOf<String?>(null)
    }

    var embeddingsStored by remember {
        mutableStateOf<Int?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Student Enrollment")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor =
                            MaterialTheme.colorScheme.background,
                        titleContentColor =
                            MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor =
                            MaterialTheme.colorScheme.onSurface
                    )
            )
        },
        containerColor =
            MaterialTheme.colorScheme.background
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            item {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(
                            RoundedCornerShape(24.dp)
                        )
                        .background(
                            MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    if (
                        cameraPermissionState
                            .status
                            .isGranted
                    ) {

                        CameraPreview(
                            onImageCapture = {
                                capture ->
                                imageCapture = capture
                            }
                        )

                        if (isCapturing) {

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        4.dp,
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .background(
                                        Color(0x88000000)
                                    ),
                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Column(
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally
                                ) {

                                    CircularProgressIndicator(
                                        progress = {
                                            captureProgress
                                        },
                                        modifier =
                                            Modifier.size(64.dp),
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                        strokeWidth = 8.dp
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.height(16.dp)
                                    )

                                    Text(
                                        text =
                                            "Capturing " +
                                            "$currentSample / " +
                                            "$ENROLLMENT_SAMPLE_COUNT",
                                        color =
                                            Color.White,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .titleMedium,
                                        fontWeight =
                                            FontWeight.Bold
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.height(8.dp)
                                    )

                                    Text(
                                        text =
                                            currentInstruction,
                                        color =
                                            Color.White,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyLarge
                                    )
                                }
                            }

                        } else if (enrollmentSuccess) {

                            Column(
                                modifier =
                                    Modifier.fillMaxSize(),
                                horizontalAlignment =
                                    Alignment.CenterHorizontally,
                                verticalArrangement =
                                    Arrangement.Center
                            ) {

                                Icon(
                                    imageVector =
                                        Icons.Default.PersonAdd,
                                    contentDescription =
                                        "Success",
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                    modifier =
                                        Modifier.size(64.dp)
                                )

                                Spacer(
                                    modifier =
                                        Modifier.height(16.dp)
                                )

                                Text(
                                    text =
                                        "Enrollment Complete",
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                embeddingsStored?.let { count ->

                                    Spacer(
                                        modifier =
                                            Modifier.height(8.dp)
                                    )

                                    Text(
                                        text =
                                            "$count face embeddings stored",
                                        color =
                                            Color.White,
                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodyMedium
                                    )
                                }
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
                }

                Spacer(
                    modifier =
                        Modifier.height(24.dp)
                )

                if (
                    !isCapturing &&
                    !enrollmentSuccess
                ) {

                    Text(
                        text = "Face Enrollment",
                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "The system will capture " +
                            "$ENROLLMENT_SAMPLE_COUNT " +
                            "real face samples and send them " +
                            "to the EduLens server.",
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(24.dp)
                    )
                }

                OutlinedTextField(
                    value = studentName,
                    onValueChange = {
                        studentName = it
                    },
                    label = {
                        Text("Student Name")
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCapturing
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = studentId,
                    onValueChange = {
                        studentId = it
                    },
                    label = {
                        Text("Student ID")
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCapturing
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = usn,
                    onValueChange = {
                        usn = it
                    },
                    label = {
                        Text("USN (e.g. 1SV23CS001)")
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCapturing
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = department,
                    onValueChange = {
                        department = it
                    },
                    label = {
                        Text("Department")
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCapturing
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(16.dp)
                ) {

                    OutlinedTextField(
                        value = semester,
                        onValueChange = {
                            semester = it
                        },
                        label = {
                            Text("Semester")
                        },
                        modifier =
                            Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isCapturing
                    )

                    OutlinedTextField(
                        value = section,
                        onValueChange = {
                            section = it
                        },
                        label = {
                            Text("Section")
                        },
                        modifier =
                            Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isCapturing
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                    },
                    label = {
                        Text("Email")
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCapturing
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                    },
                    label = {
                        Text("Phone Number")
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isCapturing
                )

                Spacer(
                    modifier =
                        Modifier.height(24.dp)
                )

                enrollmentError?.let { error ->

                    Text(
                        text = error,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )
                }

                serverMessage?.let { message ->

                    Text(
                        text = message,
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )
                }

                Button(
                    onClick = {

                        if (
                            studentName.isBlank() ||
                            studentId.isBlank()
                        ) {
                            enrollmentError =
                                "Student Name and Student ID are required."
                            return@Button
                        }

                        val capture = imageCapture

                        if (capture == null) {
                            enrollmentError =
                                "Camera is not ready."
                            return@Button
                        }

                        enrollmentError = null
                        serverMessage = null
                        embeddingsStored = null
                        enrollmentSuccess = false
                        isCapturing = true
                        captureProgress = 0f
                        currentSample = 0

                        coroutineScope.launch {

                            var datasetDir: File? = null

                            try {

                                // ---------------------------------------------
                                // TEMPORARY LOCAL ENROLLMENT DIRECTORY
                                // ---------------------------------------------

                                datasetDir =
                                    File(
                                        context.filesDir,
                                        "dataset/$studentId"
                                    )

                                if (datasetDir.exists()) {

                                    datasetDir
                                        .listFiles()
                                        ?.forEach {
                                            it.delete()
                                        }
                                }

                                datasetDir.mkdirs()

                                val executor =
                                    ContextCompat
                                        .getMainExecutor(
                                            context
                                        )

                                val capturedFiles =
                                    mutableListOf<File>()

                                // ---------------------------------------------
                                // CAPTURE 8 FACE SAMPLES
                                // ---------------------------------------------

                                for (
                                    index in 0 until
                                        ENROLLMENT_SAMPLE_COUNT
                                ) {

                                    currentSample =
                                        index + 1

                                    currentInstruction =
                                        enrollmentInstructions[
                                            index
                                        ]

                                    delay(1000)

                                    val photoFile =
                                        File(
                                            datasetDir,
                                            "img_${index + 1}.jpg"
                                        )

                                    val savedFile =
                                        capturePhoto(
                                            imageCapture =
                                                capture,
                                            executor =
                                                executor,
                                            outputFile =
                                                photoFile
                                        )

                                    if (
                                        !savedFile.exists() ||
                                        savedFile.length() <= 0L
                                    ) {
                                        throw Exception(
                                            "Photo ${index + 1} " +
                                            "was not saved correctly."
                                        )
                                    }

                                    capturedFiles.add(
                                        savedFile
                                    )

                                    captureProgress =
                                        (index + 1).toFloat() /
                                        ENROLLMENT_SAMPLE_COUNT

                                    Log.d(
                                        "EDULENS_ENROLL",
                                        "Captured " +
                                        "${index + 1}/" +
                                        "$ENROLLMENT_SAMPLE_COUNT"
                                    )
                                }

                                if (
                                    capturedFiles.size !=
                                    ENROLLMENT_SAMPLE_COUNT
                                ) {
                                    throw Exception(
                                        "Only " +
                                        "${capturedFiles.size}/" +
                                        "$ENROLLMENT_SAMPLE_COUNT " +
                                        "photos were captured."
                                    )
                                }

                                // ---------------------------------------------
                                // GET JWT TOKEN
                                // ---------------------------------------------

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
                                    throw Exception(
                                        "Authentication token not found. " +
                                        "Please login again."
                                    )
                                }

                                // ---------------------------------------------
                                // CREATE TEXT MULTIPART FIELDS
                                // ---------------------------------------------

                                val textMediaType =
                                    "text/plain"
                                        .toMediaTypeOrNull()

                                val fields =
                                    mapOf(
                                        "studentId" to
                                            studentId.toRequestBody(
                                                textMediaType
                                            ),

                                        "name" to
                                            studentName.toRequestBody(
                                                textMediaType
                                            ),

                                        "usn" to
                                            usn.toRequestBody(
                                                textMediaType
                                            ),

                                        "department" to
                                            department.toRequestBody(
                                                textMediaType
                                            ),

                                        "semester" to
                                            semester.toRequestBody(
                                                textMediaType
                                            ),

                                        "section" to
                                            section.toRequestBody(
                                                textMediaType
                                            ),

                                        "email" to
                                            email.toRequestBody(
                                                textMediaType
                                            ),

                                        "phoneNumber" to
                                            phone.toRequestBody(
                                                textMediaType
                                            )
                                    )

                                // ---------------------------------------------
                                // CREATE IMAGE MULTIPART PARTS
                                // ---------------------------------------------

                                val imageParts =
                                    capturedFiles.mapIndexed {
                                        index,
                                        file ->

                                        val requestBody =
                                            file.asRequestBody(
                                                "image/jpeg"
                                                    .toMediaTypeOrNull()
                                            )

                                        MultipartBody.Part
                                            .createFormData(
                                                "images",
                                                "enroll_${index + 1}.jpg",
                                                requestBody
                                            )
                                    }

                                Log.d(
                                    "EDULENS_ENROLL",
                                    "Uploading " +
                                    "${imageParts.size} images to Flask"
                                )

                                // ---------------------------------------------
                                // CALL FLASK /api/enroll
                                // ---------------------------------------------

                                val response =
                                    ApiClient
                                        .flaskApiService
                                        .enrollStudent(
                                            authorization =
                                                "Bearer $token",
                                            fields =
                                                fields,
                                            images =
                                                imageParts
                                        )

                                Log.d(
                                    "EDULENS_ENROLL",
                                    "Server response success = " +
                                    response.success
                                )

                                Log.d(
                                    "EDULENS_ENROLL",
                                    "Server message = " +
                                    response.message
                                )

                                // ---------------------------------------------
                                // HANDLE SERVER RESPONSE
                                // ---------------------------------------------

                                if (!response.success) {

                                    throw Exception(
                                        response.message.ifBlank {
                                            "Server rejected enrollment."
                                        }
                                    )
                                }

                                embeddingsStored =
                                    response.embeddingsStored

                                serverMessage =
                                    response.message

                                enrollmentSuccess = true
                                isCapturing = false
                                captureProgress = 1f
                                currentSample =
                                    ENROLLMENT_SAMPLE_COUNT

                                Log.d(
                                    "EDULENS_ENROLL",
                                    "ENROLLMENT SUCCESS"
                                )

                                // ---------------------------------------------
                                // DELETE TEMPORARY LOCAL FILES
                                // ---------------------------------------------

                                capturedFiles.forEach { file ->

                                    try {
                                        if (file.exists()) {
                                            file.delete()
                                        }
                                    } catch (
                                        cleanupException: Exception
                                    ) {
                                        Log.w(
                                            "EDULENS_ENROLL",
                                            "Could not delete " +
                                            file.name,
                                            cleanupException
                                        )
                                    }
                                }

                            } catch (e: Exception) {

                                Log.e(
                                    "EDULENS_ENROLL",
                                    "Enrollment failed",
                                    e
                                )

                                isCapturing = false
                                enrollmentSuccess = false

                                enrollmentError =
                                    when {
                                        e.message
                                            ?.contains(
                                                "timeout",
                                                ignoreCase = true
                                            ) == true ->
                                            "Server connection timed out. " +
                                            "Check that the laptop and phone " +
                                            "are on the same network."

                                        e.message
                                            ?.contains(
                                                "Unable to resolve host",
                                                ignoreCase = true
                                            ) == true ->
                                            "Cannot reach EduLens server. " +
                                            "Check the laptop IP address."

                                        e.message
                                            ?.contains(
                                                "401",
                                                ignoreCase = true
                                            ) == true ->
                                            "Authentication failed. " +
                                            "Please login again."

                                        e.message
                                            ?.contains(
                                                "403",
                                                ignoreCase = true
                                            ) == true ->
                                            "Only an administrator can enroll students."

                                        else ->
                                            "Enrollment failed: " +
                                            (
                                                e.message
                                                    ?: "Unknown error"
                                            )
                                    }

                            } finally {

                                isCapturing = false

                                // ---------------------------------------------
                                // REMOVE TEMPORARY DIRECTORY
                                // ---------------------------------------------

                                datasetDir?.let { directory ->

                                    try {

                                        if (
                                            directory.exists() &&
                                            directory.listFiles()
                                                ?.isEmpty() == true
                                        ) {
                                            directory.delete()
                                        }

                                    } catch (
                                        cleanupException: Exception
                                    ) {

                                        Log.w(
                                            "EDULENS_ENROLL",
                                            "Temporary directory cleanup failed",
                                            cleanupException
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    enabled =
                        !isCapturing &&
                        studentName.isNotBlank() &&
                        studentId.isNotBlank() &&
                        imageCapture != null
                ) {

                    if (isCapturing) {

                        Text(
                            if (
                                currentSample <
                                ENROLLMENT_SAMPLE_COUNT
                            ) {
                                "Capturing Face Samples..."
                            } else {
                                "Processing Enrollment..."
                            }
                        )

                    } else {

                        Text(
                            "Start Enrollment Scan",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(24.dp)
                )
            }
        }
    }
}

/**
 * Capture one real CameraX image and suspend until
 * ImageCapture reports success or failure.
 */
private suspend fun capturePhoto(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    outputFile: File
): File =
    suspendCoroutine { continuation ->

        takePhoto(
            imageCapture = imageCapture,
            executor = executor,
            outputFile = outputFile,
            onImageCaptured = { file ->
                continuation.resume(file)
            },
            onError = { exception ->
                continuation.resumeWithException(
                    exception
                )
            }
        )
    }