# Validación de la Etapa 1

- Fecha: 2026-08-14
- Estado: completada

## Automatización

Desde la raíz del repositorio se ejecutó:

```powershell
.\gradlew.bat --no-daemon lintDebug test assembleDebug
```

Resultado: `BUILD SUCCESSFUL`. Android lint conserva advertencias informativas de versiones disponibles, sin errores. La supresión de `QueryAllPackagesPermission` está limitada a la declaración justificada por ADR 0002.

Los tests cubren:

- SDK mínimo, máximo y target SDK mínimo instalable;
- APK universal y compatibilidad de código nativo;
- preferencia y fallback ABI, incluido multi-ABI;
- selección de densidad asimétrica y `anydpi`;
- targeting SDK y dispositivos pre-release;
- features requeridas y digest SHA-256;
- representación Compose del perfil, recuentos y estado de aplicaciones.

## Emuladores

### Teléfono Android 6

- AVD: `Frank_API23_Phone`.
- API 23, 1080 × 1920, 480 dpi.
- `connectedDebugAndroidTest`: 1 test, correcto.
- Inventario real mostrado: 81 paquetes, 8 de usuario y 73 de sistema.
- Se verificó navegación inferior compacta, perfil API/ABI/DPI, tarjetas de aplicaciones y etiquetas de sistema.

### Tablet Android 16

- AVD: `Frank_API36_Tablet`.
- API 36, 2560 × 1600, 320 dpi.
- La primera invocación de Gradle no adjuntó el runner y terminó con 0 tests; los mismos APKs se instalaron y el test se repitió directamente con `am instrument`, con resultado `OK (1 test)`.
- Inventario real mostrado: 251 paquetes, 2 de usuario y 249 de sistema.
- Se verificó Navigation Rail, perfil API/ABI/DPI y cuadrícula adaptativa de tres columnas.

La estación de validación tiene recursos limitados y ejecutó dos AVDs junto con Gradle. Los tiempos observados no se consideran una medición de rendimiento del producto. La comprobación relevante para esta etapa es que la enumeración se ejecuta fuera del hilo principal, la UI mantiene un estado de carga responsivo y el inventario termina presentándose.
