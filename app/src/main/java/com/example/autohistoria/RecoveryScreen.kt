package com.example.autohistoria

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun RecoveryScreen(state: AutoHistoriaState,onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    fun submit(action: suspend () -> String) { scope.launch {
        busy=true
        try { message=action() }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { message=e.message.orEmpty() }
        finally { busy=false }
    } }
    Surface { Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=onBack,enabled=!busy) { Text("Volver al inicio de sesión") }
        Text("Recuperar acceso",style=MaterialTheme.typography.headlineMedium)
        Text("Solicita un código y cópialo desde tu correo. Sólo se puede usar una vez.")
        OutlinedTextField(email,{email=it},enabled=!busy,label={Text("Correo electrónico")},modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy,onClick={submit { state.recoverAccount("/auth/forgot-password",JSONObject().put("email",email)).getString("message") }}) { Text("Enviar código") }
        HorizontalDivider()
        OutlinedTextField(token,{token=it.trim()},enabled=!busy,label={Text("Código del correo")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},enabled=!busy,label={Text("Nueva contraseña: mínimo 12 caracteres")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
        Button(enabled=!busy,onClick={submit {
            state.recoverAccount("/auth/reset-password",JSONObject().put("token",token).put("password",password))
            token="";password="";"Contraseña actualizada. Ya puedes volver e iniciar sesión."
        }}) { Text("Restablecer contraseña") }
        if(busy) BrandLoading("Procesando solicitud…")
        if(message.isNotBlank()) Text(message)
    } }
}
