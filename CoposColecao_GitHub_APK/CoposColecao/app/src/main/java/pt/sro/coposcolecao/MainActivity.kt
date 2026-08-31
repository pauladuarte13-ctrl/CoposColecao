@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package pt.sro.coposcolecao

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CollectionApp()
            }
        }
    }
}

@Composable
private fun CollectionApp(vm: MainViewModel = viewModel()) {
    val glasses by vm.glasses.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val pending by vm.pendingPhoto.collectAsState()

    val context = LocalContext.current
    var selected by remember { mutableStateOf<GlassEntity?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = cameraUri
        if (ok && uri != null) vm.importPhoto(uri)
    }


    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) vm.exportBackup(uri) { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.restoreBackup(uri) { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Coleção de Copos") },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Criar backup") },
                            onClick = {
                                showMenu = false
                                backupLauncher.launch("copos_backup.zip")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restaurar backup") },
                            onClick = {
                                showMenu = false
                                restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("Pesquisar número, marca ou descrição") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            if (glasses.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ainda não existem copos na coleção.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(glasses, key = { it.id }) { item ->
                        GlassRow(item = item, onClick = { selected = item })
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Text(
                "Adicionar fotografia",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(12.dp))

            ListItem(
                headlineContent = { Text("Usar câmara") },
                leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                modifier = Modifier.clickable {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    cameraUri = uri
                    showAddSheet = false
                    cameraLauncher.launch(uri)
                }
            )
            ListItem(
                headlineContent = { Text("Escolher da galeria") },
                leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                modifier = Modifier.clickable {
                    showAddSheet = false
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    pending?.let { p ->
        if (p.similar.isNotEmpty()) {
            SimilarityDialog(
                pending = p,
                onCancel = vm::cancelPendingPhoto,
                onContinue = vm::acceptPendingPhoto
            )
        } else {
            NewGlassDialog(
                pending = p,
                onCancel = vm::cancelPendingPhoto,
                onSave = { brand, description ->
                    vm.saveNew(brand, description) {}
                }
            )
        }
    }

    selected?.let { item ->
        GlassDetailDialog(
            item = item,
            onDismiss = { selected = null },
            onDelete = {
                vm.delete(item)
                selected = null
            },
            onSave = { brand, description ->
                vm.update(item, brand, description) { selected = null }
            }
        )
    }
}

@Composable
private fun GlassRow(item: GlassEntity, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = File(item.photoPath),
                contentDescription = item.brand,
                modifier = Modifier.size(84.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "#${item.sequenceNumber.toString().padStart(4, '0')}  ${item.brand}",
                    fontWeight = FontWeight.Bold
                )
                if (item.description.isNotBlank()) {
                    Text(item.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SimilarityDialog(
    pending: PendingPhoto,
    onCancel: () -> Unit,
    onContinue: () -> Unit
) {
    var candidateIndex by remember(pending.path) { mutableIntStateOf(0) }
    val candidates = pending.similar.take(3)
    val candidate = candidates[candidateIndex.coerceIn(0, candidates.lastIndex)]

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Possível correspondência") },
        text = {
            Column {
                Text(
                    "Compare a fotografia nova com o copo já existente antes de decidir.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Fotografado agora",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        AsyncImage(
                            model = File(pending.path),
                            contentDescription = "Fotografia nova",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Na coleção",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        AsyncImage(
                            model = File(candidate.glass.photoPath),
                            contentDescription = "Copo existente",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "#${candidate.glass.sequenceNumber.toString().padStart(4, '0')} · " +
                        "${candidate.glass.brand.ifBlank { "Sem marca" }} · " +
                        "${candidate.similarity}% de semelhança",
                    fontWeight = FontWeight.Bold
                )

                pending.detectedBrand?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Marca detetada: $it", style = MaterialTheme.typography.bodySmall)
                }

                if (candidates.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { candidateIndex-- },
                            enabled = candidateIndex > 0
                        ) { Text("Anterior") }

                        Text("${candidateIndex + 1} de ${candidates.size}")

                        TextButton(
                            onClick = { candidateIndex++ },
                            enabled = candidateIndex < candidates.lastIndex
                        ) { Text("Seguinte") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCancel) { Text("É o mesmo copo") }
        },
        dismissButton = {
            TextButton(onClick = onContinue) { Text("É diferente — adicionar") }
        }
    )
}

@Composable
private fun NewGlassDialog(
    pending: PendingPhoto,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var brand by remember { mutableStateOf(pending.detectedBrand.orEmpty()) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Novo copo") },
        text = {
            Column {
                AsyncImage(
                    model = File(pending.path),
                    contentDescription = "Fotografia",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
                if (pending.detectedText.isNotBlank()) {
                    Text(
                        "Texto detetado no copo: ${pending.detectedText.replace("\n", " · ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        "Não foi possível ler uma inscrição. Confirme a marca manualmente.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marca") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(brand, description) },
                enabled = brand.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    )
}

@Composable
private fun GlassDetailDialog(
    item: GlassEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var brand by remember(item.id) { mutableStateOf(item.brand) }
    var description by remember(item.id) { mutableStateOf(item.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Copo #${item.sequenceNumber.toString().padStart(4, '0')}")
        },
        text = {
            Column {
                AsyncImage(
                    model = File(item.photoPath),
                    contentDescription = item.brand,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Marca") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(brand, description) },
                enabled = brand.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Eliminar") }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        }
    )
}
