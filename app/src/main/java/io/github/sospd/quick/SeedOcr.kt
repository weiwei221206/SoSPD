package io.github.sospd.quick

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.sospd.domain.OcrSeedParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

internal class SeedOcr : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): List<String> {
        val first = recognizeOnce(bitmap, invert = false)
        if (first.isNotEmpty()) return first
        return recognizeOnce(bitmap, invert = true)
    }

    private suspend fun recognizeOnce(input: Bitmap, invert: Boolean): List<String> = suspendCancellableCoroutine { continuation ->
        // The task owns its bitmap until completion, even when its caller cancels.
        val scale = min(3f, max(1f, 100f / input.height)).coerceAtMost(2400f / input.width)
        val resized = Bitmap.createScaledBitmap(input, max(1, (input.width * scale).toInt()), max(1, (input.height * scale).toInt()), true)
        val image = resized.copy(Bitmap.Config.ARGB_8888, true)
        if (resized !== input) resized.recycle()
        if (invert) {
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            for (i in pixels.indices) {
                val gray = 255 - ((Color.red(pixels[i]) * 30 + Color.green(pixels[i]) * 59 + Color.blue(pixels[i]) * 11) / 100)
                pixels[i] = Color.rgb(gray, gray, gray)
            }
            image.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        }
        try {
            recognizer.process(InputImage.fromBitmap(image, 0))
                .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(OcrSeedParser.candidates(result.text)) }
                .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
                .addOnCompleteListener { image.recycle() }
        } catch (error: Exception) {
            image.recycle()
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    override fun close() { recognizer.close() }
}
