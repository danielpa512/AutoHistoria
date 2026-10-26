package com.example.autohistoria

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalPersistenceTest {
    @Test fun aNewStateRestoresAccountsVehiclesReportsAndInspectionLocations() = runBlocking {
        var disk: String? = null
        val first = AutoHistoriaState()
        first.connectCache({ disk }, { disk = it; true })
        first.register("Daniel", " Daniel@Example.COM ", "clave-2026")
        val vehicle = Vehicle("1HGCM82633A004352", "ABC123")
        first.addVehicle(vehicle)
        first.saveReport(vehicle, SavedReport("""{"vehicle":{"brand":"RENAULT"},"token":"secret"}""", 5))
        first.addInspection(vehicle, InspectionRecord(time = 100, place = "Taller norte", address = "Calle 1",
            kilometers = 65000, notes = "Revisión", point = GnssReading(4.6, -74.1, 8f, Double.NaN, 99),
            googleAddress = false))
        assertFalse(disk!!.contains("clave-2026"))
        assertFalse(disk!!.contains("secret"))
        val restarted = AutoHistoriaState()
        restarted.connectCache({ disk }, { disk = it; true })
        assertTrue(restarted.login("DANIEL@example.com", "clave-2026"))
        assertFalse(restarted.login("daniel@example.com", "incorrecta"))
        assertEquals("Daniel", restarted.currentUser?.name)
        assertEquals(vehicle, restarted.vehicles.single())
        assertEquals("RENAULT", VehicleDetails(vehicle, restarted.reports[vehicle.recordKey()]).brand)
        assertEquals("Taller norte", restarted.history(vehicle).single().place)
        assertEquals(65000L, restarted.history(vehicle).single().kilometers)
        assertTrue(restarted.history(vehicle).single().point.altitude.isNaN())
    }

    @Test fun duplicatesAreRejectedAcrossRestartsAndNormalizedIdentifiers() = runBlocking {
        var disk: String? = null
        val first = AutoHistoriaState()
        first.connectCache({ disk }, { disk = it; true })
        first.register("Daniel", "daniel@example.com", "clave-2026")
        first.addVehicle(Vehicle("1HGCM82633A004352", "abc 123"))
        val second = AutoHistoriaState()
        second.connectCache({ disk }, { disk = it; true })
        assertTrue(runCatching { second.register("Otro", " DANIEL@EXAMPLE.COM ", "otra-clave") }.isFailure)
        assertTrue(runCatching { second.addVehicle(Vehicle("", "ABC-123")) }.isFailure)
        assertTrue(runCatching { second.addVehicle(Vehicle("1hgcm82633a004352", "ZZZ999")) }.isFailure)
        assertEquals(1, second.accounts.size)
        assertEquals(1, second.vehicles.size)
    }

    @Test fun failedDiskWriteDoesNotCreateAnUnstoredVehicle() {
        val state = AutoHistoriaState()
        state.connectCache({ null }, { false })
        assertTrue(runCatching { state.addVehicle(Vehicle("", "ABC123")) }.isFailure)
        assertTrue(state.vehicles.isEmpty())
    }

    @Test fun corruptCacheIsNotSilentlyOverwritten() {
        var disk = "not valid JSON"
        val state = AutoHistoriaState()
        state.connectCache({ disk }, { disk = it; true })
        assertTrue(state.storageError.isNotBlank())
        assertTrue(runCatching { state.addVehicle(Vehicle("", "ABC123")) }.isFailure)
        assertEquals("not valid JSON", disk)
    }
}
