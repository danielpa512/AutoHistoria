package com.example.autohistoria

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object PasswordHasher {
    private fun hash(password: String, salt: ByteArray): ByteArray {
        val specification = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded }
        finally { specification.clearPassword() }
    }
    fun create(name: String, email: String, password: String): LocalAccount {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return LocalAccount(name, email, Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash(password, salt)))
    }
    fun matches(account: LocalAccount, password: String): Boolean = runCatching {
        MessageDigest.isEqual(Base64.getDecoder().decode(account.passwordHash),
            hash(password, Base64.getDecoder().decode(account.salt)))
    }.getOrDefault(false)
}

@Serializable
internal data class CachedInspection(
    val id: String, val time: Long, val place: String, val address: String,
    val kilometers: Long, val notes: String, val latitude: Double, val longitude: Double,
    val accuracy: Float, val altitude: Double?, val capturedAt: Long, val googleAddress: Boolean,
    val photos: List<String> = emptyList(),
    val checklist: InspectionChecklist = InspectionChecklist()
) {
    fun toRecord() = InspectionRecord(id, time, place, address, kilometers, notes,
        GnssReading(latitude, longitude, accuracy, altitude ?: Double.NaN, capturedAt), googleAddress, photos, checklist)
    companion object {
        fun from(record: InspectionRecord) = CachedInspection(
            record.id, record.time, record.place, record.address, record.kilometers, record.notes,
            record.point.lat, record.point.lon, record.point.accuracy,
            record.point.altitude.takeUnless { it.isNaN() }, record.point.time, record.googleAddress, record.photos, record.checklist)
    }
}
@Serializable
internal data class LocalSnapshot(
    val version: Int = 1,
    val accounts: List<LocalAccount> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val reports: Map<String, SavedReport> = emptyMap(),
    val inspections: Map<String, List<CachedInspection>> = emptyMap()
)
internal object LocalCacheCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(snapshot: LocalSnapshot) = json.encodeToString(snapshot)
    fun decode(raw: String): LocalSnapshot {
        val value = json.decodeFromString<LocalSnapshot>(raw)
        require(value.version == 1) { "La versión de los datos locales no es compatible." }
        val accounts = value.accounts.distinctBy { it.email.trim().lowercase(java.util.Locale.ROOT) }
        val plates = mutableSetOf<String>()
        val vins = mutableSetOf<String>()
        val vehicles = value.vehicles.map { Vehicle(normalizeVehicleId(it.vin), normalizeVehicleId(it.plate)) }
            .filter {
                val valid = (it.plate.isNotBlank() || it.vin.isNotBlank()) &&
                    (it.plate.isBlank() || it.plate !in plates) && (it.vin.isBlank() || it.vin !in vins)
                if (valid) { if (it.plate.isNotBlank()) plates.add(it.plate); if (it.vin.isNotBlank()) vins.add(it.vin) }
                valid
            }
        return value.copy(accounts = accounts, vehicles = vehicles)
    }
}
