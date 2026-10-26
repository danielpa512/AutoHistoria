package com.example.autohistoria

import org.junit.Assert.*
import org.junit.Test

class ManualVehicleTest {
    @Test fun manualRegistrationPersistsFichaAndRejectsDuplicates() {
        var cache: String? = null
        val state = AutoHistoriaState()
        state.connectCache({ cache }, { cache = it; true })
        val car = Vehicle("", "abc123")
        val report = SavedReport("""{"vehicle":{"brand":"RENAULT","model":"KWID","year":2022}}""", 1, manual = true)
        state.addVehicle(car, report)
        val restored = AutoHistoriaState()
        restored.connectCache({ cache }, { true })
        assertEquals("RENAULT KWID", VehicleDetails(restored.vehicles.single(), restored.reports["ABC123"]).title)
        assertTrue(restored.reports["ABC123"]!!.manual)
        assertTrue(runCatching { restored.addVehicle(car, report) }.isFailure)
        assertEquals(1, restored.vehicles.size)
    }

    @Test fun failedStorageDoesNotPartiallyRegisterVehicle() {
        val state = AutoHistoriaState()
        state.connectCache({ null }, { false })
        assertTrue(runCatching { state.addVehicle(Vehicle("", "ABC123"), SavedReport("{}", 1, true)) }.isFailure)
        assertTrue(state.vehicles.isEmpty())
        assertTrue(state.reports.isEmpty())
    }
}
