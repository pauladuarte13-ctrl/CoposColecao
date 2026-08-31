package pt.sro.coposcolecao

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class LogoTextRecognizer(private val context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(path: String): String {
        // O OCR não precisa da fotografia na resolução total da câmara.
        // Reduzir antes de enviar ao ML Kit diminui bastante o tempo e a memória,
        // mantendo resolução suficiente para ler logótipos/inscrições.
        val bitmap = decodeForOcr(path, 1600)
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().text.trim()
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeForOcr(path: String, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide ||
            bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }

        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Não foi possível ler a fotografia para OCR.")
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
}
