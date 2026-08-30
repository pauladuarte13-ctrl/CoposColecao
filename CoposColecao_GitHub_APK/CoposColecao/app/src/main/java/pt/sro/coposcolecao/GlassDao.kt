package pt.sro.coposcolecao

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GlassDao {
    @Query("SELECT * FROM glasses ORDER BY sequenceNumber ASC")
    fun observeAll(): Flow<List<GlassEntity>>

    @Query("""
        SELECT * FROM glasses
        WHERE brand LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
           OR CAST(sequenceNumber AS TEXT) LIKE '%' || :query || '%'
        ORDER BY sequenceNumber ASC
    """)
    fun search(query: String): Flow<List<GlassEntity>>

    @Query("SELECT * FROM glasses ORDER BY sequenceNumber ASC")
    suspend fun getAllOnce(): List<GlassEntity>

    @Insert
    suspend fun insert(item: GlassEntity): Long

    @Update
    suspend fun update(item: GlassEntity)

    @Delete
    suspend fun delete(item: GlassEntity)

    @Query("DELETE FROM glasses")
    suspend fun deleteAll()

    @Query("DELETE FROM app_meta")
    suspend fun deleteAllMeta()

    @Query("SELECT * FROM app_meta WHERE `key` = :key LIMIT 1")
    suspend fun getMeta(key: String): AppMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: AppMetaEntity)
}
