# AutoHistoria: análisis técnico y evolución del proyecto

Revisión del 19 de noviembre de 2026. Base revisada: `danielpa512/AutoHistoria`, rama `master`, commit `5c33d9f74011b991d35fe4d706557204ee4fe230`. Este documento distingue lo observado en esa base, lo implementado en esta entrega y lo pendiente antes de una publicación pública.

## 1. Qué lenguaje utilizar y para qué sirve PM2

La aplicación Android sigue escrita en **Kotlin con Jetpack Compose y Navigation 3**. No es necesario cambiarla a JavaScript para añadir un backend. Android envía solicitudes HTTP y recibe JSON; el servidor puede estar escrito en un lenguaje diferente.

**Node.js** es el entorno donde corre el backend. **JavaScript** es el lenguaje utilizado en esta entrega. TypeScript es una alternativa apropiada cuando aumenten los contratos, módulos y colaboradores; normalmente se compila a JavaScript antes de ejecutarlo. **JSON** es el formato de intercambio de datos. **PM2** supervisa el proceso del servidor, lo reinicia si falla y facilita consultar sus registros. No sustituye la base de datos, la autenticación, el alojamiento ni HTTPS.

La separación queda así:

```text
Android / Kotlin / Jetpack Compose
              │ HTTPS + JSON
              ▼
AutoHistoria API / Node.js / JavaScript
              │
              ├─ SQLite: cuentas, sesiones, vehículos e inspecciones
              ├─ Google Geocoding: coordenadas → dirección
              └─ Resend: recuperación de contraseña, cuando se configure

PM2 mantiene en ejecución la API en el servidor.
Google Maps abre las coordenadas guardadas desde Android.
```

He elegido una API pequeña sin dependencias de npm para que la primera puesta en marcha sea reproducible. Utiliza módulos incluidos en Node 24.14.1: HTTP, criptografía y SQLite. La versión probada de Node emite un aviso de que `node:sqlite` es experimental; por eso se fija la familia Node 24 y esta entrega debe tratarse como base funcional de MVP, no como una plataforma de producción ya auditada. Para un servicio que va a crecer, recomiendo TypeScript, Fastify o NestJS, PostgreSQL, migraciones versionadas y almacenamiento separado de imágenes.

