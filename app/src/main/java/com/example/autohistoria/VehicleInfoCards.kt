package com.example.autohistoria

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

private val InfoInk = Color(0xFF142039)
private val InfoMuted = Color(0xFF667085)
private val InfoGreen = Color(0xFF087F6E)
private val InfoBackground = Color(0xFFEAF5F1)

internal fun VehicleDetails.displayTitle(): String {
    val displayBrand = if (brand.replace(" ", "").replace("-", "").equals("MERCEDESBENZ", true))
        "Mercedes-Benz" else brand
    return listOf(displayBrand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Vehículo registrado" }
}

internal fun displayVehicleLabel(raw: String): String {
    if (raw.isBlank() || raw == "Sin información") return "No informado"
    return raw.lowercase(Locale.forLanguageTag("es-CO")).replaceFirstChar { it.titlecase() }
}

@Composable
private fun InfoIcon(resource: Int, background: Color = InfoBackground) {
    Surface(color = background, shape = RoundedCornerShape(14.dp)) {
        Icon(painterResource(resource), contentDescription = null, tint = InfoGreen,
            modifier = Modifier.padding(12.dp).size(26.dp))
    }
}

@Composable
private fun IconMetric(resource: Int, label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier.semantics(mergeDescendants = true) {}, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoIcon(resource)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(label, color = InfoMuted, style = MaterialTheme.typography.labelMedium)
                Text(value, color = InfoInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IconDetail(resource: Int, label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        InfoIcon(resource)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = InfoMuted)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default, color = InfoInk)
        }
    }
}

@Composable
private fun IconSection(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = InfoMuted, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
internal fun VehicleQuickFacts(details: VehicleDetails) {
    val displacement = details.value("displacement", "").toBigDecimalOrNull()
    val engine = displacement?.let { NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO")).format(it) + " cc" }
        ?: "No informado"
    val passengers = details.value("passengers", "").toIntOrNull()?.let { "$it personas" } ?: "No informado"
    val facts = listOf(
        Triple(R.drawable.ic_calendar, "Año modelo", details.year.takeUnless { it == "Año sin consultar" } ?: "Sin consultar"),
        Triple(R.drawable.ic_engine, "Cilindraje del motor", engine),
        Triple(R.drawable.ic_passengers, "Capacidad", passengers),
        Triple(R.drawable.ic_service, "Tipo de servicio", displayVehicleLabel(details.label("service")))
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tu vehículo de un vistazo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        BoxWithConstraints {
            if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    facts.forEach { (icon, label, value) -> IconMetric(icon, label, value, Modifier.fillMaxWidth()) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    facts.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (icon, label, value) -> IconMetric(icon, label, value, Modifier.weight(1f).fillMaxHeight()) }
                        }
                    }
                }
            }
        }
        IconSection("Características del vehículo") {
            IconDetail(R.drawable.ic_car, "Marca y modelo", details.displayTitle())
            HorizontalDivider(color = Color(0xFFE5E9EF))
            IconDetail(R.drawable.ic_car, "Clase de vehículo", displayVehicleLabel(details.categories))
        }
    }
}

@Composable
internal fun VehicleIdentifiers(details: VehicleDetails) {
    IconSection("Identificación del vehículo", "Los identificadores pueden aparecer parcialmente ocultos según la fuente.") {
        IconDetail(R.drawable.ic_vin, "VIN · identificación del vehículo",
            details.value("vin", details.original.vin.ifBlank { "No registrado" }), true)
        HorizontalDivider(color = Color(0xFFE5E9EF))
        IconDetail(R.drawable.ic_engine, "Número de motor", details.value("engine"), true)
        HorizontalDivider(color = Color(0xFFE5E9EF))
        IconDetail(R.drawable.ic_chassis, "Número de chasis", details.value("chassis"), true)
    }
}

@Composable
internal fun VehicleRegion(details: VehicleDetails) {
    val department = details.label("department")
    val city = details.label("city")
    IconSection("Ubicación informada", "Datos de la consulta vehicular; no es la posición GPS actual del auto.") {
        IconDetail(R.drawable.ic_location, "Departamento", displayVehicleLabel(department))
        if (city != "Sin información") IconDetail(R.drawable.ic_location, "Ciudad", displayVehicleLabel(city))
        if (details.zone != department && details.zone != "Sin información")
            IconDetail(R.drawable.ic_location, "Zona de circulación", displayVehicleLabel(details.zone))
    }
}
