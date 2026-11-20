package com.example.autohistoria

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class InspectionCondition(val label: String) {
    GOOD("Bien"), REVIEW("Revisar"), REPLACE("Cambiar")
}

enum class InspectionPart(val title: String, val description: String) {
    TIRES("Llantas", "Estado visible y desgaste"),
    BRAKES("Frenos", "Condición observada durante la revisión"),
    LIGHTS("Luces", "Faros, direccionales y luces de freno"),
    BODY("Carrocería", "Golpes, rayones y estado exterior")
}

@Serializable
data class InspectionChecklist(
    val tires: InspectionCondition? = null,
    val brakes: InspectionCondition? = null,
    val lights: InspectionCondition? = null,
    val body: InspectionCondition? = null
) {
    operator fun get(part: InspectionPart): InspectionCondition? = when (part) {
        InspectionPart.TIRES -> tires
        InspectionPart.BRAKES -> brakes
        InspectionPart.LIGHTS -> lights
        InspectionPart.BODY -> body
    }
    fun update(part: InspectionPart, value: InspectionCondition) = when (part) {
        InspectionPart.TIRES -> copy(tires = value)
        InspectionPart.BRAKES -> copy(brakes = value)
        InspectionPart.LIGHTS -> copy(lights = value)
        InspectionPart.BODY -> copy(body = value)
    }
    val completed get() = InspectionPart.entries.count { this[it] != null }
    val isComplete get() = completed == InspectionPart.entries.size
}

internal val InspectionChecklistSaver = Saver<InspectionChecklist, String>(
    save = { Json.encodeToString(it) },
    restore = { runCatching { Json.decodeFromString<InspectionChecklist>(it) }.getOrDefault(InspectionChecklist()) }
)

private fun InspectionPart.icon() = when (this) {
    InspectionPart.TIRES -> R.drawable.ic_tire
    InspectionPart.BRAKES -> R.drawable.ic_brakes
    InspectionPart.LIGHTS -> R.drawable.ic_lights
    InspectionPart.BODY -> R.drawable.ic_car
}
private fun InspectionCondition?.ink() = when (this) {
    InspectionCondition.GOOD -> Color(0xFF087F6E)
    InspectionCondition.REVIEW -> Color(0xFF946200)
    InspectionCondition.REPLACE -> Color(0xFFB43E2F)
    null -> Color(0xFF667085)
}
private fun InspectionCondition?.background() = when (this) {
    InspectionCondition.GOOD -> Color(0xFFE7F4EE)
    InspectionCondition.REVIEW -> Color(0xFFFFF3D0)
    InspectionCondition.REPLACE -> Color(0xFFFFEBE7)
    null -> Color(0xFFF1F3F6)
}

@Composable
private fun ChecklistPartHeader(part: InspectionPart, state: InspectionCondition?, showHint: Boolean) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = state.background(), shape = RoundedCornerShape(12.dp)) {
            Icon(painterResource(part.icon()), contentDescription = null, tint = state.ink(),
                modifier = Modifier.padding(11.dp).size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(part.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(if (showHint) part.description else state?.label ?: "No evaluado",
                color = if (showHint) Color(0xFF667085) else state.ink(),
                style = MaterialTheme.typography.bodySmall)
        }
        if (showHint && state != null) Text(state.label, color = state.ink(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun InspectionChecklistEditor(checklist: InspectionChecklist, onChange: (InspectionChecklist) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${checklist.completed} de 4 puntos evaluados", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { checklist.completed / 4f }, modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF087F6E))
        InspectionPart.entries.forEach { part ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChecklistPartHeader(part, checklist[part], true)
                if (LocalDensity.current.fontScale > 1.25f) {
                    Column {
                        InspectionCondition.entries.forEach { condition ->
                            ConditionChoice(condition, checklist[part] == condition, Modifier.fillMaxWidth()) {
                                onChange(checklist.update(part, condition))
                            }
                        }
                    }
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InspectionCondition.entries.forEach { condition ->
                        ConditionChoice(condition, checklist[part] == condition, Modifier.weight(1f)) {
                            onChange(checklist.update(part, condition))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFFE5E9EF))
            }
        }
        Text("Evaluación registrada por el usuario. No reemplaza una revisión técnico-mecánica ni un diagnóstico profesional.",
            color = Color(0xFF667085), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ConditionChoice(condition: InspectionCondition, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        label = { Text(condition.label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = condition.background(),
            selectedLabelColor = condition.ink()),
        border = BorderStroke(1.dp, if (selected) condition.ink() else Color(0xFFD5DAE2))
    )
}

@Composable
internal fun InspectionChecklistSummary(checklist: InspectionChecklist) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Checklist de inspección", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (checklist.completed == 0) Text("Esta inspección no tiene una checklist registrada.",
            color = Color(0xFF667085), style = MaterialTheme.typography.bodySmall)
        else {
            InspectionPart.entries.forEach { part -> ChecklistPartHeader(part, checklist[part], false) }
            Text("Estado declarado durante esta inspección", color = Color(0xFF667085), style = MaterialTheme.typography.labelSmall)
        }
    }
}
