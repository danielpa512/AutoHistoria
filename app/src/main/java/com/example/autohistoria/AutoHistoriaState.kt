package com.example.autohistoria

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class LocalAccount(val name: String, val email: String, val salt: String, val passwordHash: String)
data class InspectionRecord(
    val id: String = UUID.randomUUID().toString(),
    val time: Long,
    val place: String,
    val address: String,
    val kilometers: Long,
    val notes: String,
    val point: GnssReading,
    val googleAddress: Boolean,
    val photos: List<String> = emptyList(),
    val checklist: InspectionChecklist = InspectionChecklist()
)
@Serializable data class SavedReport(val json: String, val time: Long, val manual: Boolean = false)

fun normalizeVehicleId(value: String) = value.uppercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
fun Vehicle.recordKey() = normalizeVehicleId(plate).ifBlank { normalizeVehicleId(vin) }

class AutoHistoriaState : ViewModel() {
    val accounts = mutableStateListOf<LocalAccount>()
    val vehicles = mutableStateListOf<Vehicle>()
    val reports = mutableStateMapOf<String, SavedReport>()
    val inspections = mutableStateMapOf<String, List<InspectionRecord>>()
    var currentUser by mutableStateOf<LocalAccount?>(null)
    private var writer: ((String) -> Boolean)? = null
    private var cacheConnected = false
    var storageError by mutableStateOf("")
        private set

    fun connectCache(read: () -> String?, write: (String) -> Boolean) {
        if (cacheConnected) return
        val restored = runCatching {
            read()?.takeIf { it.isNotBlank() }?.let { LocalCacheCodec.decode(it) }
        }.getOrElse {
            storageError = "No se pudieron leer los datos locales. Se conservaron sin modificarlos."
            null
        }
        if (restored != null) {
            accounts.clear(); accounts.addAll(restored.accounts)
            vehicles.clear(); vehicles.addAll(restored.vehicles)
            reports.clear(); reports.putAll(restored.reports)
            inspections.clear()
            restored.inspections.forEach { (key, rows) -> inspections[key] = rows.map { it.toRecord() } }
        }
        writer = write
        cacheConnected = true
    }

    private fun snapshot() = LocalSnapshot(
        accounts = accounts.toList(), vehicles = vehicles.toList(), reports = reports.toMap(),
        inspections = inspections.mapValues { (_, rows) -> rows.map { CachedInspection.from(it) } }
    )
    private fun persist(snapshot: LocalSnapshot) {
        check(storageError.isBlank()) { storageError }
        val store = writer ?: return
        check(store(LocalCacheCodec.encode(snapshot))) { "No se pudo guardar en el dispositivo. Intenta nuevamente." }
    }
    suspend fun register(name: String, email: String, password: String) {
        val normalized = email.trim().lowercase(java.util.Locale.ROOT)
        require(name.isNotBlank()) { "Ingresa tu nombre." }
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalized)) { "Ingresa un correo válido." }
        require(password.length >= 6) { "Usa una contraseña de al menos 6 caracteres." }
        require(accounts.none { it.email == normalized }) { "Este correo ya está registrado en el dispositivo." }
        val account = withContext(Dispatchers.Default) { PasswordHasher.create(name.trim(), normalized, password) }
        require(accounts.none { it.email == normalized }) { "Este correo ya está registrado en el dispositivo." }
        persist(snapshot().copy(accounts = accounts.toList() + account))
        accounts.add(account)
    }
    suspend fun login(email: String, password: String): Boolean {
        val normalized = email.trim().lowercase(java.util.Locale.ROOT)
        val account = accounts.find { it.email == normalized } ?: return false
        val valid = withContext(Dispatchers.Default) { PasswordHasher.matches(account, password) }
        if (valid) currentUser = account
        return valid
    }
    fun addVehicle(candidate: Vehicle, manualReport: SavedReport? = null) {
        val normalized = Vehicle(normalizeVehicleId(candidate.vin), normalizeVehicleId(candidate.plate))
        require(normalized.plate.isNotBlank() || normalized.vin.isNotBlank()) { "Ingresa una placa o un VIN." }
        require(normalized.vin.isBlank() || Regex("[A-HJ-NPR-Z0-9]{17}").matches(normalized.vin)) { "El VIN no es válido." }
        require(vehicles.none {
            (normalized.plate.isNotBlank() && normalizeVehicleId(it.plate) == normalized.plate) ||
                (normalized.vin.isNotBlank() && normalizeVehicleId(it.vin) == normalized.vin)
        }) { "Ya existe un vehículo con esa placa o VIN." }
        val updatedReports = manualReport?.let { reports.toMap() + (normalized.recordKey() to it) } ?: reports.toMap()
        persist(snapshot().copy(vehicles = vehicles.toList() + normalized, reports = updatedReports))
        vehicles.add(normalized)
        if (manualReport != null) reports[normalized.recordKey()] = manualReport
    }
    fun saveReport(vehicle: Vehicle, report: SavedReport) {
        val safe = report.copy(json = VehicleDetails(vehicle, report).safeJson)
        persist(snapshot().copy(reports = reports.toMap() + (vehicle.recordKey() to safe)))
        reports[vehicle.recordKey()] = safe
    }
    fun history(vehicle: Vehicle) = inspections[vehicle.recordKey()].orEmpty().sortedByDescending { it.time }
    fun addInspection(vehicle: Vehicle, record: InspectionRecord) {
        val key = vehicle.recordKey()
        if (inspections[key].orEmpty().any { it.id == record.id }) return
        val updated = inspections[key].orEmpty() + record
        persist(snapshot().copy(inspections = snapshot().inspections + (key to updated.map { CachedInspection.from(it) })))
        inspections[key] = updated
    }
}

