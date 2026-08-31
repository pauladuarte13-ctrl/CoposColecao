package pt.sro.coposcolecao

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.Normalizer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PendingPhoto(
    val path: String,
    val hash: Long,
    val similar: List<SimilarGlass>,
    val detectedText: String = "",
    val detectedBrand: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val repo = GlassRepository(db)
    private val photoStore = PhotoStore(application)
    private val backupManager = BackupManager(application, repo)
    private val textRecognizer = LogoTextRecognizer(application)

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
                val all = repo.allOnce()

                // Primeiro tenta ler a inscrição/logótipo textual no vidro.
                // Uma marca reconhecida funciona como filtro forte: nunca comparamos
                // automaticamente copos de marcas diferentes só porque têm a mesma forma.
                val detectedText = runCatching { textRecognizer.recognize(path) }.getOrDefault("")
                val detectedBrand = detectKnownBrand(detectedText, all)
                // A marca é um sinal forte, mas não pode ser um bloqueio absoluto:
                // o OCR pode falhar por reflexos, gravações claras ou pequenas mudanças de ângulo.
                // Se reconhecemos a marca, comparamos apenas com essa marca e aceitamos uma
                // semelhança visual mais baixa. Se o OCR não reconhece a marca, ainda fazemos
                // uma pesquisa visual conservadora em toda a coleção.
                val candidates = detectedBrand?.let { brand ->
                    all.filter { normalize(it.brand) == normalize(brand) }
                } ?: all

                val threshold = if (detectedBrand != null) 65 else 78

                val similar = if (candidates.isEmpty()) {
                    emptyList()
                } else {
                    AiImageSimilarity(getApplication()).use { ai ->
                        candidates.map { item ->
                            SimilarGlass(item, ai.similarity(path, item.photoPath))
                        }
                            .filter { it.similarity >= threshold }
                            .sortedByDescending { it.similarity }
                            .take(5)
                    }
                }

                _pendingPhoto.value = PendingPhoto(
                    path = path,
                    hash = hash,
                    similar = similar,
                    detectedText = detectedText,
                    detectedBrand = detectedBrand
                )
            }.onFailure { onError(it.message ?: "Erro ao importar fotografia.") }
        }
    }

    private fun detectKnownBrand(text: String, glasses: List<GlassEntity>): String? {
        val normalizedText = normalize(text)
        if (normalizedText.length < 3) return null
        return glasses.asSequence()
            .map { it.brand.trim() }
            .filter { it.length >= 3 }
            .distinctBy(::normalize)
            .sortedByDescending { normalize(it).length }
            .firstOrNull { brand -> normalizedText.contains(normalize(brand)) }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .uppercase()
        .replace("[^A-Z0-9]+".toRegex(), " ")
        .trim()

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
