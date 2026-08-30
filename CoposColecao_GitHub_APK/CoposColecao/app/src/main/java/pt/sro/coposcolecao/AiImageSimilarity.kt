package pt.sro.coposcolecao

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import java.io.Closeable
import java.io.File

class AiImageSimilarity(context: Context) : Closeable {
    private val embedder: ImageEmbedder

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_PATH)
            .build()

        val options = ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(baseOptions)
            .build()

        embedder = ImageEmbedder.createFromOptions(context, options)
    }

    fun similarity(firstPath: String, secondPath: String): Int {
        val first = BitmapFactory.decodeFile(firstPath)
            ?: error("Não foi possível ler ${File(firstPath).name}")
        val second = BitmapFactory.decodeFile(secondPath)
            ?: error("Não foi possível ler ${File(secondPath).name}")

        try {
            val firstEmbedding = embedder
                .embed(BitmapImageBuilder(first).build())
                .embeddingResult()
                .embeddings()
                .first()

            val secondEmbedding = embedder
                .embed(BitmapImageBuilder(second).build())
                .embeddingResult()
                .embeddings()
                .first()

            val cosine = ImageEmbedder.cosineSimilarity(firstEmbedding, secondEmbedding)
            return (cosine.coerceIn(0.0, 1.0) * 100.0).toInt()
        } finally {
            first.recycle()
            second.recycle()
        }
    }

    override fun close() {
        embedder.close()
    }

    companion object {
        const val MODEL_PATH = "mobilenet_v3_small.tflite"
    }
}
