package com.example.autohistoria

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InspectionPhotoPersistenceTest {
    private fun record() = InspectionRecord(
        time = 100, place = "Taller", address = "Calle 1", kilometers = 12000,
        notes = "Revisión de llantas", point = GnssReading(4.6, -74.1, 5f, Double.NaN, 99),
        googleAddress = false, photos = listOf("inspection_1234.jpg", "inspection_5678.jpg")
    )

    @Test fun savedPhotoReferencesRemainInTheVehicleHistoryAfterRestart() {
        var disk: String? = null
        val first = AutoHistoriaState()
        first.connectCache({ disk }, { disk = it; true })
        val vehicle = Vehicle("", "ABC123")
        first.addVehicle(vehicle)
        val inspection = record()
        first.addInspection(vehicle, inspection)
        val restarted = AutoHistoriaState()
        restarted.connectCache({ disk }, { disk = it; true })
        assertEquals(inspection.photos, restarted.history(vehicle).single().photos)
        assertEquals("Revisión de llantas", restarted.history(vehicle).single().notes)
        assertEquals(12000L, restarted.history(vehicle).single().kilometers)
    }

    @Test fun snapshotsFromBeforeThePhotoFeatureStillLoad() {
        val snapshot = LocalSnapshot(inspections = mapOf("ABC123" to listOf(CachedInspection.from(record()))))
        val oldJson = JSONObject(LocalCacheCodec.encode(snapshot))
        oldJson.getJSONObject("inspections").getJSONArray("ABC123").getJSONObject(0).remove("photos")
        val restored = LocalCacheCodec.decode(oldJson.toString()).inspections.getValue("ABC123").single().toRecord()
        assertTrue(restored.photos.isEmpty())
        assertEquals(12000L, restored.kilometers)
        assertEquals("Taller", restored.place)
    }
}
