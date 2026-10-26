package com.example.autohistoria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun VehicleReportPreview() {
    val state = remember {
        AutoHistoriaState().apply {
            val vehicle = Vehicle("", "ABC123")
            vehicles.add(vehicle)
            reports[vehicle.recordKey()] = SavedReport(
                """{"vehicle":{"brand":"MERCEDESBENZ","model":"E300","year":2025,"plate":"ABC123","displacement":"1999","passengers":5,"vin":"**************614","engine":"***********686","chassis":"**************614","service":{"label":"PARTICULAR"},"type":{"label":"AUTOMOVILES FAMILIARES"},"department":{"label":"CUNDINAMARCA"}}}""", System.currentTimeMillis())
        }
    }
    AutoHistoriaTheme { VehicleReportScreen(state.vehicles.first(), state, {}, {}) }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun GaragePreview() {
    val state = remember { AutoHistoriaState() }
    AutoHistoriaTheme { GarageHome(state, {}, {}, {}, {}) }
}
