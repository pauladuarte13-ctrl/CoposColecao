package pt.sro.coposcolecao

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

data class SimilarGlass(
    val glass: GlassEntity,
    val similarity: Int
)

class GlassRepository(private val db: AppDatabase) {
    private val dao = db.glassDao()

    fun observeAll(): Flow<List<GlassEntity>> = dao.observeAll()
    fun search(query: String): Flow<List<GlassEntity>> = dao.search(query)

    suspend fun allOnce(): List<GlassEntity> = dao.getAllOnce()

    suspend fun findSimilar(imageHash: Long, minimumSimilarity: Int = 78): List<SimilarGlass> =
        dao.getAllOnce()
            .map { SimilarGlass(it, ImageSimilarity.similarityPercent(imageHash, it.imageHash)) }
            .filter { it.similarity >= minimumSimilarity }
            .sortedByDescending { it.similarity }
            .take(5)

    suspend fun add(
        brand: String,
        description: String,
        photoPath: String,
        imageHash: Long
    ): Long = db.withTransaction {
        val meta = dao.getMeta(NEXT_NUMBER_KEY)
        val next = meta?.intValue ?: 1
        val id = dao.insert(
            GlassEntity(
                sequenceNumber = next,
                brand = brand.trim(),
                description = description.trim(),
                photoPath = photoPath,
                imageHash = imageHash
            )
        )
        dao.putMeta(AppMetaEntity(NEXT_NUMBER_KEY, next + 1))
        id
    }

    suspend fun update(item: GlassEntity, brand: String, description: String) {
        dao.update(
            item.copy(
                brand = brand.trim(),
                description = description.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(item: GlassEntity) = dao.delete(item)

    suspend fun replaceAll(items: List<GlassEntity>) = db.withTransaction {
        dao.deleteAll()
        dao.deleteAllMeta()
        items.sortedBy { it.sequenceNumber }.forEach { dao.insert(it.copy(id = 0)) }
        val next = (items.maxOfOrNull { it.sequenceNumber } ?: 0) + 1
        dao.putMeta(AppMetaEntity(NEXT_NUMBER_KEY, next))
    }

    companion object {
        private const val NEXT_NUMBER_KEY = "next_number"
    }
}
