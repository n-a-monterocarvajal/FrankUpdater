# Validación de la Etapa 2

- Fecha: 2026-08-15
- Estado: completada y validada

## Automatización final

Desde la raíz del repositorio se ejecutó:

```powershell
.\gradlew.bat --no-daemon lintDebug test assembleDebug
```

Resultado: `BUILD SUCCESSFUL` en 122,5 s.

Después de la demostración de instalación se repitieron las mismas tareas con el JBR local de Android Studio, sin cambios de código: `BUILD SUCCESSFUL` en 54 s, con 61 tareas, 1 ejecutada y 60 `up-to-date`.

La cobertura automatizada incluye:

- extracción de APK, APKS, APKM moderno y XAPK, incluidos hashes, OBB, límites de tamaño y rechazo de traversal;
- lectura de package, version code y atributo AXML `split` sin usar APIs internas;
- verificación criptográfica de la firma de un APK real y continuidad de firmante instalado;
- rechazo de bases ausentes/duplicadas, package, versión o firmante discordantes y downgrade;
- serialización atómica de metadatos de biblioteca;
- escritura ordenada de base y dos splits, tamaño declarado, contenido completo y `fsync` por componente;
- importación de los cuatro formatos mediante URI `content://` real en pruebas instrumentadas.

## Emuladores

### Teléfono Android 6

- AVD: `Frank_API23_Phone`.
- API 23, 1080 × 1920, 480 dpi.
- Pasada final `connectedDebugAndroidTest`: 5 tests, 0 fallos, 1 omitido, 109,9 s.
- El test omitido busca un paquete del sistema con splits legibles; esta imagen no contiene uno. Los tests de APK real, los cuatro contenedores y la firma sí se ejecutaron.
- La importación SAF manual de un APK real terminó en `Paquete verificado` y creó una sesión que llegó al instalador del sistema. Android mostró `Install blocked` porque la opción global de orígenes desconocidos estaba desactivada.

### Tablet Android 16

- AVD: `Frank_API36_Tablet`.
- API 36, 2560 × 1600, 320 dpi.
- Pasada final `connectedDebugAndroidTest`: 5 tests, 0 fallos, 0 omitidos, 242,4 s; el test de manifiesto split real se ejecutó.
- Se verificaron Navigation Rail y distribución adaptable.
- Se construyó localmente, sin conservarlo en Git ni publicarlo, un APKS de 72,37 MB a partir del paquete split ya instalado `com.android.vending`: un APK base y seis splits.
- SAF importó el archivo y FrankUpdater mostró `Paquete verificado`, package `com.android.vending`, código `85262640`, `APKS · 7 APK · 69.0 MB`, hash y acción `Reinstall`.
- `Conservar` copió el original al espacio privado; la biblioteca mostró `Reinstalar`, `Compartir` y `Eliminar`.
- Ajustes mostró y persistió la política predeterminada `Preguntar` junto con `Eliminar automáticamente` y `Conservar siempre`.
- Tras recibir autorización explícita, `REQUEST_INSTALL_PACKAGES` se habilitó temporalmente sólo para FrankUpdater y se repitió la reinstalación del paquete retenido.
- El instalador del sistema mostró `Do you want to update this app?` para Google Play Store. Tras confirmar `Update`, FrankUpdater recibió el resultado y mostró `Aplicación instalada correctamente.`
- `dumpsys package` confirmó el código `85262640` y `pm path` enumeró exactamente siete rutas: `base.apk` y los seis splits esperados.
- La política `Preguntar` mantuvo el original como `Conservado`; la biblioteca continuó mostrando `Reinstalar`, `Compartir` y `Eliminar` después del éxito.
- El permiso temporal se devolvió a `REQUEST_INSTALL_PACKAGES: default` inmediatamente después de comprobar el resultado.

## Defectos funcionales encontrados y corregidos

1. `PackageManager.getPackageArchiveInfo` devolvía `null` para un split real aislado y el APKS se rechazaba. Se reprodujo con `split_config.en.apk`, se sustituyó la identidad por APIs públicas de apksig y se añadió lectura de `manifest@split`.
2. ARSCLib 1.4.0 y 1.3.8 resolvían el caso en API 36, pero fallaban reproduciblemente en API 23 con `NoClassDefFoundError` (74,9 s y 170,6 s respectivamente). Se retiró la dependencia y el lector AXML mínimo pasó en ambas APIs.
3. Las tres acciones de una tarjeta verificada desbordaban una fila compacta en API 23. Se cambiaron a una fila con ajuste de línea.

## Limitaciones del entorno y resultados no concluyentes

- Gradle y el dexado variaron entre 109,9 y 363,6 s. En la pasada más lenta se comprobó que el runner aún no estaba instalado, de modo que la demora ocurrió en el host y no dentro de FrankUpdater.
- La sesión de cierre no heredó `JAVA_HOME` y el wrapper intentó resolver inicialmente la caché como `C:\.gradle`; esos intentos terminaron antes de ejecutar tareas. Al invocar el JBR instalado y la caché local correctos, la misma validación finalizó correctamente en 54 s. Se clasifica como configuración del entorno.
- `Pixel Launcher` y `System UI` mostraron avisos `isn't responding` durante arranques fríos. Tras elegir `Wait`, FrankUpdater continuó; los procesos señalados eran del sistema.
- Una preparación inicial del fixture SAF en API 23 eliminó accidentalmente el archivo físico al borrar su registro de Download Provider. Después de volver a copiarlo, la misma importación finalizó correctamente; se clasifica como error de preparación del entorno.
- El bloqueo previo de la instalación fue resuelto mediante autorización explícita y no se clasifica como defecto. La pasada autorizada terminó correctamente sin eludir la confirmación del instalador y con el permiso restablecido después.

Con esta demostración quedan satisfechos los criterios de cierre de la Etapa 2: pipeline local para los cuatro formatos, verificación integral, sesión única base + splits, biblioteca y retención, automatización final y validación en API 23 y API 36 adaptable.
