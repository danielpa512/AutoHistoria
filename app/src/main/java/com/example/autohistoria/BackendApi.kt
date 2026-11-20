package com.example.autohistoria

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UserProfile(val id: String, val name: String, val lastName: String, val email: String,
    val phone: String, val city: String, val bio: String, val avatar: String)

internal class BackendApi(private val context: Context) {
    var token: String = ""
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaRoot get() = File(context.cacheDir, "remote_media").apply { mkdirs() }
    suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val base = BuildConfig.AUTOHISTORIA_API_URL.trimEnd('/')
        require(base.isNotBlank()) { "Configura AUTOHISTORIA_API_URL en local.properties y recompila la app." }
        require(base.startsWith("https://") || (BuildConfig.DEBUG && base.startsWith("http://"))) { "El servidor debe usar HTTPS." }
        val connection = URL(base + "/v1" + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 15000; connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = input?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrElse { error("El servidor no devolvió JSON válido (HTTP $status).") }
            if (status !in 200..299) error(result.optJSONObject("error")?.optString("message") ?: "Error HTTP $status")
            result
        } finally { connection.disconnect() }
    }
    fun profile(json: JSONObject) = UserProfile(json.getString("id"), json.getString("name"),json.optString("lastName"),
        json.getString("email"),json.optString("phone"),json.optString("city"),json.optString("bio"),json.optString("avatar"))
    suspend fun cacheAvatar(profile: UserProfile): String = withContext(Dispatchers.IO) {
        val file = File(mediaRoot, "avatar-${profile.id}.jpg")
        if (profile.avatar.isBlank()) { file.delete(); "" }
        else { file.writeBytes(Base64.decode(profile.avatar.substringAfter(','), Base64.DEFAULT)); file.absolutePath }
    }
    suspend fun encodePhoto(path: String): String = withContext(Dispatchers.IO) {
        val bitmap = InspectionPhotos.bitmap(context,path,768) ?: error("No se pudo abrir una foto de la inspección.")
        try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG,80,output)
            require(output.size() <= 400000) { "Una foto supera el límite. Selecciona otra imagen." }
            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
        } finally { bitmap.recycle() }
    }
    suspend fun inspectionBody(record: InspectionRecord): JSONObject {
        val photos = record.photos.map { encodePhoto(it) }
        return JSONObject(codec.encodeToString(CachedInspection.from(record).copy(photos = photos)))
    }
    suspend fun decodeInspection(json: JSONObject): InspectionRecord = withContext(Dispatchers.IO) {
        val cached = codec.decodeFromString<CachedInspection>(json.toString())
        val paths = cached.photos.map { data ->
            val name = "remote_${java.util.UUID.nameUUIDFromBytes(data.toByteArray())}.jpg"
            val file = InspectionPhotos.file(context,name)
            file.writeBytes(Base64.decode(data.substringAfter(','),Base64.DEFAULT)); name
        }
        cached.copy(photos = paths).toRecord()
    }
    fun clearSession() { token = ""; mediaRoot.listFiles()?.forEach { it.delete() } }
}

/** Decode bounded dimensions, then recompress: don't upload original EXIF or full-size camera data. */
internal suspend fun encodeImage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it,null,bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "La imagen no se puede abrir." }
    var sample = 1
    while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("No se pudo leer la foto.")
    try {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG,80,output)
        require(output.size() <= 400000) { "La imagen es demasiado grande. Prueba con otra." }
        "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
    } finally { bitmap.recycle() }
}