Referencia: [documentación de PM2](https://pm2.keymetrics.io/docs/usage/quick-start/).

## 2. Qué tenía realmente el repositorio

La base ya hacía más de lo que parecía: registro e inicio de sesión locales, un garaje, búsqueda por placa o VIN, ficha técnica, registro manual, inspecciones con kilometraje, checklist de cuatro componentes y fotografías. También capturaba ubicación GNSS real mediante Android y conservaba latitud, longitud, precisión, altitud y hora. No hacía falta inventar otra captura GPS.

La limitación principal era arquitectónica. `AutoHistoriaState.kt` mantenía una lista de cuentas y una lista global de vehículos. `LocalCache.kt` serializaba esa información en SharedPreferences. Los registros podían sobrevivir al cierre de la app, pero no existía una cuenta central accesible desde otro teléfono. Tampoco había una relación de propietario entre una cuenta y sus vehículos: distintas cuentas locales compartían el mismo garaje.

Las contraseñas locales no estaban en texto plano: se utilizaba PBKDF2 con sal individual. Eso era positivo, pero seguía siendo un control dentro de un dispositivo. No proporciona autorización entre clientes ni sesiones revocables en un servidor.

La ficha externa se consulta a un endpoint del proveedor de seguros incluido en el código. Hay un número de identificación fijo en la petición y un campo manual para el token de validación del proveedor. Esa integración necesita un contrato, documentación y credenciales adecuados antes de convertirse en una función comercial estable. No demuestra antecedentes de accidentes, robos o propietarios anteriores. No cambié la app para afirmar que tiene información que la fuente no entrega.

El README original también documentaba un rechazo anterior de Google Geocoding. Eso es un antecedente del proyecto, no una comprobación del estado actual de tu cuenta de Google. El repositorio público no contiene la clave privada de `local.properties`; no se puede concluir desde GitHub que facturación y permisos estén activos ahora.

Fuente de la revisión: [repositorio AutoHistoria](https://github.com/danielpa512/AutoHistoria).

## 3. Qué queda implementado

| Área | Comportamiento de esta entrega |
|---|---|
| Registro | Cuenta en servidor, correo normalizado y único, contraseña entre 12 y 128 caracteres |
| Contraseñas | Scrypt con sal aleatoria; nunca se devuelven hashes al cliente |
| Login | Token aleatorio de sesión, almacenado como hash en la base de datos |
| Sesiones | Caducidad de siete días, logout, revocación global y revocación tras cambio o recuperación de contraseña |
| Recuperación | Código aleatorio de un uso, caducidad de 15 minutos, pantalla Android y adaptador Resend |
| Perfil | Nombres, apellidos, correo, teléfono, ciudad, biografía y foto |
| Cambio de correo | Exige confirmar la contraseña actual y conserva la unicidad |
| Fotos de perfil | Selector del sistema, reducción de tamaño, JPEG sin EXIF, copia remota y copia privada en caché |
| Vehículos | Registro por placa/VIN, separación por usuario y detección de duplicados dentro de cada cuenta |
| Búsqueda | Acceso visible desde el garaje; placa, VIN, marca y modelo en Android; consulta y paginación en API |
| Inspecciones | Persistencia remota de lugar, dirección, kilometraje, notas, GPS, checklist y hasta seis fotos |
| Consistencia | Repetir una inspección con su mismo ID no duplica el registro; el servidor rechaza kilometraje decreciente |
| Google | Geocodificación desde backend y apertura de coordenadas guardadas en Google Maps |
| Identidad visual | El mismo logo PNG generado, utilizado en cabeceras, icono y cargas |
| Movimiento | Giro del logo sólo durante operaciones pendientes; cambios de tamaño y aparición de opciones animados |

La app ya no conecta automáticamente el almacén antiguo compartido al iniciar. Conservé el código y las pruebas del formato previo para una futura migración explícita. No se asignan silenciosamente los vehículos antiguos a la primera persona que inicie sesión: eso atribuiría información sin saber a quién pertenece.

## 4. Cuentas y autorización

Una cuenta debe ser la unidad de acceso. La API obtiene el usuario desde el token y consulta los vehículos filtrando por ese usuario. No acepta un `userId` enviado por Android para decidir quién es el propietario. Si una persona conoce el ID de un vehículo de otra cuenta, recibe una respuesta de no encontrado.

La misma placa puede aparecer en dos cuentas distintas. Es una decisión deliberada: registrar una placa no demuestra propiedad legal. En esta versión cada cuenta conserva su expediente privado. Un historial global compartido requeriría un modelo diferente: propietario acreditado, talleres, permisos, transferencias, consentimientos y auditoría de cambios.

El token sólo vive en memoria en Android. Al reiniciar el proceso se vuelve a iniciar sesión; no se almacena una contraseña ni un token en SharedPreferences. Un siguiente paso sería mantener la sesión con Android Keystore y renovación de tokens, junto con listado de dispositivos y cierre remoto. La API actual usa sesiones opacas consultadas en base de datos; no necesita JWT para funcionar correctamente.

Hay límites de peticiones y de intentos de autenticación, validación de entradas, consultas SQL con parámetros, límite del cuerpo HTTP y errores JSON con un identificador de solicitud. Los registros del servidor no incluyen contraseñas, cuerpos, tokens, correos ni coordenadas. Los límites están en memoria y corresponden a una sola instancia; se reinician con el proceso. Detrás de un proxy todos los clientes pueden compartir la IP observada: el despliegue debe ajustar la limitación en el proxy, sin confiar ciegamente en un `X-Forwarded-For` enviado por el cliente.

## 5. Perfil y caché de imágenes

La foto no debe existir únicamente en caché. Android puede borrar la caché cuando necesita espacio. Por eso la copia del servidor es la referencia y la copia local acelera la visualización. Cambiar de teléfono o limpiar la caché no debería destruir el avatar; iniciar sesión permite recuperarlo.

En esta implementación se aceptan JPEG y PNG con un límite de 400.000 bytes decodificados por imagen. Android reduce las imágenes y genera JPEG antes de enviarlas. Las fotos se transmiten como base64 dentro del JSON, lo que aumenta su tamaño aproximadamente un tercio. Es una solución simple para este MVP; para mucho contenido conviene usar subida multipart, almacenamiento de objetos, miniaturas y URLs con acceso autorizado.

La API comprueba el tipo declarado, el tamaño y la firma del archivo. Antes de aceptar archivos de clientes públicos arbitrarios conviene añadir decodificación y recodificación en el servidor, límites de píxeles y cuotas por cuenta. No se aceptan SVG ni URLs remotas como avatar. La caché remota se limpia al cerrar sesión; las fotos originales que el usuario seleccionó para una revisión siguen en el espacio privado utilizado por el prototipo, y una política de limpieza de borradores queda pendiente.

El perfil no ofrece editar IDs internos, propietario de registros, hashes, roles o fechas de auditoría. “Editar todos mis datos” debe cubrir información personal legítima, sin permitir alterar los controles de acceso.

Referencia: [Photo Picker de Android](https://developer.android.com/training/data-storage/shared/photo-picker).

## 6. Revisiones, ubicación y Google Maps

Cada revisión conserva el punto capturado durante esa visita. Abrir una revisión antigua utiliza ese punto histórico, no la ubicación actual del teléfono. Se guardan también la precisión y la hora de la lectura. La captura sólo se solicita al pulsar el botón; no se añadió seguimiento en segundo plano.

El nombre del taller lo confirma la persona. Una dirección de Google no certifica qué negocio realizó la revisión ni que el teléfono estuviera realmente allí. Las coordenadas enviadas por un cliente tampoco constituyen una prueba antifraude. Si AutoHistoria evoluciona hacia revisiones certificadas, harán falta identidad del inspector, relación con el taller, controles de integridad y trazabilidad.

El GPS y Geocoding son pasos independientes. Cuando Google falla, se conserva la lectura y se permite escribir el lugar manualmente. El botón **Abrir en Google Maps** usa una URL universal con `api=1` y las coordenadas. Puede abrir la aplicación de Maps o el navegador, y no necesita clave de API. Un mapa incrustado dentro de Compose sí requeriría otra integración, Maps SDK for Android, y su configuración correspondiente.

La clave de Geocoding se configura en el backend y deja de incorporarse al APK. Debe restringirse a la API y al servidor autorizado. No inserté una clave nueva ni cambié la facturación de tu proyecto de Google.

Referencia: [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started).

## 7. Logo, navegación y animaciones

Se conserva exactamente el logo solicitado: automóvil amarillo, flecha circular e indicador verde sobre fondo azul oscuro, en PNG. El PNG es estático; Compose aplica la rotación mientras se compone el indicador de carga. La animación usa una vuelta continua de 1,6 segundos y se elimina cuando termina la operación. No gira en el menú, en el perfil ni en una cabecera en reposo.

El componente se utiliza en acceso, recuperación, consultas, preparación de fotografías, captura/geocodificación y guardados. Se acompaña de texto para explicar qué está ocurriendo. Los botones de guardado quedan desactivados mientras su operación está pendiente. Las tarjetas ajustan suavemente su tamaño y las opciones adicionales aparecen con animación. Las animaciones Compose utilizan la escala de duración del sistema; falta comprobar físicamente los ajustes de accesibilidad y TalkBack en varios dispositivos.

El garaje incorpora dos entradas claras: **Buscar vehículos registrados** y **Mi perfil**. La búsqueda se limita a los registros de la propia cuenta. No se añadió un directorio público que exponga información de otros usuarios.

## 8. Base de datos y escalado

SQLite guarda los datos en disco, utiliza claves foráneas, índices de unicidad y transacciones para los cambios que deben completarse juntos. Es adecuado para una instalación pequeña con un proceso y un volumen moderado. La configuración PM2 incluye expresamente una instancia; no debe cambiarse a `instances: max` suponiendo que todo escalará automáticamente.

Para crecer propongo PostgreSQL con tablas de usuarios, sesiones, vehículos, relaciones usuario-vehículo, inspecciones, resultados de checklist y archivos. Las fotos deberían salir de las filas JSON y almacenarse en un servicio de objetos. Los informes y el historial deben paginarse por separado: actualmente la API pagina vehículos, pero cada vehículo incluye sus inspecciones y fotografías. Esto no es eficiente para expedientes con cientos de visitas.

La búsqueda actual es funcional para un garaje pequeño, pero filtra resultados cargados y no constituye un motor de búsqueda indexado. Para escala: índices normalizados de placa/VIN, búsqueda por campos técnicos concretos, límites por cuenta y respuestas resumidas para listados. También conviene separar controladores, servicios, repositorios y esquemas, añadir tipos y generar un contrato OpenAPI completo.

## 9. Qué falta antes de un lanzamiento público

1. **Infraestructura:** elegir servidor y dominio, configurar HTTPS, proxy, firewall y directorio persistente; probar reinicio de máquina y restauración de copias. PM2 por sí solo no hace esto.
2. **Correo:** configurar `RESEND_API_KEY` y `MAIL_FROM` con un remitente válido. El flujo de recuperación está escrito y probado con un transportador de prueba, pero no se enviaron correos reales. La verificación del correo al registrar o cambiar dirección todavía no está implementada; el producto no muestra esos correos como verificados.
3. **Google:** configurar la clave en el backend, confirmar Geocoding API, restricciones, cuota y facturación. Falta una comprobación real con tu cuenta.
4. **Fuente vehicular:** sustituir o formalizar la consulta externa existente; eliminar el dato fijo de identificación cuando se defina el contrato correcto. Separar ficha técnica, datos declarados y antecedentes verificados.
5. **Migración:** diseñar la importación de los datos del prototipo con selección y confirmación de la cuenta destinataria. No se realiza automáticamente.
6. **Sesión y trabajo sin conexión:** persistencia cifrada del token, renovación, base local por usuario, cola de operaciones y resolución de conflictos. Hoy los guardados remotos requieren conexión; no se finge que un cambio fallido ya fue sincronizado.
7. **Archivos y retención:** recodificación en servidor, cuotas, almacenamiento dedicado y limpieza de fotos huérfanas. Probar restauraciones con imágenes y registros juntos.
8. **Experiencia de cuenta:** confirmar contraseña durante registro, ver/ocultar contraseña, verificación de correo, acceso de varios dispositivos y una pantalla explícita para exportar/borrar datos. El endpoint de eliminación existe, pero no se añadió un botón destructivo de borrado al perfil.
9. **Calidad:** pruebas instrumentadas de cámara, galería, GPS, Maps, rotación de pantalla, errores de red y accesibilidad. Las pruebas unitarias del formato antiguo no sustituyen estas comprobaciones.
10. **Operación:** rotación de logs, alertas de errores, métricas, migraciones incrementales y política de actualizaciones. Documentar quién puede acceder a ubicaciones y durante cuánto tiempo.

La prioridad práctica es desplegar una instancia de pruebas con HTTPS y conectar un teléfono real. Después, probar cuentas separadas, recuperación, fotos y GPS con tus servicios configurados. Sólo entonces conviene ampliar roles, talleres, certificados o historial público: esas funciones dependen de que la identidad y la propiedad de los datos estén bien definidas.

## 10. Verificación y alcance de la entrega

El backend tiene pruebas de autenticación, duplicados, separación entre cuentas, validación de revisiones, idempotencia, cambio de contraseña, revocación, recuperación, límites de intentos y borrado de cuenta. Android conserva sus 13 pruebas de estado/persistencia del prototipo. La compilación y esas pruebas se ejecutaron localmente; los detalles de la comprobación final están en `VALIDACION.md`.

La entrega incluye código Android y backend, configuración PM2, ejemplo de variables, instrucciones, PNG y APK de depuración para emulador. No se desplegó en un servidor público ni se modificó la rama remota. No incluye claves, bases de datos de prueba ni `local.properties` de este equipo.
