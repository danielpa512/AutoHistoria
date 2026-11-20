# AutoHistoria

Android con Kotlin, Jetpack Compose y Navigation 3, conectado a una API JSON de Node.js con SQLite. Incluye cuentas separadas, perfil editable, avatar, vehículos, búsqueda, revisiones con fotos/GPS y apertura en Google Maps.

## Arranque local

1. Instala Node **24.14.1** o una versión compatible de Node 24.
2. En backend/, copia .env.example a .env y ejecuta npm start.
3. Abre esta carpeta en Android Studio.
4. Configura local.properties con la ruta de tu SDK y la dirección de la API:

```properties
sdk.dir=C:/Users/TU_USUARIO/AppData/Local/Android/Sdk
AUTOHISTORIA_API_URL=http://10.0.2.2:3000
```

10.0.2.2 accede al equipo anfitrión desde el emulador Android. Para un teléfono real conectado por USB, configura http://127.0.0.1:3000, recompila y usa adb reverse tcp:3000 tcp:3000. La variante debug permite HTTP para pruebas. Una compilación release necesita URL HTTPS y firma propia.

5. Compila con gradlew.bat :app:assembleDebug en Windows o ./gradlew :app:assembleDebug en Linux/macOS.
6. Crea una cuenta nueva, inicia sesión y registra un vehículo. Las cuentas del prototipo local no se importan automáticamente.

El token permanece en memoria; después de reiniciar el proceso debes iniciar sesión nuevamente. Los datos quedan en el servidor. Para PM2 y el contrato de endpoints consulta [backend/README.md](backend/README.md).

## Servicios externos

- Google Geocoding: configura GOOGLE_GEOCODING_API_KEY en backend/.env. No se incorpora al APK. Sin clave, el GPS funciona y puedes escribir la dirección manualmente.
- Google Maps: el botón del historial abre las coordenadas históricas y funciona sin clave de Maps URLs.
- Recuperación: configura RESEND_API_KEY y MAIL_FROM. No se simula el envío si faltan esas variables.
- La consulta externa de ficha vehicular procede del prototipo y requiere revisar el contrato y la validación del proveedor antes del uso comercial. Puedes registrar fichas manuales.

## Identidad y cargas

Se conserva el logo PNG solicitado. Es estático en las cabeceras y sólo gira durante una operación pendiente. No es un PNG animado: Compose aplica el movimiento.

## Verificación y alcance

Ejecuta npm test desde backend/ y gradlew.bat :app:testDebugUnitTest desde la raíz. [VALIDACION.md](VALIDACION.md) distingue pruebas realizadas y pendientes. [ANALISIS.md](ANALISIS.md) contiene la revisión extensa, decisiones técnicas y tareas previas a producción.

Esta es una base funcional de MVP. Necesita desplegarse con HTTPS, configurar correo/Google y completar las verificaciones en teléfono real. SQLite nativo de Node 24 emite un aviso experimental en la versión probada; PM2 se configura con una sola instancia.
