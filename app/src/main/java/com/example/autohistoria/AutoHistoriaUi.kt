package com.example.autohistoria

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF142039)
private val Muted = Color(0xFF667085)
private val Paper = Color(0xFFF3F5F8)
private val Gold = Color(0xFFFFD84D)
private val Green = Color(0xFF087F6E)
private val Border = Color(0xFFE5E9EF)

@Composable
fun AutoHistoriaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Color.White, secondary = Green, tertiary = Gold,
            background = Paper, surface = Color.White, onSurface = Ink,
            onSurfaceVariant = Muted, surfaceVariant = Color(0xFFEDF0F5), outline = Color(0xFFBBC3D0)
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}

private fun dateText(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.forLanguageTag("es-CO")).format(Date(time))
private fun kmText(value: Long) = String.format(Locale.forLanguageTag("es-CO"), "%,d km", value)

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = Gold, shape = RoundedCornerShape(10.dp)) {
            Text("A", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = Ink, fontWeight = FontWeight.Black)
        }
        Text("auto", fontWeight = FontWeight.Black, fontSize = 21.sp, color = Ink)
        Text("historia", fontWeight = FontWeight.Medium, fontSize = 21.sp, color = Ink)
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String = "", onBack: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Brand()
            if (onBack != null) TextButton(onClick = onBack) { Text("Volver") }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
private fun ContentCard(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted)
            content()
        }
    }
}

