package com.example.emotiondetector.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.emotiondetector.R
import com.example.emotiondetector.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onError: (String) -> Unit = {},
    onEmotionDetected: (String, Float) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    // Camera state
    var hasCamPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA) {
        hasCamPermission = it
        if (!it) {
            onError("Camera permission is required for emotion detection")
        }
    }
    
    // Camera provider
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var cameraSelector by remember { 
        mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) 
    }
    
    // Request permission when the screen is first launched
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Set up the camera when permission is granted
    if (hasCamPermission) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                
                // Set up camera use cases
                coroutineScope.launch {
                    val cameraProvider = cameraProviderFuture.await()
                    
                    // Set up the preview use case
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    previewUseCase = preview
                    
                    // Set up the image analysis use case
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    
                    imageAnalysis.setAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        { imageProxy ->
                            // Process the image for emotion detection
                            // This will be implemented in the next step
                            imageProxy.close()
                        }
                    )
                    
                    try {
                        // Unbind all use cases before binding new ones
                        cameraProvider.unbindAll()
                        
                        // Bind use cases to the lifecycle
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        onError("Failed to start camera: ${e.message}")
                    }
                }
                
                previewView
            }
        )
        
        // Camera controls overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Switch camera button
            FloatingActionButton(
                onClick = {
                    cameraSelector = when (cameraSelector) {
                        CameraSelector.DEFAULT_FRONT_CAMERA -> CameraSelector.DEFAULT_BACK_CAMERA
                        else -> CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    // Restart preview with new camera selector
                    previewUseCase?.targetRotation = when (cameraSelector.lensFacing) {
                        CameraSelector.LENS_FACING_FRONT -> 270
                        else -> 90
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch camera"
                )
            }
            
            // Emotion result overlay
            EmotionResultOverlay(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
            )
        }
    } else {
        // Permission not granted UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Permission required",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please grant camera permission to enable emotion detection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
private fun EmotionResultOverlay(
    modifier: Modifier = Modifier,
    emotion: String = "Neutral",
    confidence: Float = 0f
) {
    val emotionColor = when (emotion.lowercase()) {
        "happy" -> HappyColor
        "sad" -> SadColor
        "angry" -> AngryColor
        "surprise" -> SurpriseColor
        "fear" -> FearColor
        "disgust" -> DisgustColor
        else -> NeutralColor
    }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(emotionColor, MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${emotion.replaceFirstChar { it.uppercase() }}: ${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PreviewCameraScreen() {
    EmotionDetectorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            CameraScreen(
                onError = {},
                onEmotionDetected = { _, _ -> }
            )
        }
    }
}
