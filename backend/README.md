# AutoHistoria API

Node **24.14.1** probado. JavaScript ESM, sin paquetes npm de ejecución. El módulo nativo SQLite de esta versión emite aviso experimental. Configuración para **un único proceso**.

## Arranque

Desde `backend/`:

```sh
cp .env.example .env
npm start
```

En PowerShell usa `Copy-Item .env.example .env`. El servidor escucha por defecto en `127.0.0.1:3000`; crea `data/autohistoria.sqlite`. No se incluye una base de datos en el ZIP. `npm test` ejecuta las pruebas HTTP.

Configura `GOOGLE_GEOCODING_API_KEY` en `.env` para obtener direcciones. Recuperación por correo: configura `RESEND_API_KEY` y `MAIL_FROM` con remitente verificado en tu proveedor. Sin esas variables, recuperación devuelve 503 con una explicación; no se simula un correo enviado. No publiques `.env`.

## PM2 en un servidor preparado

Instala PM2 según su documentación y, desde `backend/`, ejecuta:

```sh
pm2 start ecosystem.config.cjs --env production
pm2 status
pm2 logs autohistoria-api
pm2 save
```

En Linux, `pm2 startup` muestra el comando necesario para el servicio de arranque. Ejecutarlo requiere ajustar el usuario real del servidor. Mantén una instancia, `fork`, con almacenamiento persistente. Usa un proxy HTTPS frente al puerto local. Configura límite de cuerpo de al menos 4,5 MB, timeouts y limitación de solicitudes en el proxy. No publiques el puerto directamente como sustituto de HTTPS.

Antes de actualizar: detén escrituras, realiza una copia coherente de SQLite con su API de backup o con la app detenida, guarda fuera del servidor y prueba restauración. Copiar sólo el `.sqlite` mientras hay escritura WAL puede producir una copia incompleta. La versión inicial crea el esquema al arrancar; futuras modificaciones necesitan migraciones explícitas.

## Contrato HTTP

Los cuerpos de escritura son JSON. Las rutas privadas reciben `Authorization: Bearer <token>`.

| Método | Ruta | Uso |
|---|---|---|
| GET | `/health` | Salud y acceso a la base |
| POST | `/v1/auth/register` | `{name,email,password}` |
| POST | `/v1/auth/login` | `{email,password}` → `{token,expiresAt,user}` |
| POST | `/v1/auth/forgot-password` | `{email}`; respuesta neutra |
| POST | `/v1/auth/reset-password` | `{token,password}`; código de un uso |
| POST | `/v1/auth/logout` | `{}`; revoca sesión actual |
| POST | `/v1/auth/logout-all` | `{}`; revoca todas |
| GET | `/v1/me` | Perfil de la sesión |
| PATCH | `/v1/me` | `name,lastName,email,phone,city,bio,avatar`; `currentPassword` para cambiar correo |
| POST | `/v1/me/password` | `{currentPassword,newPassword}`; revoca sesiones |
| DELETE | `/v1/me` | `{currentPassword}`; elimina cuenta y datos relacionados |
| GET | `/v1/vehicles?q=&offset=0&limit=50` | Listado privado, máximo 100 por página |
| POST | `/v1/vehicles` | `{plate,vin,report?}` |
| GET | `/v1/vehicles/:id` | Expediente privado |
| PUT | `/v1/vehicles/:id/report` | `{json,time,manual}` |
| POST | `/v1/vehicles/:id/inspections` | Revisión con GPS/checklist/fotos |
| POST | `/v1/geocode` | `{lat,lon}` → `{address}` |

Una inspección utiliza este cuerpo; las fotos son data URLs JPEG/PNG, hasta seis de 400.000 bytes cada una. Genera un UUID una vez por borrador y reutilízalo al reintentar.

```json
{
  "id": "35899784-f61b-4fa8-b75b-3cf661f14bc3",
  "place": "Taller Central", "address": "Calle 10", "notes": "Revisión general",
  "kilometers": 42000, "latitude": 4.60971, "longitude": -74.08175,
  "accuracy": 6.5, "altitude": 2600, "capturedAt": 1795118400000,
  "googleAddress": false, "photos": [],
  "checklist": { "tires": "GOOD", "brakes": "REVIEW", "lights": "GOOD", "body": "GOOD" }
}
```

`capturedAt` debe ser la hora real de captura en milisegundos; el ejemplo es ilustrativo. El servidor asigna la fecha del registro. La marca `googleAddress` es declarada por el cliente y no una certificación. La API rechaza kilometraje inferior al máximo registrado.

Errores: `{ "error": { "code": 422, "message": "...", "requestId": "..." } }`. Códigos principales: 400 JSON inválido, 401 sesión/credenciales, 404 recurso ajeno o inexistente, 409 duplicado, 413 tamaño, 415 tipo de contenido, 422 validación, 429 límite, 503 configuración externa ausente.

Los endpoints de listado incluyen fotos e inspecciones completas: adecuados para probar y para garajes pequeños, pendientes de paginación de historial y almacenamiento de objetos para crecer. No hay verificación de correo, roles de taller, certificados, sincronización sin conexión ni JWT/refresh tokens.

## Recuperación simulada local
DEMO_PASSWORD_RESET=true permite cualquier código no vacío después de solicitar recuperación para un correo existente. No usa Resend. Sólo funciona desde localhost y se rechaza en producción. Usa únicamente cuentas de prueba. Una solicitud pendiente por dirección local; expira en 15 minutos. Desactiva la variable para utilizar recuperación real.