@Composable
private fun Tag(text: String, color: Color = Color(0xFFE9F4F0), ink: Color = Green) {
    Surface(color = color, shape = RoundedCornerShape(8.dp)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
        Text(value.ifBlank { "Sin información" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MainAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Feedback(text: String, error: Boolean = false) {
    if (text.isNotBlank()) Surface(
        color = if (error) Color(0xFFFFF0ED) else Color(0xFFEAF5F1),
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall,
            color = if (error) Color(0xFF9F3326) else Green)
    }
}

@Composable
internal fun CarIllustration(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.red_sedan_front),
        contentDescription = "Sedán rojo visto de frente",
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = modifier
    )
}

@Composable
fun AccessScreen(state: AutoHistoriaState, register: Boolean, onDone: () -> Unit, onOther: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val accessScope = rememberCoroutineScope()
    Surface(color = Paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Brand()
            Card(colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.padding(24.dp)) {
                    Text("CONOCE SU HISTORIA", color = Gold, style = MaterialTheme.typography.labelMedium)
                    Text("Cada vehículo tiene\nuna historia.", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                    CarIllustration(Modifier.fillMaxWidth().height(110.dp))
                    Text("Consulta sus datos. Documenta cada inspección.", color = Color(0xFFD7DEEA), style = MaterialTheme.typography.bodySmall)
                }
            }
            ContentCard(if (register) "Crea tu cuenta" else "Bienvenido a tu garaje",
                "Tus cuentas y vehículos se guardan en este dispositivo") {
                if (register) OutlinedTextField(name, { name = it }, label = { Text("Nombre completo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Feedback(message, true)
                Feedback(state.storageError, true)
                MainAction(if (submitting) "Procesando…" else if (register) "Crear cuenta" else "Entrar a mi garaje",
                    enabled = !submitting) {
                    val normalized = email.trim().lowercase()
                    submitting = true
                    accessScope.launch {
                        try {
                            if (register) {
                                state.register(name, normalized, password)
                                onDone()
                            } else {
                                if (state.login(normalized, password)) onDone()
                                else message = "Correo o contraseña incorrectos. Puedes crear una cuenta si aún no estás registrado."
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
                        } catch (failure: Exception) {
                            message = failure.message ?: "No se pudo completar la operación."
                        } finally {
                            submitting = false
                        }
                    }
                }
                TextButton(onClick = onOther, modifier = Modifier.fillMaxWidth()) {
                    Text(if (register) "Ya tengo una cuenta" else "Crear una cuenta local")
                }
            }
        }
    }
}

@Composable
fun GarageHome(state: AutoHistoriaState, onAdd: () -> Unit, onList: () -> Unit, onOpen: (Vehicle) -> Unit, onLogout: () -> Unit) {
    Surface(color = Paper) {
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { PageHeader("Tu garaje", "Hola, ${state.currentUser?.name ?: "bienvenido"}. Aquí comienza la historia de tus vehículos.") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Ink)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AUTOHISTORIA", color = Gold, style = MaterialTheme.typography.labelLarge)
                        Text("Más claridad.\nMejores registros.", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Ficha del vehículo e inspecciones con ubicación en un solo informe.", color = Color(0xFFCBD4E2))
                        MainAction("Registrar vehículo", onClick = onAdd)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("VEHÍCULOS", state.vehicles.size.toString(), Modifier.weight(1f))
                    Metric("INSPECCIONES", state.inspections.values.sumOf { it.size }.toString(), Modifier.weight(1f))
                }
            }
            item { OutlinedButton(onClick = onList, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Ver datos de mis autos") } }
            item { Text("Últimos vehículos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (state.vehicles.isEmpty()) item { ContentCard("Tu garaje está vacío") { Text("Registra una placa o un VIN para empezar.", color = Muted) } }
            items(state.vehicles.takeLast(3).reversed(), key = { it.recordKey() }) { vehicle ->
                VehicleTile(vehicle, state) { onOpen(vehicle) }
            }
            item { TextButton(onClick = onLogout) { Text("Cerrar sesión") } }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp).heightIn(min = 66.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun VehicleTile(vehicle: Vehicle, state: AutoHistoriaState, onOpen: () -> Unit) {
    val details = VehicleDetails(vehicle, state.reports[vehicle.recordKey()])
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Paper, shape = RoundedCornerShape(12.dp)) { CarIllustration(Modifier.size(72.dp, 58.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(details.title, fontWeight = FontWeight.Bold)
                Text(vehicle.plate.ifBlank { vehicle.vin }, style = MaterialTheme.typography.bodySmall, color = Muted)
                Text("${state.history(vehicle).size} inspecciones", color = Green, style = MaterialTheme.typography.labelSmall)
            }
            Text("›", fontSize = 26.sp, color = Muted)
        }
    }
}

@Composable
fun GarageList(state: AutoHistoriaState, onBack: () -> Unit, onOpen: (Vehicle) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = state.vehicles.filter {
        it.plate.contains(search.trim(), true) || it.vin.contains(search.trim(), true)
    }
    Surface(color = Paper) {
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PageHeader("Mis vehículos", "Encuentra un informe por placa o VIN.", onBack) }
            item { OutlinedTextField(search, { search = it }, label = { Text("Buscar por placa o VIN") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if (filtered.isEmpty()) item { ContentCard("Sin resultados") { Text("No hay vehículos que coincidan con tu búsqueda.", color = Muted) } }
            items(filtered, key = { it.recordKey() }) { vehicle -> VehicleTile(vehicle, state) { onOpen(vehicle) } }
        }
    }
}

@Composable
fun AddVehicleScreen(state: AutoHistoriaState, onBack: () -> Unit, onSave: (Vehicle) -> Unit) {
    var plate by rememberSaveable { mutableStateOf("") }
    var vin by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var displacement by rememberSaveable { mutableStateOf("") }
    var passengers by rememberSaveable { mutableStateOf("") }
    var service by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var department by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var engine by rememberSaveable { mutableStateOf("") }
    var chassis by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Surface(color = Paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PageHeader("Un nuevo vehículo", "Regístralo para consultar su ficha y documentar inspecciones.", onBack)
            ContentCard("Identifica tu vehículo", "Puedes usar solo placa, solo VIN o los dos.") {
                OutlinedTextField(plate, { plate = it.uppercase().filter { char -> char.isLetterOrDigit() } },
                    label = { Text("Placa") }, placeholder = { Text("ABC123") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vin, { vin = it.uppercase().filter { char -> char.isLetterOrDigit() } },
                    label = { Text("VIN · opcional si tienes placa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("La consulta externa de datos generales requiere una placa.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = manual, onCheckedChange = { manual = it })
                    Text("Completar ficha manualmente", fontWeight = FontWeight.SemiBold)
                }
                if (manual) {
                    Text("Datos declarados por ti. No requieren conexión ni verificación externa.", color = Muted)
                    ManualVehicleField("Marca *", brand) { brand = it }
                    ManualVehicleField("Modelo o línea *", model) { model = it }
                    ManualVehicleField("Año *", year) { year = it.filter(Char::isDigit).take(4) }
                    ManualVehicleField("Cilindraje (cm³)", displacement) { displacement = it.filter(Char::isDigit) }
                    ManualVehicleField("Pasajeros", passengers) { passengers = it.filter(Char::isDigit) }
                    ManualVehicleField("Servicio (particular / público)", service) { service = it }
                    ManualVehicleField("Clase de vehículo", type) { type = it }
                    ManualVehicleField("Departamento", department) { department = it }
                    ManualVehicleField("Ciudad", city) { city = it }
                    ManualVehicleField("Número de motor", engine) { engine = it }
                    ManualVehicleField("Número de chasis", chassis) { chassis = it }
                }
                Feedback(message, true)
                MainAction("Crear informe") {
                    val candidate = Vehicle(vin.trim(), plate.trim())
                    when {
                        plate.isBlank() && vin.isBlank() -> message = "Ingresa al menos una placa o un VIN."
                        vin.isNotBlank() && !Regex("[A-HJ-NPR-Z0-9]{17}").matches(vin) -> message = "El VIN debe tener 17 caracteres y no incluir I, O ni Q."
                        manual && (brand.isBlank() || model.isBlank()) -> message = "Completa la marca y el modelo."
                        manual && (year.toIntOrNull() !in 1886..(java.time.Year.now().value + 1)) -> message = "Ingresa un año válido."
                        manual && ((displacement.isNotBlank() && (displacement.toIntOrNull() ?: 0) <= 0) || (passengers.isNotBlank() && (passengers.toIntOrNull() ?: 0) <= 0)) -> message = "El cilindraje y los pasajeros deben ser mayores que cero."
                        state.vehicles.any { (plate.isNotBlank() && it.plate == plate) || (vin.isNotBlank() && it.vin == vin) } -> message = "Ese vehículo ya está en tu garaje."
                        else -> runCatching {
                            val report = if (manual) SavedReport(org.json.JSONObject().put("vehicle", org.json.JSONObject().apply {
                                put("plate", plate.trim()); put("vin", vin.trim())
                                put("brand", brand.trim()); put("model", model.trim()); put("year", year.toInt())
                                if (displacement.isNotBlank()) put("displacement", displacement)
                                if (passengers.isNotBlank()) put("passengers", passengers.toInt())
                                fun label(key: String, value: String) { if (value.isNotBlank()) put(key, org.json.JSONObject().put("label", value.trim())) }
                                label("service", service); label("type", type); label("department", department); label("city", city)
                                if (engine.isNotBlank()) put("engine", engine.trim())
                                if (chassis.isNotBlank()) put("chassis", chassis.trim())
                            }).toString(), System.currentTimeMillis(), manual = true) else null
                            state.addVehicle(candidate, report)
                        }
                            .onSuccess { onSave(candidate) }
                            .onFailure { message = it.message ?: "No se pudo guardar el vehículo." }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualVehicleField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
fun VehicleReportScreen(vehicle: Vehicle, state: AutoHistoriaState, onBack: () -> Unit, onInspection: () -> Unit) {
    val saved = state.reports[vehicle.recordKey()]
    val details = VehicleDetails(vehicle, saved)
    val history = state.history(vehicle)
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var debug by remember { mutableStateOf(false) }
    Surface(color = Paper) {
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { PageHeader("Informe del vehículo", onBack = onBack) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Ink)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tag(if (saved == null) "FICHA SIN CONSULTAR" else if (saved.manual) "REGISTRO MANUAL" else "DATOS CONSULTADOS", Gold, Ink)
                        Text(details.displayTitle(), color = Color.White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                        Text("${details.year} · ${displayVehicleLabel(details.label("service"))}", color = Color(0xFFD8DEEA))
                        CarIllustration(Modifier.fillMaxWidth().height(105.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Tag(details.plate, Gold, Ink)
                            Text("${history.size} inspecciones", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (saved != null) item { VehicleQuickFacts(details) }
            item {
                ContentCard("Resumen del informe", "Consulta general y registros propios de inspección") {
                    DataRow("Ficha técnica", if (saved == null) "Pendiente de consulta" else if (saved.manual) "Datos declarados · ${dateText(saved.time)}" else "Disponible · ${dateText(saved.time)}")
                    HorizontalDivider(color = Border)
                    DataRow("Último kilometraje declarado", history.firstOrNull()?.let { kmText(it.kilometers) } ?: "Sin inspecciones registradas")
                    HorizontalDivider(color = Border)
                    DataRow("Siniestros y robos", "Sin fuente de antecedentes integrada")
                }
            }
            item {
                ContentCard("Datos generales", "Actualiza la ficha con la placa del vehículo.") {
                    if (message.isNotBlank()) Feedback(message, true)
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    MainAction(if (loading) "Consultando…" else if (saved == null) "Consultar datos del vehículo" else "Actualizar ficha",
                        enabled = !loading && vehicle.plate.isNotBlank()) {
                        scope.launch {
                            loading = true; message = ""
                            try { state.saveReport(vehicle, loadVehicleReport(vehicle.plate, token)) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (error: Exception) { message = error.message ?: "No se pudo consultar el vehículo." }
                            finally { loading = false }
                        }
                    }
                    if (vehicle.plate.isBlank()) Text("La consulta externa necesita placa; puedes registrar inspecciones con el VIN.", color = Muted)
                    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar opciones de consulta" else "Opciones de consulta") }
                    if (advanced) OutlinedTextField(token, { token = it }, label = { Text("Token de validación del servicio") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
            item { VehicleIdentifiers(details) }
            if (saved != null) item { VehicleRegion(details) }
            item {
                ContentCard("Historial de inspecciones", "Registros creados por ti. Cada visita conserva su lugar y kilometraje.") {
                    MainAction("Registrar nueva inspección", onClick = onInspection)
                    if (history.isEmpty()) Text("Aquí aparecerá la primera inspección de este vehículo.", color = Muted)
                }
            }
            items(history, key = { it.id }) { inspection -> InspectionTimelineCard(inspection) }
            if (saved != null) item {
                ContentCard("Detalle de la consulta") {
                    TextButton(onClick = { debug = !debug }) { Text(if (debug) "Ocultar JSON" else "Ver JSON de diagnóstico") }
                    if (debug) SelectionContainer { Text(details.safeJson, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                }
            }
            item { Text("Guardado en este dispositivo · sin base de datos", color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun InspectionTimelineCard(record: InspectionRecord) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Green, shape = RoundedCornerShape(50)) { Text("✓", Modifier.padding(8.dp), color = Color.White) }
        }
        Column(Modifier.weight(1f)) {
            ContentCard(record.place, dateText(record.time)) {
                Tag(kmText(record.kilometers), Color(0xFFFFF5D1), Ink)
                if (record.address.isNotBlank()) DataRow("Dirección", record.address)
                DataRow("Coordenadas", String.format(Locale.US, "%.6f, %.6f", record.point.lat, record.point.lon))
                DataRow("Precisión GPS", "%.1f m".format(record.point.accuracy))
                if (!record.point.altitude.isNaN()) DataRow("Altitud", "%.1f m".format(record.point.altitude))
                DataRow("Observaciones", record.notes.ifBlank { "Sin observaciones" })
                InspectionChecklistSummary(record.checklist)
                InspectionPhotoGallery(record.photos)
                Text(if (record.googleAddress) "Dirección obtenida con Google Maps" else "Lugar indicado por el usuario",
                    color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("Kilometraje declarado por el usuario", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun InspectionEditor(
    vehicle: Vehicle, state: AutoHistoriaState,
    gps: ((GnssReading) -> Unit, (String) -> Unit) -> Unit,
    cancelGps: () -> Unit, onBack: () -> Unit
) {
    var place by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var kilometers by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var checklist by rememberSaveable(stateSaver = InspectionChecklistSaver) { mutableStateOf(InspectionChecklist()) }
    var photos by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var importingPhotos by remember { mutableStateOf(false) }
    var point by rememberSaveable(stateSaver = listSaver<GnssReading?, Any>(
        save = { value -> if (value == null) emptyList() else listOf(value.lat, value.lon, value.accuracy, value.altitude, value.time) },
        restore = { values -> if (values.isEmpty()) null else GnssReading(values[0] as Double, values[1] as Double,
            values[2] as Float, values[3] as Double, values[4] as Long) }
    )) { mutableStateOf<GnssReading?>(null) }
    var googleAddress by rememberSaveable { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var geoMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var active by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { active = false; cancelGps() } }
    Surface(color = Paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PageHeader("Nueva inspección", "Documenta una visita para ${vehicle.plate.ifBlank { vehicle.vin }}.", onBack)
            ContentCard("01  Lugar de la inspección", "Captura el GPS del teléfono. Google buscará la dirección de ese punto.") {
                MainAction(if (capturing) "Buscando señal GPS…" else if (resolving) "Buscando dirección…" else "Capturar mi ubicación",
                    enabled = !capturing && !resolving) {
                    capturing = true; error = ""; geoMessage = ""
                    gps({ reading ->
                        if (active) {
                            capturing = false; point = reading; googleAddress = false
                            address = ""; resolving = true
                            scope.launch {
                                try {
                                    val result = resolveGooglePlace(reading)
                                    address = result.address; googleAddress = true
                                    if (place.isBlank()) place = result.address.substringBefore(',')
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (failure: Exception) { geoMessage = failure.message ?: "Escribe el lugar manualmente." }
                                finally { resolving = false }
                            }
                        }
                    }, { if (active) { capturing = false; error = it } })
                }
                if (capturing || resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Feedback(geoMessage)
                point?.let {
                    Tag("Ubicación capturada")
                    DataRow("Coordenadas", String.format(Locale.US, "%.6f, %.6f", it.lat, it.lon))
                    DataRow("Precisión y hora", "%.1f m · %s".format(it.accuracy, dateText(it.time)))
                }
                OutlinedTextField(place, { place = it }, label = { Text("Nombre del lugar") },
                    placeholder = { Text("Ej. Taller Central · sede norte") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it; googleAddress = false }, label = { Text("Dirección") }, modifier = Modifier.fillMaxWidth())
                Text(if (googleAddress) "Dirección de Google Maps. Confirma el nombre del taller o lugar."
                    else "Puedes escribir el lugar manualmente si la dirección no está disponible.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            ContentCard("02  Registro de la visita") {
                OutlinedTextField(kilometers, { kilometers = it.filter(Char::isDigit) }, label = { Text("Kilometraje al inspeccionar") },
                    suffix = { Text("km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") },
                    placeholder = { Text("Estado del vehículo, revisión realizada o hallazgos…") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("El kilometraje es declarado por ti. La fecha y la ubicación se incorporan al registro.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            ContentCard("03  Checklist del vehículo", "Selecciona un estado para cada componente") {
                InspectionChecklistEditor(checklist) { checklist = it }
            }
            ContentCard("04  Fotos de la inspección", "Evidencia visual asociada a esta visita") {
                InspectionPhotoPicker(
                    photos = photos,
                    onChange = { photos = ArrayList(it) },
                    onBusy = { importingPhotos = it },
                    enabled = !capturing && !resolving
                )
            }
            Feedback(error, true)
            MainAction("Guardar inspección", enabled = !capturing && !resolving && !importingPhotos) {
                val km = kilometers.toLongOrNull()
                val location = point
                val previous = state.history(vehicle).firstOrNull()?.kilometers
                when {
                    place.isBlank() -> error = "Escribe el nombre del lugar de la inspección."
                    km == null || km < 0 -> error = "Ingresa un kilometraje válido."
                    location == null -> error = "Captura la ubicación GPS antes de guardar."
                    previous != null && km < previous -> error = "El kilometraje no puede ser menor al de la última inspección (${kmText(previous)})."
                    !checklist.isComplete -> error = "Completa los cuatro puntos de la checklist antes de guardar."
                    else -> {
                        runCatching {
                            state.addInspection(vehicle, InspectionRecord(time = System.currentTimeMillis(), place = place.trim(),
                                address = address.trim(), kilometers = km, notes = notes.trim(), point = location,
                                googleAddress = googleAddress, photos = photos.toList(), checklist = checklist))
                        }.onSuccess { onBack() }
                            .onFailure { error = it.message ?: "No se pudo guardar la inspección." }
                    }
                }
            }
            Text("Se guardará en el historial local del vehículo, también al cerrar la app.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
