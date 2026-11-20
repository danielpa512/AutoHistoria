package com.example.autohistoria

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Serializable
data class PublicVehicleReport(val id: String) : NavKey
@Serializable data object Login : NavKey
@Serializable data object Register : NavKey
@Serializable data object Recovery : NavKey
@Serializable data object Profile : NavKey
@Serializable data object Menu : NavKey
@Serializable data object RegisterVehicle : NavKey
@Serializable data object VehicleList : NavKey
@Serializable data class Vehicle(val vin: String, val plate: String) : NavKey
@Serializable data class NewInspection(val vehicle: Vehicle) : NavKey
data class GnssReading(val lat: Double, val lon: Double, val accuracy: Float, val altitude: Double, val time: Long)

class MainActivity : ComponentActivity() {
    private val state by lazy { ViewModelProvider(this)[AutoHistoriaState::class.java] }
    private val handler = Handler(Looper.getMainLooper())
    private var locationListener: LocationListener? = null
    private var locationFailure: ((String) -> Unit)? = null
    private var timeout: Runnable? = null
    private var afterPermission: (() -> Unit)? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val pending = afterPermission
        afterPermission = null
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            pending?.invoke()
        else locationFailure?.invoke("Necesitamos ubicación precisa. Actívala en los permisos de la aplicación.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state.configureApi(applicationContext)
        setContent {
            AutoHistoriaTheme {
                val stack = rememberNavBackStack(Login)
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                LaunchedEffect(state.currentUser) {
                    if (state.currentUser == null && stack.lastOrNull() != Login && stack.lastOrNull() != Register && stack.lastOrNull() != Recovery) {
                        stack.clear(); stack.add(Login)
                    }
                }
                val current = stack.lastOrNull()
                val showTabs = state.currentUser != null && (current == Menu || current == VehicleList || current == Profile)
                fun selectTab(route: NavKey) {
                    stack.clear(); stack.add(Menu)
                    if(route != Menu) stack.add(route)
                }
                Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).consumeWindowInsets(if(showTabs) WindowInsets.navigationBars else WindowInsets(0,0,0,0))) {
                NavDisplay(stack, entryProvider = entryProvider {
                    addEntryProvider(Login) {
                        AccessScreen(state, false, { stack.clear(); stack.add(Menu) }, { stack.add(Register) },{ stack.add(Recovery) })
                    }
                    addEntryProvider(Register) {
                        AccessScreen(
                            state,
                            true,
                            {
                                stack.clear()
                                stack.add(Menu)
                            },
                            {
                                stack.removeLastOrNull()
                            }
                        )
                    }
                    addEntryProvider(Menu) {
                        GarageHome(state, { stack.add(RegisterVehicle) }, { stack.add(VehicleList) },
                            { stack.add(it) }, { scope.launch { try { state.logout() } catch (_: Exception) { } finally { stack.clear(); stack.add(Login) } } }, { stack.add(Profile) })
                    }
                    addEntryProvider(Profile) {
                        ProfileScreen(state,{ stack.removeLastOrNull() },{ stack.clear(); stack.add(Login) })
                    }
                    addEntryProvider(Recovery) { RecoveryScreen(state,{stack.removeLastOrNull()}) }
                    addEntryProvider(RegisterVehicle) {
                        AddVehicleScreen(state, { stack.removeLastOrNull() }) { vehicle ->
                            stack.removeLastOrNull()
                            stack.add(vehicle)
                        }
                    }

                    addEntryProvider(VehicleList) {
                        GarageList(
                            state,
                            { stack.removeLastOrNull() },
                            { id ->
                                stack.add(PublicVehicleReport(id))
                            }
                        )
                    }
                    addEntryProvider(PublicVehicleReport::class) { route ->
                        PublicVehicleScreen(
                            state = state,
                            id = route.id,
                            onBack = {
                                stack.removeLastOrNull()
                            }
                        )
                    }

                    addEntryProvider(Vehicle::class) { vehicle ->
                        VehicleReportScreen(vehicle, state, { stack.removeLastOrNull() }, { stack.add(NewInspection(vehicle)) })
                    }
                    addEntryProvider(NewInspection::class) { route ->
                        InspectionEditor(route.vehicle, state, ::requestGnss, { stopLocation() }, { stack.removeLastOrNull() })
                    }
                })
                }

                    if (showTabs) NavigationBar {
                        val tabs: List<Triple<NavKey, String, Int>> = listOf(
                            Triple(Menu, "Inicio", R.drawable.ic_nav_inicio),
                            Triple(VehicleList, "Buscar", R.drawable.ic_nav_buscar),
                            Triple(Profile, "Perfil", R.drawable.ic_nav_perfil)
                        )

                        tabs.forEach { (route, label, iconRes) ->
                            NavigationBarItem(
                                selected = current == route,
                                onClick = { selectTab(route) },
                                icon = {
                                    Icon(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(label)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun stopLocation() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationListener?.let { manager.removeUpdates(it) }
        locationListener = null
    }

    private fun requestGnss(ok: (GnssReading) -> Unit, fail: (String) -> Unit) {
        stopLocation()
        locationFailure = fail
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            afterPermission = { requestGnss(ok, fail) }
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            fail("Activa la ubicación del teléfono y vuelve a capturar el lugar.")
            return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                stopLocation()
                ok(GnssReading(location.latitude, location.longitude, location.accuracy,
                    if (location.hasAltitude()) location.altitude else Double.NaN, location.time))
            }
            override fun onProviderDisabled(provider: String) {
                stopLocation(); fail("Se desactivó el GPS. Actívalo y vuelve a intentar.")
            }
        }
        locationListener = listener
        val deadline = Runnable {
            stopLocation()
            fail("El GPS no obtuvo una posición en 45 segundos. Prueba al aire libre.")
        }
        timeout = deadline
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            handler.postDelayed(deadline, 45000L)
        } catch (_: SecurityException) { stopLocation(); fail("No se pudo acceder a la ubicación precisa.") }
    }
    override fun onStop() {
        val wasWaiting = locationListener != null
        stopLocation()
        if (wasWaiting) locationFailure?.invoke("Captura pausada. Pulsa nuevamente al regresar a la aplicación.")
        super.onStop()
    }
}
