package pt.sro.coposcolecao

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val repository: GlassRepository
) {
    suspend fun exportTo(uri: Uri): Int {
        val items = repository.allOnce()
        val resolver = context.contentResolver
        resolver.openOutputStream(uri)?.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                val manifest = JSONObject().apply {
                    put("format", "copos-colecao-backup")
                    put("version", 1)
                    put("createdAt", System.currentTimeMillis())
                    put("items", JSONArray().apply {
                        items.forEach { item ->
                            put(JSONObject().apply {
                                put("sequenceNumber", item.sequenceNumber)
                                put("brand", item.brand)
                                put("description", item.description)
                                put("photo", "photos/${item.sequenceNumber}.jpg")
                                put("imageHash", item.imageHash.toString())
                                put("createdAt", item.createdAt)
                                put("updatedAt", item.updatedAt)
                            })
                        }
                    })
                }

                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                items.forEach { item ->
                    val photo = File(item.photoPath)
                    if (photo.exists()) {
                        zip.putNextEntry(ZipEntry("photos/${item.sequenceNumber}.jpg"))
                        photo.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("Não foi possível criar o ficheiro de backup.")
        return items.size
    }

    suspend fun restoreFrom(uri: Uri): Int {
        val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply {
            mkdirs()
        }

        try {
            var manifestText: String? = null
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name == "manifest.json") {
                            manifestText = zip.readBytes().toString(Charsets.UTF_8)
                        } else if (entry.name.startsWith("photos/") && !entry.isDirectory) {
                            val safeName = File(entry.name).name
                            File(tempDir, safeName).outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("Não foi possível abrir o backup.")

            val manifest = JSONObject(
                manifestText ?: error("Backup inválido: falta manifest.json.")
            )
            require(manifest.optString("format") == "copos-colecao-backup") {
                "O ficheiro não é um backup da Coleção de Copos."
            }

            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            val array = manifest.getJSONArray("items")
            val restored = mutableListOf<GlassEntity>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val number = obj.getInt("sequenceNumber")
                val source = File(tempDir, "$number.jpg")
                require(source.exists()) { "Falta a fotografia do copo #$number." }

                val target = File(
                    photosDir,
                    "glass_restore_${number}_${System.currentTimeMillis()}.jpg"
                )
                source.copyTo(target, overwrite = true)

                restored += GlassEntity(
                    sequenceNumber = number,
                    brand = obj.optString("brand"),
                    description = obj.optString("description"),
                    photoPath = target.absolutePath,
                    imageHash = obj.optString("imageHash", "0").toLongOrNull() ?: 0L,
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }

            // Só substitui a coleção depois de validar e copiar todo o conteúdo.
            val oldPhotos = repository.allOnce().map { it.photoPath }
            repository.replaceAll(restored)
            oldPhotos.forEach { runCatching { File(it).delete() } }
            return restored.size
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
