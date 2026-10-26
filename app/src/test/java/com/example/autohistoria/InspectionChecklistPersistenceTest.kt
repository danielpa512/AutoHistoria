package com.example.autohistoria

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class InspectionChecklistPersistenceTest {
    private val vehicle = Vehicle("", "ABC123")
    private fun inspection(checklist: InspectionChecklist) = InspectionRecord(
        time = 100, place = "Taller", address = "Calle 1", kilometers = 12000,
        notes = "Luz trasera rota", point = GnssReading(4.6, -74.1, 5f, Double.NaN, 99),
        googleAddress = false, checklist = checklist
    )

    @Test fun individualStatusesSurviveApplicationRestart() {
        var disk: String? = null
        val state = AutoHistoriaState()
        state.connectCache({ disk }, { disk = it; true })
        state.addVehicle(vehicle)
        val checklist = InspectionChecklist(
            tires = InspectionCondition.GOOD, brakes = InspectionCondition.REVIEW,
            lights = InspectionCondition.REPLACE, body = InspectionCondition.GOOD
        )
        state.addInspection(vehicle, inspection(checklist))
        val restarted = AutoHistoriaState()
        restarted.connectCache({ disk }, { disk = it; true })
        assertEquals(checklist, restarted.history(vehicle).single().checklist)
        assertTrue(restarted.history(vehicle).single().checklist.isComplete)
        assertEquals(InspectionCondition.REPLACE, restarted.history(vehicle).single().checklist.lights)
    }

    @Test fun oldInspectionsAreNotAutomaticallyMarkedAsGood() {
        val snapshot = LocalSnapshot(inspections = mapOf(vehicle.recordKey() to listOf(CachedInspection.from(inspection(InspectionChecklist())))))
        val oldJson = JSONObject(LocalCacheCodec.encode(snapshot))
        oldJson.getJSONObject("inspections").getJSONArray(vehicle.recordKey()).getJSONObject(0).remove("checklist")
        val restored = LocalCacheCodec.decode(oldJson.toString()).inspections.getValue(vehicle.recordKey()).single().toRecord()
        assertEquals(0, restored.checklist.completed)
        assertFalse(restored.checklist.isComplete)
        assertNull(restored.checklist.tires)
        assertEquals(12000L, restored.kilometers)
    }
}
