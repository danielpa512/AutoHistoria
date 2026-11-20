package com.example.autohistoria

import org.junit.Assert.*
import org.junit.Test

class AutoHistoriaStateTest {
    @Test fun inspectionsRemainAssociatedWithTheirVehicleAndAreChronological() {
        val state = AutoHistoriaState()
        val first = Vehicle("", "ABC123")
        val second = Vehicle("", "XYZ789")
        val point = GnssReading(4.6, -74.1, 8f, 2600.0, 1000L)
        state.addInspection(first, InspectionRecord(time = 2000, place = "Taller norte", address = "Calle 10",
            kilometers = 65000, notes = "Frenos", point = point, googleAddress = false))
        state.addInspection(second, InspectionRecord(time = 3000, place = "Taller sur", address = "",
            kilometers = 12000, notes = "", point = point, googleAddress = false))
        state.addInspection(first, InspectionRecord(time = 1000, place = "Inspección inicial", address = "",
            kilometers = 64000, notes = "", point = point, googleAddress = false))
        assertEquals(listOf(65000L, 64000L), state.history(first).map { it.kilometers })
        assertEquals("Taller sur", state.history(second).single().place)
        // Una ruta reconstruida al volver al informe encuentra el mismo historial.
        assertEquals(2, state.history(Vehicle("", "ABC123")).size)
        assertEquals(4.6, state.history(first).first().point.lat, 0.00001)
    }

    @Test fun reportUnderstandsBothObservedApiFormats() {
        val vehicle = Vehicle("", "ABC123")
        val simple = VehicleDetails(vehicle, SavedReport(
            """{"vehicle":{"brand":"VOLVO","line":"B330RLE","yearOfModel":2021,"displacement":7698,"types":[{"label":"BUSES"}]}}""", 1))
        assertEquals("VOLVO B330RLE", simple.title)
        assertEquals("2021", simple.year)
        assertEquals("7698", simple.value("displacement"))
        assertEquals("BUSES", simple.categories)
        val nested = VehicleDetails(vehicle, SavedReport(
            """{"vehicle":{"brand":"RENAULT","model":"KWID","year":2022,"line":{"label":"SIN LINEA"},"type":{"label":"AUTOMÓVIL"}}}""", 2))
        assertEquals("RENAULT KWID", nested.title)
        assertEquals("2022", nested.year)
        assertEquals("SIN LINEA", nested.label("line"))
        assertEquals("AUTOMÓVIL", nested.categories)
    }

    @Test fun reportDiagnosticsRemoveSessionCredentials() {
        val details = VehicleDetails(Vehicle("", "ABC123"), SavedReport(
            """{"vehicle":{"brand":"RENAULT"},"quotation":{"identificationNumber":"123456"},"token":"secret-token"}""", 1))
        assertFalse(details.safeJson.contains("secret-token"))
        assertFalse(details.safeJson.contains("123456"))
        assertTrue(details.safeJson.contains("RENAULT"))
    }
}
