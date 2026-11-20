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
import kotlinx.coroutines.ensureActive

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

data class RecentVehicle(
    val id: String,
    val plate: String,
    val createdAt: Long
)

fun normalizeVehicleId(value: String) = value.uppercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
fun Vehicle.recordKey() = normalizeVehicleId(plate).ifBlank { normalizeVehicleId(vin) }

class AutoHistoriaState : ViewModel() {




    var publicVehicle by mutableStateOf<PublicVehicleInfo?>(null)
        private set

    var publicVehicleLoading by mutableStateOf(false)
        private set

    var publicVehicleError by mutableStateOf("")
        private set

    suspend fun loadPublicVehicle(id: String) {
        val client = api ?: return

        publicVehicle = null
        publicVehicleLoading = true
        publicVehicleError = ""

        try {
            val response = client.call(
                "GET",
                "/vehicles/public/$id"
            )

            val row = response.getJSONObject("vehicle")

            val technical = JSONObject()

            listOf(
                "plate",
                "brand",
                "model",
                "year",
                "displacement",
                "passengers",
                "service",
                "type"
            ).forEach { key ->
                val value = row.optString(key)
                if (value.isNotBlank()) {
                    technical.put(key, value)
                }
            }

            val createdAt = row.optLong("createdAt")

            val reportJson = JSONObject()
                .put("vehicle", technical)
                .toString()

            publicVehicle = PublicVehicleInfo(
                id = row.getString("id"),
                plate = row.optString("plate"),
                createdAt = createdAt,
                report = SavedReport(
                    json = reportJson,
                    time = createdAt
                )
            )

        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            publicVehicleError = e.message
                ?: "No se pudo cargar el informe."
        } finally {
            publicVehicleLoading = false
        }
    }


    private var api: BackendApi? = null
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var avatarCachePath by mutableStateOf("")
        private set
    private val remoteIds = mutableMapOf<String,String>()
    fun configureApi(context: android.content.Context) { if (api == null) api = BackendApi(context.applicationContext) }
    suspend fun recoverAccount(path: String,body: JSONObject) = api!!.call("POST",path,body)
    private suspend fun setProfile(value: UserProfile) {
        profile = value
        currentUser = LocalAccount(value.name, value.email, "", "")
        avatarCachePath = api!!.cacheAvatar(value)
    }
    suspend fun updateProfile(body: JSONObject) { setProfile(api!!.profile(api!!.call("PATCH","/me",body).getJSONObject("user"))) }
    suspend fun changePassword(current: String, next: String) {
        api!!.call("POST","/me/password",JSONObject().put("currentPassword",current).put("newPassword",next))
        clearRemoteSession()
        recentError = ""
        recentLoading = false
    }
    private fun clearRemoteSession() {
        api?.clearSession(); currentUser=null; profile=null; avatarCachePath=""
        vehicles.clear(); reports.clear(); inspections.clear(); remoteIds.clear()
    }
    suspend fun logout() {
        try { api?.call("POST","/auth/logout",JSONObject()) }
        finally { clearRemoteSession() }
    }
    suspend fun reloadGarage() {
        val client=api ?: return
        val loaded=mutableListOf<JSONObject>()
        var offset=0
        do {
            val page=client.call("GET","/vehicles?offset=$offset&limit=50")
            val items=page.getJSONArray("items")
            for(i in 0 until items.length()) loaded.add(items.getJSONObject(i))
            offset += items.length()
        } while(offset < page.getInt("total") && items.length() > 0)
        val freshVehicles=mutableListOf<Vehicle>()
        val freshReports=mutableMapOf<String,SavedReport>()
        val freshInspections=mutableMapOf<String,List<InspectionRecord>>()
        val freshIds=mutableMapOf<String,String>()
        for(row in loaded) {
            val vehicle=Vehicle(row.optString("vin"),row.optString("plate")); val key=vehicle.recordKey()
            freshVehicles.add(vehicle); freshIds[key]=row.getString("id")
            row.optJSONObject("report")?.let { freshReports[key]=SavedReport(it.getString("json"),it.getLong("time"),it.optBoolean("manual")) }
            val records=row.getJSONArray("inspections")
            val list=mutableListOf<InspectionRecord>()
            for(i in 0 until records.length()) list.add(client.decodeInspection(records.getJSONObject(i)))
            freshInspections[key]=list
        }
        vehicles.clear(); vehicles.addAll(freshVehicles); reports.clear(); reports.putAll(freshReports)
        inspections.clear(); inspections.putAll(freshInspections); remoteIds.clear(); remoteIds.putAll(freshIds)
    }
    suspend fun createVehicle(candidate: Vehicle, report: SavedReport?) {
        val client=api
        if(client == null) { addVehicle(candidate,report); return }
        val body=JSONObject().put("vin",candidate.vin).put("plate",candidate.plate)
        report?.let { body.put("report",JSONObject().put("json",it.json).put("time",it.time).put("manual",it.manual)) }
        val row=client.call("POST","/vehicles",body).getJSONObject("vehicle")
        val vehicle=Vehicle(row.getString("vin"),row.getString("plate"))
        remoteIds[vehicle.recordKey()] = row.getString("id")
        vehicles.add(0, vehicle)
        if(report!=null) reports[vehicle.recordKey()]=report
    }
    suspend fun updateReport(vehicle: Vehicle, report: SavedReport) {
        val client=api
        if(client == null) { saveReport(vehicle,report); return }
        val safe=report.copy(json=VehicleDetails(vehicle,report).safeJson)
        client.call("PUT","/vehicles/${remoteIds.getValue(vehicle.recordKey())}/report",JSONObject().put("json",safe.json).put("time",safe.time).put("manual",safe.manual))
        reports[vehicle.recordKey()]=safe
    }
    suspend fun createInspection(vehicle: Vehicle, record: InspectionRecord) {
        val client=api
        if(client == null) { addInspection(vehicle,record); return }
        val row=client.call("POST","/vehicles/${remoteIds.getValue(vehicle.recordKey())}/inspections",client.inspectionBody(record)).getJSONObject("inspection")
        val saved=client.decodeInspection(row); val key=vehicle.recordKey()
        inspections[key]=inspections[key].orEmpty().filterNot { it.id == saved.id } + saved
    }
    suspend fun geocode(point: GnssReading): ResolvedPlace {
        val client=api ?: return resolveGooglePlace(point)
        return ResolvedPlace(client.call("POST","/geocode",JSONObject().put("lat",point.lat).put("lon",point.lon)).getString("address"))
    }

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
        api?.let { it.call("POST","/auth/register",JSONObject().put("name",name).put("email",email).put("password",password)); return }

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
        api?.let { client ->
            clearRemoteSession()
            val response=client.call("POST","/auth/login",JSONObject().put("email",email).put("password",password))
            client.token=response.getString("token")
            try { reloadGarage(); setProfile(client.profile(response.getJSONObject("user"))); return true }
            catch(e: Exception) { clearRemoteSession(); throw e }
        }

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


