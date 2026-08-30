package pt.sro.coposcolecao

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs

object ImageSimilarity {
    /**
     * dHash 64-bit. É totalmente local e rápido.
     * Bom para detetar fotos iguais ou muito parecidas; em versões futuras
     * pode ser trocado por embeddings TFLite para maior robustez.
     */
    fun dHash(path: String): Long {
        val bitmap = BitmapFactory.decodeFile(path)
            ?: throw IllegalArgumentException("Não foi possível ler a imagem.")
        return dHash(bitmap)
    }

    fun dHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int =
        java.lang.Long.bitCount(a xor b)

    fun similarityPercent(a: Long, b: Long): Int {
        val distance = hammingDistance(a, b)
        return ((64 - distance) * 100 / 64)
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}
