package com.example.autohistoria

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

internal object InspectionPhotos {
    const val MAX_PHOTOS = 6
    private const val MAX_BYTES = 20L * 1024 * 1024
    private fun directory(context: Context) = File(context.filesDir, "inspection_photos").apply {
        check(exists() || mkdirs()) { "No se pudo crear la carpeta de fotos." }
    }
    fun file(context: Context, name: String): File {
        require(Regex("inspection_[a-f0-9-]+\\.jpg").matches(name)) { "Nombre de foto inválido." }
        return File(directory(context), name)
    }
    fun create(context: Context): String {
        val name = "inspection_${UUID.randomUUID()}.jpg"
        check(file(context, name).createNewFile()) { "No se pudo crear el archivo de foto." }
        return name
    }
    fun cameraUri(context: Context, name: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.inspectionphotos", file(context, name))

    suspend fun importPhotos(context: Context, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val created = mutableListOf<String>()
        try {
            uris.distinct().forEach { uri ->
                val name = create(context)
                created.add(name)
                val target = file(context, name)
                val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo leer una imagen.")
                input.use { source ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BYTES) throw IOException("Cada foto debe ocupar menos de 20 MB.")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Uno de los archivos no es una imagen compatible.")
            }
            created.toList()
        } catch (failure: Exception) {
            created.forEach { file(context, it).delete() }
            throw failure
        }
    }

    suspend fun bitmap(context: Context, name: String, pixels: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val source = file(context, name)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return@runCatching null
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > pixels) sample *= 2
            val image = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching null
            val orientation = runCatching { ExifInterface(source.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            }
            Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
        }.getOrNull()
    }
}

@Composable
private fun InspectionPhoto(name: String, description: String, modifier: Modifier, full: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, name, full) {
        value = InspectionPhotos.bitmap(context, name, if (full) 1400 else 300)
    }
    val picture = bitmap
    if (picture == null) Surface(modifier, color = Color(0xFFE8EDF3)) {
        Text("Foto no disponible", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    } else Image(picture.asImageBitmap(), description, modifier,
        contentScale = if (full) ContentScale.Fit else ContentScale.Crop)
}

@Composable
internal fun InspectionPhotoGallery(photos: List<String>, onRemove: ((String) -> Unit)? = null) {
    var enlarged by remember { mutableStateOf<String?>(null) }
    if (photos.isNotEmpty()) {
        Text("${photos.size} foto(s) de la inspección", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(photos, key = { _, name -> name }) { index, name ->
                Column {
                    Card(onClick = { enlarged = name }) {
                        InspectionPhoto(name, "Foto ${index + 1} de la inspección", Modifier.size(128.dp, 108.dp))
                    }
                    if (onRemove != null) TextButton(onClick = { onRemove(name) }) { Text("Quitar") }
                }
            }
        }
    }
    enlarged?.let { name ->
        Dialog(onDismissRequest = { enlarged = null }) {
            Card {
                Column(Modifier.padding(12.dp)) {
                    InspectionPhoto(name, "Foto ampliada de la inspección", Modifier.fillMaxWidth().height(400.dp), true)
                    TextButton(onClick = { enlarged = null }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
                }
            }
        }
    }
}

@Composable
internal fun InspectionPhotoPicker(
    photos: List<String>, onChange: (List<String>) -> Unit, onBusy: (Boolean) -> Unit, enabled: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val name = pending
        pending = null
        if (name != null) {
            if (saved && InspectionPhotos.file(context, name).length() > 0) onChange(photos + name)
            else InspectionPhotos.file(context, name).delete()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(InspectionPhotos.MAX_PHOTOS)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true; onBusy(true); error = ""
            try {
                val remaining = InspectionPhotos.MAX_PHOTOS - photos.size
                val imported = InspectionPhotos.importPhotos(context, uris.take(remaining))
                onChange(photos + imported)
                if (uris.size > remaining) error = "Solo se agregaron las fotos disponibles hasta completar el máximo de 6."
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (failure: Exception) { error = failure.message ?: "No se pudieron agregar las fotos."
            } finally { busy = false; onBusy(false) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Máximo 6 fotos. Puedes capturar el vehículo, el tablero o un hallazgo.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        val name = InspectionPhotos.create(context)
                        pending = name
                        camera.launch(InspectionPhotos.cameraUri(context, name))
                    }.onFailure {
                        pending?.let { name -> InspectionPhotos.file(context, name).delete() }
                        pending = null
                        error = "No se pudo abrir la cámara del dispositivo."
                    }
                },
                enabled = enabled && !busy && photos.size < InspectionPhotos.MAX_PHOTOS,
                modifier = Modifier.weight(1f)
            ) { Text("Tomar foto") }
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = enabled && !busy && photos.size < InspectionPhotos.MAX_PHOTOS,
                modifier = Modifier.weight(1f)
            ) { Text("Galería") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        InspectionPhotoGallery(photos, if (enabled && !busy) { name ->
            onChange(photos - name)
            InspectionPhotos.file(context, name).delete()
        } else null)
    }
}
