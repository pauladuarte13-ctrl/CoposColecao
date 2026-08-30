package pt.sro.coposcolecao

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class PendingPhoto(
    val path: String,
    val hash: Long,
    val similar: List<SimilarGlass>
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val repo = GlassRepository(db)
    private val photoStore = PhotoStore(application)
    private val backupManager = BackupManager(application, repo)

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val glasses: StateFlow<List<GlassEntity>> =
        query.flatMapLatest { q ->
            if (q.isBlank()) repo.observeAll() else repo.search(q.trim())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingPhoto = MutableStateFlow<PendingPhoto?>(null)
    val pendingPhoto: StateFlow<PendingPhoto?> = _pendingPhoto.asStateFlow()

    fun setQuery(value: String) { query.value = value }

    fun importPhoto(uri: Uri, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                val path = photoStore.import(uri)
                val hash = ImageSimilarity.dHash(path)

                // Comparação por IA totalmente local. O MobileNet converte as imagens
                // em representações semânticas e usa similaridade de cosseno.
                val all = repo.allOnce()
                val similar = if (all.isEmpty()) {
                    emptyList()
                } else {
                    AiImageSimilarity(getApplication()).use { ai ->
                        all.map { item ->
                            SimilarGlass(item, ai.similarity(path, item.photoPath))
                        }
                        .filter { it.similarity >= 72 }
                        .sortedByDescending { it.similarity }
                        .take(5)
                    }
                }

                _pendingPhoto.value = PendingPhoto(path, hash, similar)
            }.onFailure { onError(it.message ?: "Erro ao importar fotografia.") }
        }
    }

    fun acceptPendingPhoto() {
        val pending = _pendingPhoto.value ?: return
        _pendingPhoto.value = pending.copy(similar = emptyList())
    }

    fun cancelPendingPhoto() {
        _pendingPhoto.value?.let { photoStore.delete(it.path) }
        _pendingPhoto.value = null
    }

    fun saveNew(brand: String, description: String, onDone: () -> Unit) {
        val pending = _pendingPhoto.value ?: return
        viewModelScope.launch {
            repo.add(brand, description, pending.path, pending.hash)
            _pendingPhoto.value = null
            onDone()
        }
    }


    fun exportBackup(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { onResult("Backup concluído: $it copos guardados.") }
                .onFailure { onResult("Erro no backup: ${it.message}") }
        }
    }

    fun restoreBackup(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { backupManager.restoreFrom(uri) }
                .onSuccess { onResult("Restauro concluído: $it copos recuperados.") }
                .onFailure { onResult("Erro no restauro: ${it.message}") }
        }
    }

    fun delete(item: GlassEntity) {
        viewModelScope.launch {
            repo.delete(item)
            photoStore.delete(item.photoPath)
        }
    }

    fun update(item: GlassEntity, brand: String, description: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.update(item, brand, description)
            onDone()
        }
    }
}
