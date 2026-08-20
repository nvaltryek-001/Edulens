package com.example.ui.screens

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCapture: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var previewView by remember {
        mutableStateOf<PreviewView?>(null)
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            )
            .build()
    }

    LaunchedEffect(previewView) {
        previewView?.let { view ->

            try {
                Log.d(
                    "EDULENS_CAMERA",
                    "Initializing camera..."
                )

                val cameraProvider =
                    context.getCameraProvider()

                val preview =
                    Preview.Builder()
                        .build()
                        .also {
                            it.setSurfaceProvider(
                                view.surfaceProvider
                            )
                        }

                val cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                Log.d(
                    "EDULENS_CAMERA",
                    "Camera bound successfully"
                )

                onImageCapture(imageCapture)

                Log.d(
                    "EDULENS_CAMERA",
                    "ImageCapture delivered to AttendanceScreen"
                )

            } catch (exc: Exception) {

                Log.e(
                    "EDULENS_CAMERA",
                    "Camera binding failed",
                    exc
                )
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType =
                    PreviewView.ScaleType.FILL_CENTER

                previewView = this

                Log.d(
                    "EDULENS_CAMERA",
                    "PreviewView created"
                )
            }
        },
        modifier = modifier.fillMaxSize()
    )
}

suspend fun Context.getCameraProvider():
    ProcessCameraProvider =
    suspendCoroutine { continuation ->

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            {
                try {
                    continuation.resume(
                        cameraProviderFuture.get()
                    )
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

fun takePhoto(
    imageCapture: ImageCapture,
    executor: Executor,
    outputFile: File,
    onImageCaptured: (File) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {

    Log.d(
        "EDULENS_CAMERA",
        "takePhoto() called"
    )

    Log.d(
        "EDULENS_CAMERA",
        "Output file: ${outputFile.absolutePath}"
    )

    val outputOptions =
        ImageCapture.OutputFileOptions
            .Builder(outputFile)
            .build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {

            override fun onImageSaved(
                outputFileResults:
                    ImageCapture.OutputFileResults
            ) {

                Log.d(
                    "EDULENS_CAMERA",
                    "Photo saved successfully"
                )

                Log.d(
                    "EDULENS_CAMERA",
                    "File exists: ${outputFile.exists()}"
                )

                Log.d(
                    "EDULENS_CAMERA",
                    "File size: ${outputFile.length()} bytes"
                )

                onImageCaptured(outputFile)
            }

            override fun onError(
                exception: ImageCaptureException
            ) {

                Log.e(
                    "EDULENS_CAMERA",
                    "Photo capture failed",
                    exception
                )

                onError(exception)
            }
        }
    )
}