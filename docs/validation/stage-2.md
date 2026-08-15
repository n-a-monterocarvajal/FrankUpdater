# Validación de la Etapa 2

- Fecha: 2026-08-15
- Estado: en validación; pendiente demostrar un commit exitoso de `PackageInstaller.Session` con el permiso del sistema autorizado explícitamente

## Automatización final

Desde la raíz del repositorio se ejecutó:

```powershell
.\gradlew.bat --no-daemon lintDebug test assembleDebug
```

Resultado: `BUILD SUCCESSFUL` en 122,5 s.

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
- Al solicitar reinstalación, Android abrió el ajuste por aplicación `Allow from this source`. No se habilitó porque es un cambio persistente de seguridad que requiere autorización explícita. Por ello la sesión final no se comprometió en esta pasada.

## Defectos funcionales encontrados y corregidos

1. `PackageManager.getPackageArchiveInfo` devolvía `null` para un split real aislado y el APKS se rechazaba. Se reprodujo con `split_config.en.apk`, se sustituyó la identidad por APIs públicas de apksig y se añadió lectura de `manifest@split`.
2. ARSCLib 1.4.0 y 1.3.8 resolvían el caso en API 36, pero fallaban reproduciblemente en API 23 con `NoClassDefFoundError` (74,9 s y 170,6 s respectivamente). Se retiró la dependencia y el lector AXML mínimo pasó en ambas APIs.
3. Las tres acciones de una tarjeta verificada desbordaban una fila compacta en API 23. Se cambiaron a una fila con ajuste de línea.

## Limitaciones del entorno y resultados no concluyentes

- Gradle y el dexado variaron entre 109,9 y 363,6 s. En la pasada más lenta se comprobó que el runner aún no estaba instalado, de modo que la demora ocurrió en el host y no dentro de FrankUpdater.
- `Pixel Launcher` y `System UI` mostraron avisos `isn't responding` durante arranques fríos. Tras elegir `Wait`, FrankUpdater continuó; los procesos señalados eran del sistema.
- Una preparación inicial del fixture SAF en API 23 eliminó accidentalmente el archivo físico al borrar su registro de Download Provider. Después de volver a copiarlo, la misma importación finalizó correctamente; se clasifica como error de preparación del entorno.
- La instalación exitosa base + splits permanece no concluyente hasta autorizar el permiso por aplicación en el emulador API 36. No se relajó ningún criterio funcional ni se intentó eludir la política del sistema.

La etapa no debe marcarse completada hasta repetir la acción `Reinstalar`, confirmar el diálogo del instalador, observar éxito y verificar la política de retención posterior.
