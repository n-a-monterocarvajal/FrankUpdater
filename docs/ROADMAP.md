# Roadmap de implementación

Cada etapa debe terminar con tests y una demostración verificable antes de abrir la siguiente.

Excepción autorizada el 6 de septiembre de 2026: continuar las etapas siguientes
mientras la validación real de Play permanece bloqueada por HTTP 403. Esa validación
sigue pendiente; las pruebas locales de los proveedores no la sustituyen.

## 0. Fundación

- Proyecto Kotlin/Compose compilable, `minSdk 23`.
- Material 3 Adaptive y navegación compacta/amplia.
- Licencia, especificación, ADRs y `UPSTREAMS.yml`.
- CI de compilación, lint y tests.

## 1. Inventario y compatibilidad local

- `InstalledAppRepository` mediante `PackageManager`.
- `GenericDeviceProfile` y `PlayDeviceProfile` separados.
- Adaptación del checker de F-Droid.
- Port del targeting esencial de bundletool con tests de paridad.

## 2. Pipeline local seguro

- Importación por Storage Access Framework.
- Lectura de APK/APKS/APKM/XAPK.
- Verificación de package, versión, integridad y firma.
- Instalación de base y splits mediante `PackageInstaller.Session`.
- Biblioteca y política de retención.

## 3. Google Play

- Spike acotado de GPlayApi y decisión go/no-go.
- Autenticación, búsqueda, detalles y versión actual.
- Descarga por `packageName + versionCode`.
- URLs expirables, resume y verificación de archivos.

## 4. Última versión compatible

- `VersionCatalog` multifuente.
- Selección descendente por `versionCode`.
- Diferencia visible entre última publicada y última compatible.
- Recuperación histórica desde Play cuando el `versionCode` sea conocido.

## 5. APKMirror

- Metadata, historial y variantes.
- Descarga directa desacoplada.
- APK/APKM y fallback WebView → navegador/SAF.

## 6. APKPure

- Historial, APK/XAPK y tercer fallback.
- Fixtures y tests de regresión independientes del servicio vivo.

## 7. Instalación avanzada y actualizaciones periódicas

- Router Sistema/Shizuku/Root/Legacy.
- WorkManager, notificaciones y actualizaciones por lotes.

## 8. Mantenimiento de upstreams

- Watcher semanal por commits, paths y símbolos.
- Informe de impacto; nunca auto-merge para código portado.
- Dashboard de `last_seen`, `last_reviewed` y `last_integrated`.
