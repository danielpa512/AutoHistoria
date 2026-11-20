# Validación

Revisión final: 20 de noviembre de 2026.

- Node 24.14.1: **10 pruebas HTTP aprobadas**, incluyendo autorización entre cuentas, recuperación, cambio de correo, persistencia de fotos y revisiones tras reiniciar el servidor y ausencia de clave Google.
- Android: APK debug y 13 pruebas unitarias existentes de estado, persistencia, checklist, fotos y datos manuales.
- Emulador: instalación y arranque del APK, inicio de sesión contra API local, registro de vehículo desde la interfaz y lectura de coordenadas GPS simuladas.
- Logo: observado estático en cabecera y girando durante la captura GPS; captura adjunta como referencia.
- Sin clave Google: observado mensaje recuperable, conservación de coordenadas y posibilidad de introducir dirección manualmente.

Pendiente de comprobación en dispositivo: recorrido completo de selección de avatar, cámara, carga y descarga de seis fotos, guardado de revisión desde la interfaz, apertura efectiva de Maps y accesibilidad. La API sí prueba persistencia de imágenes y revisiones con reinicio. Esta cobertura no equivale a probar todos los flujos de interfaz.

No se enviaron correos reales ni se probó Geocoding con tu clave de Google. No se desplegó públicamente ni se modificó GitHub. La APK es debug, apunta a http://10.0.2.2:3000 y necesita el backend en el equipo del emulador.
