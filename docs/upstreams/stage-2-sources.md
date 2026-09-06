# Fuentes upstream de la Etapa 2

La Etapa 2 fija las siguientes revisiones antes de adaptar código o incorporar dependencias:

| Componente local | Upstream y revisión | Símbolos estudiados | Integración |
|---|---|---|---|
| Lectura y clasificación de archivos | `MuntashirAkon/AppManager@fc1e70074e8cf75c0619e526e688c16ad2e1e862` | `ApkFile`, `SplitApkChooser` | Adaptación al extractor seguro y modelos propios |
| Firma criptográfica | `MuntashirAkon/apksig-android@1bd3a0c000c56e752b41d6f65c3a3d3d8ad7f049` | API pública `ApkVerifier` y `ApkUtils` | Dependencia `4.4.0` |
| Sesión de instalación | `AuroraOSS/AuroraStore@f1bb85ff9dcbcc5cae07779d4e13f77b6b7f245b` | `SessionInstaller` | Adaptación a una sola sesión base + splits |
| APKM cifrado histórico | `MuntashirAkon/unapkm-android@0cc2ced9ae0c227aa86c18a85e984f879247922a` | `UnApkm` | Referencia; no integrada como dependencia obligatoria |

## Licencias y límites

- App Manager es GPL-3.0-or-later y la clasificación adaptada conserva trazabilidad y encabezados SPDX.
- El fork Android de apksig y UnApkm son Apache-2.0.
- El código de sesión estudiado en Aurora Store conserva GPL-2.0-or-later.
- FrankUpdater acepta APKM modernos sin DRM como ZIP. Los APKM históricos cifrados se detectan y rechazan explícitamente; no se finge que sean archivos corruptos ni se obliga a instalar un servicio externo.

## Divergencias deliberadas

- El extractor impone límites de entradas, tamaño y rutas antes de materializar contenido privado, y calcula SHA-256 del archivo fuente y de cada componente.
- La clasificación base/split no depende del nombre del archivo. `PackageManager` no puede leer de forma aislada ciertos splits reales; por eso package y versión se extraen mediante la API pública de apksig y el atributo AXML `manifest@split` se decodifica con un lector mínimo, acotado y con límites verificados.
- Se evaluaron ARSCLib 1.4.0 y 1.3.8 para esa única lectura, pero ambas produjeron `NoClassDefFoundError` reproducible en API 23. No quedaron integradas ni registradas como upstream activo.
- La biblioteca conserva el contenedor original verificado. Los APK extraídos son temporales de instalación y no se presentan como archivos retenidos independientes.
