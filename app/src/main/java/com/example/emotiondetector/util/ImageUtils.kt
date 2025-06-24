package com.example.emotiondetector.util

import android.graphics.*
import android.media.Image
import android.renderscript.*
import androidx.camera.core.ImageProxy
import androidx.core.graphics.scale
import com.example.emotiondetector.di.AppScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for image processing operations
 */
@AppScope
class ImageUtils @Inject constructor() {

    companion object {
        private const val TAG = "ImageUtils"
        
        // Standard emotion labels in the model's output order
        val EMOTION_LABELS = listOf("angry", "disgust", "fear", "happy", "neutral", "sad", "surprise")
    }
    
    /**
     * Convert an ImageProxy to a Bitmap
     */
    fun imageProxyToBitmap(image: ImageProxy, rotationDegrees: Int = 0): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        // Apply rotation if needed
        return if (rotationDegrees != 0) {
            rotateBitmap(bitmap, rotationDegrees.toFloat())
        } else {
            bitmap
        }
    }
    
    /**
     * Rotate a bitmap by the specified degrees
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
            postScale(-1f, -1f) // Flip to match camera preview
        }
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }
    
    /**
     * Crop a bitmap to a square from the center
     */
    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }
    
    /**
     * Resize a bitmap to the specified dimensions
     */
    fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return bitmap.scale(width, height, true)
    }
    
    /**
     * Convert a bitmap to grayscale
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    
    /**
     * Normalize pixel values to [-1, 1] range
     */
    fun normalizeBitmap(bitmap: Bitmap, mean: Float = 0f, std: Float = 255f): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return FloatArray(pixels.size) { i ->
            val pixel = pixels[i]
            val gray = (Color.red(pixel) * 0.299f + 
                       Color.green(pixel) * 0.587f + 
                       Color.blue(pixel) * 0.114f)
            (gray - mean) / std
        }
    }
    
    /**
     * Draw emotion probabilities on a bitmap
     */
    fun drawEmotionProbabilities(
        bitmap: Bitmap,
        probabilities: FloatArray,
        labels: List<String> = EMOTION_LABELS
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            textSize = 32f
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
        
        // Draw background for text
        val textBg = Paint().apply {
            color = Color.argb(128, 0, 0, 0)
            style = Paint.Style.FILL
        }
        
        // Calculate text bounds for background
        val textBounds = Rect()
        val textHeight = paint.fontMetrics.let { it.descent - it.ascent } + 4
        
        // Draw each emotion and its probability
        labels.forEachIndexed { index, label ->
            val prob = probabilities.getOrNull(index) ?: 0f
            val text = "$label: ${String.format("%.2f", prob)}"
            
            paint.getTextBounds(text, 0, text.length, textBounds)
            
            // Draw background
            canvas.drawRect(
                0f,
                index * textHeight,
                (textBounds.width() + 20).toFloat(),
                (index + 1) * textHeight,
                textBg
            )
            
            // Draw text
            canvas.drawText(
                text,
                10f,
                (index + 1) * textHeight - paint.fontMetrics.descent,
                paint
            )
        }
        
        return result
    }
    
    /**
     * Calculate the optimal preview size based on the target aspect ratio and available sizes
     */
    fun getOptimalPreviewSize(
        sizes: List<android.util.Size>,
        targetRatio: Float,
        maxWidth: Int = Int.MAX_VALUE,
        maxHeight: Int = Int.MAX_VALUE
    ): android.util.Size? {
        // Try to find a size with the target aspect ratio and within max dimensions
        val bigEnough = sizes.filter {
            val ratio = it.width.toFloat() / it.height
            Math.abs(ratio - targetRatio) <= 0.1f &&
                    it.width <= maxWidth &&
                    it.height <= maxHeight
        }
        
        // If we have any matching sizes, return the largest one
        if (bigEnough.isNotEmpty()) {
            return bigEnough.maxByOrNull { it.width * it.height }!!
        }
        
        // Otherwise, return the largest size that fits within max dimensions
        return sizes.filter {
            it.width <= maxWidth && it.height <= maxHeight
        }.maxByOrNull { it.width * it.height }
    }
}
