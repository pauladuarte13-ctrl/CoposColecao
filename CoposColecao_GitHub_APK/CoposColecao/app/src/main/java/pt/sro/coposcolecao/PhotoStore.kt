package pt.sro.coposcolecao

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class PhotoStore(private val context: Context) {
    private val photosDir = File(context.filesDir, "photos").apply { mkdirs() }

    fun import(uri: Uri): String {
        val target = File(photosDir, "glass_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir a fotografia." }
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }

    fun delete(path: String) {
        runCatching { File(path).delete() }
    }
}
