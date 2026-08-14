# Política de seguridad

FrankUpdater descarga, inspecciona e instala paquetes Android. Cualquier fallo que permita confundir el paquete, la firma, la versión o el conjunto de splits se considera de alta prioridad.

## Reportar una vulnerabilidad

No publiques detalles explotables en un issue público. Utiliza el canal privado de reporte de vulnerabilidades de GitHub del repositorio cuando esté disponible. Incluye:

- versión o commit afectado;
- dispositivo y versión Android;
- pasos de reproducción;
- impacto esperado y observado;
- prueba de concepto mínima, si es segura de compartir.

No incluyas credenciales, cookies de sesión ni APKs que no puedas redistribuir.

## Propiedades que deben preservarse

- El `packageName` debe coincidir con el solicitado.
- La firma debe ser válida y compatible con la instalación existente.
- El artefacto debe cumplir restricciones de SDK, ABI, densidad, locale y features.
- Base, features y config splits deben formar un conjunto completo.
- La procedencia y hashes disponibles deben persistirse antes de instalar.
- WebView y descargas asistidas nunca pueden omitir el verificador común.
