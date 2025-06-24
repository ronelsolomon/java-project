package com.example.emotiondetector.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions as TFLiteBaseOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Handles emotion detection using MediaPipe for face detection and TFLite for emotion classification
 */
class EmotionDetector(
    private val context: Context,
    private val modelPath: String = "emotion_model.tflite",
    private val maxResults: Int = 5,
    private val threshold: Float = 0.3f
) {
    private var faceDetector: FaceDetector? = null
    private var emotionClassifier: ImageClassifier? = null
    private var isInitialized = false
    
    private val modelInputSize = 48 // Input size expected by the emotion model
    
    init {
        initialize()
    }
    
    private fun initialize() {
        try {
            // Initialize MediaPipe Face Detector
            val faceDetectorOptions = com.google.mediapipe.tasks.vision.facedetector.FaceDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("face_detection_short_range.tflite") // Bundled with MediaPipe
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setMinDetectionConfidence(0.5f)
                .build()
            
            faceDetector = FaceDetector.createFromOptions(context, faceDetectorOptions)
            
            // Initialize TFLite Emotion Classifier
            val baseOptions = TFLiteBaseOptions.builder()
                .setNumThreads(4)
                .build()
                
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(maxResults)
                .setScoreThreshold(threshold)
                .build()
                
            emotionClassifier = ImageClassifier.createFromFileAndOptions(
                context,
                modelPath,
                options
            )
            
            isInitialized = true
        } catch (e: Exception) {
            throw RuntimeException("Error initializing EmotionDetector", e)
        }
    }
    
    suspend fun detectEmotions(imageProxy: ImageProxy): List<EmotionResult> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext emptyList()
        }
        
        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxy.toBitmap()
            
            // Detect faces
            val faceDetector = faceDetector ?: return@withContext emptyList()
            val mpImage = com.google.mediapipe.tasks.vision.core.Image(
                com.google.mediapipe.tasks.vision.core.Image.IMAGE_FORMAT_RGBA,
                bitmap
            )
            
            val detectionResult = faceDetector.detect(mpImage)
            
            if (detectionResult.detections().isEmpty()) {
                return@withContext emptyList()
            }
            
            // Process each detected face
            val results = mutableListOf<EmotionResult>()
            
            for (detection in detectionResult.detections()) {
                val boundingBox = detection.boundingBox()
                
                // Extract face region
                val faceBitmap = cropBitmap(bitmap, boundingBox)
                
                // Classify emotion
                val emotion = classifyEmotion(faceBitmap)
                
                if (emotion != null) {
                    results.add(
                        EmotionResult(
                            boundingBox = boundingBox,
                            emotion = emotion.first,
                            confidence = emotion.second
                        )
                    )
                }
            }
            
            return@withContext results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            imageProxy.close()
        }
    }
    
    private suspend fun classifyEmotion(bitmap: Bitmap): Pair<String, Float>? = withContext(Dispatchers.Default) {
        try {
            val classifier = emotionClassifier ?: return@withContext null
            
            // Convert to grayscale if needed by the model
            val processedBitmap = convertToGrayscale(bitmap)
            
            // Convert to TensorImage
            val tensorImage = TensorImage.fromBitmap(processedBitmap)
            
            // Run inference
            val results = classifier.classify(tensorImage)
            
            // Get top result
            if (results.isNotEmpty() && results[0].categories.isNotEmpty()) {
                val topCategory = results[0].categories.maxByOrNull { it.score }
                topCategory?.let { it.label to it.score }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun cropBitmap(bitmap: Bitmap, boundingBox: RectF): Bitmap {
        val x = max(0f, boundingBox.left)
        val y = max(0f, boundingBox.top)
        val width = min(bitmap.width - x, boundingBox.width())
        val height = min(bitmap.height - y, boundingBox.height())
        
        val cropped = Bitmap.createBitmap(
            bitmap,
            x.toInt(),
            y.toInt(),
            width.toInt(),
            height.toInt()
        )
        
        // Resize to model input size
        return Bitmap.createScaledBitmap(
            cropped,
            modelInputSize,
            modelInputSize,
            true
        )
    }
    
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Convert to grayscale using the luminosity method
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // Luminosity method (0.21 R + 0.72 G + 0.07 B)
            val gray = (0.21 * r + 0.72 * g + 0.07 * b).toInt()
            
            pixels[i] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }
        
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
    
    fun close() {
        faceDetector?.close()
        emotionClassifier = null
        isInitialized = false
    }
}

data class EmotionResult(
    val boundingBox: RectF,
    val emotion: String,
    val confidence: Float
)

// Extension function to convert ImageProxy to Bitmap
private fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    
    val nv21 = ByteArray(ySize + uSize + vSize)
    
    // Y buffer is not always the first in the array, need to handle padding
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    
    // Convert NV21 to ARGB_8888
    val yuvImage = android.graphics.YuvImage(
        nv21, 
        android.graphics.ImageFormat.NV21, 
        width, 
        height, 
        null
    )
    
    val outputStream = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        android.graphics.Rect(0, 0, width, height), 
        100, 
        outputStream
    )
    
    val jpegArray = outputStream.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.size)
}