    val recentVehicles = mutableStateListOf<RecentVehicle>()

    var recentLoading by mutableStateOf(false)
        private set

    var recentError by mutableStateOf("")
        private set

    suspend fun reloadRecentVehicles() {
        val client = api ?: return

        recentLoading = true
        recentError = ""

        try {
            val response = client.call(
                "GET",
                "/vehicles/recent"
            )

            val items = response.getJSONArray("items")

            val latest = (0 until items.length()).map { index ->
                val row = items.getJSONObject(index)

                RecentVehicle(
                    id = row.getString("id"),
                    plate = row.optString("plate"),
                    createdAt = row.getLong("createdAt")
                )
            }

            recentVehicles.clear()
            recentVehicles.addAll(latest)

        } catch (e: Exception) {
            recentError = e.message
                ?: "No se pudieron cargar los últimos registros."
        } finally {
            recentLoading = false
        }
    }

    val globalVehicles = mutableStateListOf<RecentVehicle>()

    var globalVehiclesLoading by mutableStateOf(false)
        private set

    var globalVehiclesError by mutableStateOf("")
        private set

    suspend fun searchAllVehicles(query: String) {
        val client = api ?: return
        val q = query.trim()

        if (q.length < 2) {
            globalVehicles.clear()
            globalVehiclesError = ""
            return
        }

        globalVehiclesLoading = true
        globalVehiclesError = ""

        try {
            val encoded = java.net.URLEncoder.encode(q, "UTF-8")

            val response = client.call(
                "GET",
                "/vehicles/search?q=$encoded"
            )

            val items = response.getJSONArray("items")

            val results = (0 until items.length()).map { index ->
                val row = items.getJSONObject(index)

                RecentVehicle(
                    id = row.getString("id"),
                    plate = row.optString("plate"),
                    createdAt = row.getLong("createdAt")
                )
            }

            kotlinx.coroutines.currentCoroutineContext().ensureActive()

            globalVehicles.clear()
            globalVehicles.addAll(results)

        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            globalVehiclesError =
                e.message ?: "No se pudieron buscar los vehículos."
        } finally {
            globalVehiclesLoading = false
        }


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
