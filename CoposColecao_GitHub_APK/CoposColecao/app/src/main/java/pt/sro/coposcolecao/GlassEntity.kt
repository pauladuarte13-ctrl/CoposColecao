package pt.sro.coposcolecao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glasses")
data class GlassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sequenceNumber: Int,
    val brand: String,
    val description: String,
    val photoPath: String,
    val imageHash: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val intValue: Int
)
