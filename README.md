# AutoHistoria

Prototipo Android con Jetpack Compose y Navigation 3, inspirado en informes vehiculares.
Sin base de datos: las cuentas, fichas e inspecciones se conservan en almacenamiento
local privado mediante SharedPreferences y JSON. Permanecen al cerrar o reiniciar
la aplicación; se eliminan al borrar sus datos o desinstalarla.

## Funcionalidad

- Registro y login local con contraseñas protegidas mediante PBKDF2 y sal individual.
- Sin correos duplicados ni vehículos repetidos por placa o VIN normalizados.
- Garaje con registro y búsqueda por placa o VIN.
- Informe con marca, modelo, año, identificación y datos técnicos recibidos por API.
- Conservación local de la última consulta y su fecha, sin guardar tokens de sesión.
- Inspecciones por vehículo con nombre del lugar, dirección, kilometraje y observaciones.
- Captura GNSS real mediante `LocationManager.GPS_PROVIDER`, con permiso, timeout y cancelación.
- Google Geocoding convierte las coordenadas en una dirección aproximada.
- Cronología de inspecciones con coordenadas, precisión y fecha.
- Checklist por visita para llantas, frenos, luces y carrocería, con estados Bien, Revisar y Cambiar.
  Los cuatro puntos deben evaluarse antes de guardar una nueva inspección.
  Se muestran con iconos vectoriales, texto y colores, y se conservan en el historial local.
- Fotos por inspección: cámara o galería, hasta 6 imágenes, con miniaturas y vista ampliada.
  Las fotos se copian a la carpeta privada de la app y se conservan al reiniciar.
  La cámara usa la aplicación de cámara del teléfono y FileProvider; la galería usa el selector de fotos del sistema.
- Diseño Material 3 con cabecera de informe, tarjetas y cronología.

El GPS usa Android; la dirección usa Google Geocoding. No usa Fused Location ni un mapa de Google embebido.
El nombre comercial del taller o lugar lo confirma el usuario: Geocoding devuelve una dirección, no un negocio certificado.

## Uso

1. Abre la carpeta en Android Studio y sincroniza Gradle.
2. Crea una cuenta local y entra al garaje.
3. Registra placa, VIN o ambos. La consulta externa de ficha requiere placa.
4. Abre el informe y pulsa **Consultar datos del vehículo**.
5. Pulsa **Registrar nueva inspección**, captura ubicación y confirma el lugar.
6. Escribe el kilometraje y observaciones y guarda. La visita aparece en la cronología.
7. Al volver al listado, cerrar la app o reiniciar el teléfono, se conservan cuentas,
   vehículos, fichas e inspecciones. Inicia sesión con la cuenta ya registrada.

## Configuración de Google

La clave se lee de `local.properties`, excluido del repositorio:

```properties
GOOGLE_GEOCODING_API_KEY=tu_clave
```

Habilita **Geocoding API** en el proyecto de Google Cloud asociado y comprueba facturación y restricciones.
La clave proporcionada devolvió REQUEST_DENIED al comprobarse porque esa API no estaba activada.
Mientras se habilita, el formulario permite usar las coordenadas reales y escribir el lugar manualmente.
La clave se incorpora al APK al compilar; una distribución pública requiere proteger/restringir su uso.

## Verificación

Hay pruebas de asociación y orden de inspecciones, compatibilidad con las dos respuestas JSON observadas,
ocultación de credenciales, restauración tras reinicio, duplicados y errores de escritura.
Se ejecutan con `gradlew :app:testDebugUnitTest`.
Las funciones con `@Preview` permiten revisar el garaje y el informe desde Android Studio.

En interiores o en un emulador, la primera lectura GPS puede tardar o no llegar. En un dispositivo real, activa la ubicación y prueba al aire libre.
