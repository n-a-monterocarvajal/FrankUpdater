# Fuentes upstream de la Etapa 1

La Etapa 1 fija las siguientes revisiones antes de adaptar o portar código:

| Componente local | Upstream y revisión | Símbolos estudiados | Integración prevista |
|---|---|---|---|
| Inventario instalado | `rumboalla/apkupdater@69b6fcdf52a7735ae17101efe1a0cd26222fb276` | `AppsRepository`, `PackageInfo.toAppInstalled` | Adaptación al modelo propio y a APIs 23–37 |
| Perfil genérico | `AuroraOSS/AuroraStore@f1bb85ff9dcbcc5cae07779d4e13f77b6b7f245b` | `NativeDeviceInfoProvider` | Adaptación sin propiedades ni dependencias de Google Play |
| Compatibilidad base | `f-droid/fdroidclient@707b8ece6e5eece0a27b80855172e12e624f9c71` | `CompatibilityCheckerImpl`, `CompatibilityCheckerUtils.minInstallableTargetSdk` | Adaptación al candidato normalizado de FrankUpdater |
| Targeting | `google/bundletool@586a43a450712a1067f3d92cf7574dee68226302` | `AbiMatcher`, `MultiAbiMatcher`, `SdkVersionMatcher`, `ScreenDensityMatcher`, `ScreenDensitySelector` | Port mínimo Kotlin sin protobuf ni Guava |

## Licencias verificadas

- APKUpdater y F-Droid client incluyen GPL versión 3 en la raíz de las revisiones fijadas.
- Los archivos de bundletool estudiados conservan encabezados Apache-2.0 de Android Open Source Project.
- `NativeDeviceInfoProvider.kt` conserva un aviso que permite GPL-2.0-or-later. Esa licencia de archivo, más específica que la licencia GPLv3 actual del repositorio Aurora Store, es la registrada para la adaptación.

Los ports y adaptaciones locales conservarán encabezados SPDX y comentarios de procedencia. Las divergencias concretas se documentarán al integrar cada componente.
