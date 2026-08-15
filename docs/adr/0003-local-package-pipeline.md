# ADR 0003: Pipeline local de paquetes

- Estado: aceptado
- Fecha: 2026-08-14

## Contexto

FrankUpdater debe importar paquetes no confiables mediante SAF, leer APK, APKS, APKM y XAPK, verificarlos y entregar exactamente el conjunto validado a `PackageInstaller.Session`. El archivo puede provenir de una fuente manual y debe recibir las mismas comprobaciones que una futura descarga automática.

## Decisión

- Copiar primero el documento SAF a almacenamiento privado y no instalar directamente desde una URI revocable.
- Reconocer el formato por extensión y validar su estructura. APKS, XAPK y los APKM modernos sin DRM se tratan como contenedores ZIP; los APKM históricos cifrados se detectan y se rechazan explícitamente hasta disponer del adaptador externo UnApkm.
- Rechazar rutas peligrosas, contenedores sin APK, duplicados estructurales y archivos que excedan límites definidos. Calcular SHA-256 del contenedor y de cada APK mientras se lee el contenido real.
- Usar las APIs públicas de `apksig-android` para package y versión, una lectura AXML acotada al atributo raíz `split`, y `PackageManager` para enriquecer los metadatos cuando Android pueda leer el APK aislado. No se usan clases internas de apksig. Todos los APK deben compartir package, versión y firmante; una actualización debe continuar el historial de firma instalado.
- Crear una sola sesión de instalación, declarar el tamaño total y escribir base más splits con `fsync` antes del commit. El resultado se recibe mediante un broadcast explícito interno.
- Conservar los originales en almacenamiento propio de la aplicación. La preferencia inicial de retención es preguntar; eliminar, preguntar y conservar siempre son opciones persistentes.
- Registrar origen SAF, nombre, formato, package, versión, hash, firmantes, fecha y método de importación. Los temporales extraídos nunca forman parte de la biblioteca y se eliminan al terminar.

## Consecuencias

El pipeline no depende de acceso a almacenamiento compartido ni de red. XAPK puede registrar archivos OBB, pero su despliegue queda fuera de esta etapa porque requiere una política de almacenamiento adicional; nunca se ignoran silenciosamente. La compatibilidad con APKM cifrado antiguo queda aislada y no reduce la validación de los formatos actuales.
