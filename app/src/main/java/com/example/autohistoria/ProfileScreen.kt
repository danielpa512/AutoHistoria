package com.example.autohistoria

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ProfileScreen(state: AutoHistoriaState, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val profile = state.profile ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var lastName by rememberSaveable(profile.id) { mutableStateOf(profile.lastName) }
    var email by rememberSaveable(profile.id) { mutableStateOf(profile.email) }
    var phone by rememberSaveable(profile.id) { mutableStateOf(profile.phone) }
    var city by rememberSaveable(profile.id) { mutableStateOf(profile.city) }
    var bio by rememberSaveable(profile.id) { mutableStateOf(profile.bio) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var avatar by remember { mutableStateOf<String?>(null) }
    val picture = avatar ?: profile.avatar
    val bitmap = remember(picture) { runCatching {
        if (picture.isBlank()) null else android.util.Base64.decode(picture.substringAfter(','),android.util.Base64.DEFAULT).let {
            android.graphics.BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap()
        }
    }.getOrNull() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { avatar = encodeImage(context,uri); message = "Foto seleccionada. Pulsa Guardar cambios." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = e.message.orEmpty() }
            finally { busy = false }
        }
    }
    Surface {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack,enabled = !busy) { Text("Volver al garaje") }
            Text("Mi perfil",style = MaterialTheme.typography.headlineMedium)
            if (bitmap != null) Image(bitmap,null,Modifier.size(96.dp).clip(CircleShape),contentScale = ContentScale.Crop)
            else Text("Añade una foto para personalizar tu cuenta")
            OutlinedButton(enabled = !busy,onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Elegir foto") }
            TextButton(enabled = !busy,onClick = { avatar = "" }) { Text("Quitar foto") }
            for ((label,value,change) in listOf(
                Triple("Nombres",name,{ v: String -> name=v }),Triple("Apellidos",lastName,{v: String -> lastName=v}),
                Triple("Correo",email,{v: String -> email=v}),Triple("Teléfono",phone,{v: String -> phone=v}),
                Triple("Ciudad",city,{v: String -> city=v}),Triple("Sobre mí",bio,{v: String -> bio=v}))) {
                OutlinedTextField(value,change,enabled = !busy,label={Text(label)},modifier=Modifier.fillMaxWidth())
            }
            OutlinedTextField(currentPassword,{currentPassword=it},enabled = !busy,label={Text("Contraseña actual")},
                supportingText={Text("Necesaria para cambiar el correo o la contraseña.")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            if (busy) BrandLoading("Guardando cambios…")
            if (message.isNotBlank()) Text(message)
            Button(enabled = !busy,onClick = { scope.launch {
                busy=true
                try {
                    state.updateProfile(JSONObject().put("name",name).put("lastName",lastName).put("email",email).put("phone",phone)
                        .put("city",city).put("bio",bio).put("currentPassword",currentPassword).apply { avatar?.let { put("avatar",it) } })
                    currentPassword=""; message="Perfil actualizado."; avatar=null
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false }
            } }) { Text("Guardar cambios") }
            HorizontalDivider()
            Text("Seguridad",style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(newPassword,{newPassword=it},enabled = !busy,label={Text("Nueva contraseña (12 caracteres mínimo)")},
                visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
            OutlinedButton(enabled = !busy,onClick={ scope.launch {
                busy=true
                try { state.changePassword(currentPassword,newPassword); onLoggedOut() }
                catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { message=e.message.orEmpty() } finally { busy=false }
            } }) { Text("Cambiar contraseña y cerrar sesiones") }
        }
    }
}