data class VehicleDetails(val original: Vehicle, val report: SavedReport?) {
    private val root = runCatching { JSONObject(report?.json.orEmpty()) }.getOrNull()
    private val data = root?.optJSONObject("vehicle")
    fun value(key: String, fallback: String = "Sin información"): String =
        data?.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() } ?: fallback
    fun label(key: String): String {
        val field = data?.opt(key)
        return when (field) {
            is JSONObject -> field.optString("label").ifBlank { "Sin información" }
            is String -> field.ifBlank { "Sin información" }
            else -> "Sin información"
        }
    }
    val brand get() = value("brand", "")
    val model get() = value("model", label("line").takeUnless { it == "Sin información" }.orEmpty())
    val title get() = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Vehículo registrado" }
    val year get() = value("year", value("yearOfModel", "Año sin consultar"))
    val plate get() = value("plate", original.plate.ifBlank { "Sin placa" })
    val zone get() = data?.optJSONObject("circulationZone")?.optString("label")
        ?: root?.optJSONObject("quotation")?.optJSONObject("circulationZone")?.optString("label") ?: "Sin información"
    val categories get() = data?.optJSONArray("types")?.let { list ->
        (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("label") }.joinToString(" · ")
    }?.ifBlank { null } ?: label("type")
    val safeJson get() = root?.let {
        JSONObject(it.toString()).apply {
            remove("token")
            optJSONObject("quotation")?.remove("identificationNumber")
        }.toString(2)
    }.orEmpty()
}

suspend fun loadVehicleReport(plate: String, captcha: String): SavedReport = withContext(Dispatchers.IO) {
    val connection = URL("https://soat-colombia-facade-production.segurosfalabella.com.co/api/v2/vehicle/byPlate")
        .openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        val body = JSONObject().apply {
            put("vehicle", JSONObject().apply {
                put("plate", plate.trim().uppercase()); put("circulationZone", ""); put("department", ""); put("city", "")
            })
            put("quotation", JSONObject().put("identificationNumber", 123123123))
            put("seller", JSONObject().put("subsidiary", "").put("identification", ""))
            put("captchaV2", ""); put("captcha", captcha); put("origin", ""); put("isColectivo", false)
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        android.util.Log.d("AutoHistoriaAPI", "HTTP $status")
        if (status !in 200..299) error("La consulta respondió HTTP $status. Intenta nuevamente.")
        val json = runCatching { JSONObject(text) }.getOrElse { error("El servicio no entregó datos JSON válidos.") }
        // El diagnóstico no expone tokens de sesión ni documentos.
        JSONObject(json.toString()).apply {
            remove("token"); optJSONObject("quotation")?.remove("identificationNumber")
        }.toString(2).chunked(2500).forEachIndexed { index, part ->
            android.util.Log.d("AutoHistoriaAPI", "BLOQUE ${index + 1}: $part")
        }
        if (json.optJSONObject("vehicle") == null) error("No se encontraron datos generales del vehículo.")
        SavedReport(json.toString(), System.currentTimeMillis())
    } finally { connection.disconnect() }
}

data class ResolvedPlace(val address: String)
suspend fun resolveGooglePlace(point: GnssReading): ResolvedPlace = withContext(Dispatchers.IO) {
    val key = BuildConfig.GOOGLE_GEOCODING_API_KEY
    if (key.isBlank()) error("Configura la clave de Google Geocoding en local.properties.")
    val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=${point.lat},${point.lon}&language=es&key=$key"
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10000; connection.readTimeout = 15000
        val status = connection.responseCode
        if (status !in 200..299) error("Google respondió HTTP $status. Puedes escribir el lugar manualmente.")
        val root = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        when (root.optString("status")) {
            "OK" -> {
                val address = root.optJSONArray("results")?.optJSONObject(0)?.optString("formatted_address").orEmpty()
                if (address.isBlank()) error("No se encontró una dirección para estas coordenadas.")
                ResolvedPlace(address)
            }
            "REQUEST_DENIED" -> error("Google rechazó la consulta. Habilita Geocoding API y revisa facturación y restricciones de la clave. Puedes escribir el lugar manualmente.")
            "ZERO_RESULTS" -> error("Google no encontró una dirección. Escribe el nombre del lugar manualmente.")
            "OVER_QUERY_LIMIT" -> error("Se alcanzó el límite de Google. Escribe el lugar manualmente.")
            else -> error("No se pudo obtener la dirección de Google. Puedes escribirla manualmente.")
        }
    } finally { connection.disconnect() }
}
