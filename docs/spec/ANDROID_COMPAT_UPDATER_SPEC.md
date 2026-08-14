# Especificación técnica — Cliente Android multifuente para búsqueda, actualización e instalación de APK compatibles

**Estado:** borrador de arquitectura para primera implementación asistida por IA  
**Fecha de consolidación:** 13 de agosto de 2026  
**Objetivo del documento:** servir como especificación autosuficiente para que un agente de IA pueda crear una primera versión funcional del proyecto, manteniendo trazabilidad estricta de código reutilizado y evitando reinventar funciones ya resueltas por proyectos FOSS maduros.

---

## 1. Visión del producto

La aplicación será un **cliente Android de búsqueda, actualización, recuperación e instalación de aplicaciones**, con especial énfasis en dispositivos antiguos.

Su característica distintiva será determinar no solo la versión más nueva existente de una aplicación, sino la **última versión realmente compatible con el dispositivo concreto**, considerando como mínimo:

- versión de Android / API (`SDK_INT`);
- `minSdk` y, cuando exista, `maxSdk`;
- `targetSdk` cuando Android imponga restricciones de instalación;
- ABI / arquitectura (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, etc.);
- orden de preferencia de ABI del dispositivo;
- densidad de pantalla;
- idioma / locale cuando existan splits específicos;
- features de hardware requeridas;
- paquetes monolíticos versus split APKs / bundles;
- dependencias entre `base.apk`, feature splits y config splits;
- identidad de paquete;
- `versionCode`;
- firma y, cuando corresponda, rotación de certificados.

La aplicación tendrá tres fuentes principales de binarios, por orden de preferencia:

1. **Google Play** — máxima disponibilidad y preferencia para el binario cuando puede servir la versión requerida.
2. **APKMirror** — gran catálogo histórico, menor dependencia de geolocalización y disponibilidad regional.
3. **APKPure** — tercer proveedor y fallback adicional.

Las tres fuentes pueden utilizarse también como **fuentes de metadatos**, aunque el binario finalmente se descargue desde otra.

Ejemplo: APKMirror puede revelar que la última versión de Zoom compatible con Android 8 tiene `versionCode = X`; la aplicación debe intentar primero obtener ese `versionCode` desde Google Play. Si Google ya no lo sirve, debe recurrir a APKMirror y luego a APKPure.

---

## 2. Decisiones de arquitectura ya adoptadas

### 2.1. Plataforma mínima

**`minSdk = 23` — Android 6.0 Marshmallow.**

Razones:

- Android 6 sigue siendo un sistema muy antiguo, por lo que el producto mantiene su vocación de rescatar hardware legado.
- Aurora Store, Droid-ify y APKUpdater 3.x convergen actualmente en `minSdk 23`.
- Las ramas actuales de AndroidX están elevando progresivamente su mínimo desde API 21 a API 23.
- Mantener API 21–22 obliga a congelar versiones de Activity, Room, WebKit y otras bibliotecas.
- WebKit moderno es especialmente valioso para el fallback de descarga asistida.
- Shizuku requiere Android 6 o posterior.
- La diferencia histórica entre Android 5 (2014) y Android 6 (2015) no justifica un impuesto permanente de mantenimiento.

No debe diseñarse inicialmente una variante Android 5. Un eventual `legacy flavor` podría estudiarse después sin condicionar el producto principal.

### 2.2. Stack de UI

**Kotlin + Jetpack Compose + Material 3 + Material 3 Adaptive.**

La UI debe ser contemporánea, pero no debe depender de gestos o patrones exclusivos de Android reciente.

Referencia visual/arquitectónica principal: **Aurora Store actual**.

La aplicación debe adaptarse por **window size classes**, no por detección rígida de “tablet”:

- Compact: navegación inferior / una pantalla o pane a la vez.
- Medium: Navigation Rail cuando convenga; posibilidad de supporting pane.
- Expanded y superiores: navegación lateral + list/detail simultáneo.

Debe aprovechar patrones tipo:

- `ListDetailPaneScaffold`;
- `SupportingPaneScaffold`;
- Navigation Rail / Navigation Bar según ancho;
- layouts de uno, dos o tres paneles según ventana.

### 2.3. Licencia del proyecto

Si se incorpora/adapta código de APKUpdater, Droid-ify, F-Droid, Aurora Store u otros componentes GPL, el proyecto debe asumirse desde el principio como **GPL-3.0-compatible**, previsiblemente **GPL-3.0-or-later**, conservando encabezados, atribuciones y avisos requeridos.

**Regla:** verificar la licencia del repositorio y del archivo concreto antes de copiar o portar código. No asumir que la licencia global cubre todos los subcomponentes o dependencias.

---

## 3. Principio rector: no construir un “fork Frankenstein” opaco

La aplicación será nueva y modular. No debe partir de un único repositorio e ir acumulando parches.

Cada función reutilizada debe clasificarse como una de estas:

1. **Dependencia real:** se consume como biblioteca publicada. Debe actualizarse con Renovate/Dependabot o mecanismo equivalente.
2. **Código adaptado:** código Kotlin/Java incorporado y modificado localmente.
3. **Código portado:** algoritmo reimplementado en otro lenguaje o modelo interno, por ejemplo TypeScript → Kotlin.
4. **Referencia:** no se copia código; se estudia para decisiones de diseño, tests y edge cases.

La procedencia debe trazarse hasta nivel de archivo, clase o símbolo cuando sea posible.

---

## 4. Arquitectura propuesta

```text
app/
│
├── ui/
│   ├── navigation/
│   ├── updates/
│   ├── search/
│   ├── appdetails/
│   ├── library/
│   ├── settings/
│   └── assistedweb/
│
├── inventory/
│   └── InstalledAppRepository
│
├── device/
│   ├── DeviceProfile
│   └── PlayDeviceProfile
│
├── compatibility/
│   ├── BaseCompatibilityChecker
│   ├── SplitTargetingMatcher
│   ├── UpdateCandidateSelector
│   └── CompatibilityEngine
│
├── sources/
│   ├── common/
│   │   ├── StoreProvider
│   │   ├── VersionCatalog
│   │   ├── ArtifactCandidate
│   │   └── SourcePreferenceResolver
│   ├── play/
│   ├── apkmirror/
│   └── apkpure/
│
├── download/
│   ├── DownloadCoordinator
│   ├── DirectDownloadBackend
│   ├── AssistedWebDownloadBackend
│   └── DownloadedArtifact
│
├── archive/
│   ├── PackageArchiveReader
│   ├── SplitClassifier
│   └── ArchiveDependencyResolver
│
├── verification/
│   ├── PackageVerifier
│   ├── SignatureVerifier
│   └── IntegrityVerifier
│
├── installer/
│   ├── InstallerRouter
│   ├── LegacyInstaller
│   ├── SessionInstaller
│   ├── ShizukuInstaller
│   └── RootInstaller
│
└── storage/
    ├── DownloadLibrary
    └── PackageRetentionPolicy
```

Separado de la aplicación:

```text
upstream/
├── UPSTREAMS.yml
├── snapshots/
└── patches/

docs/upstreams/
.github/workflows/
├── upstream-watch.yml
├── dependency-update.yml
└── upstream-review.yml
```

---

## 5. Modelo normalizado central

Las fuentes no deben decidir por sí mismas cuál es “el APK correcto”. Deben entregar candidatos a un modelo común.

Ejemplo conceptual:

```kotlin
data class ArtifactCandidate(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val source: Source,
    val minSdk: Int?,
    val maxSdk: Int?,
    val targetSdk: Int?,
    val abis: List<String>,
    val density: DensityTarget?,
    val locales: List<String>,
    val requiredFeatures: List<String>,
    val packageType: PackageType,
    val signerDigests: Set<String>,
    val artifactSet: List<RemoteArtifact>,
    val metadataUrl: String?,
    val downloadMode: DownloadMode
)
```

`DownloadMode` puede ser:

```text
DIRECT
RESOLVABLE_DIRECT
ASSISTED_WEB
EXTERNAL_BROWSER
UNAVAILABLE
```

---

## 6. Inventario de aplicaciones instaladas

### Fuente principal: APKUpdater 3.x

Repositorio:
- https://github.com/rumboalla/apkupdater

Licencia verificada del repo: GPLv3.

Tomar/adaptar:

- enumeración de paquetes instalados mediante `PackageManager`;
- obtención de `versionName` y `versionCode`;
- obtención de firmas con APIs antiguas y modernas;
- detección de system apps / disabled apps;
- installer source cuando esté disponible;
- estructura de comprobación periódica de actualizaciones;
- ideas de abstracción multifuente.

No tomar como autoridad final:

- heurísticas simples de arquitectura/DPI;
- selección de artefacto compatible cuando exista una implementación más robusta.

El `InstalledAppRepository` propio debe preservar como mínimo:

```text
packageName
versionName
versionCode
signingCertificateHistory
splitSourceDirs
installerSource
isSystemApp
isEnabled
```

---

## 7. Perfil del dispositivo

### Fuente principal: Aurora Store

Repositorio:
- https://github.com/AuroraOSS/AuroraStore

Referencia concreta:
- `NativeDeviceInfoProvider.kt`

Tomar/adaptar la recopilación de:

- `Build.VERSION.SDK_INT`;
- versión Android/codename;
- `Build.SUPPORTED_ABIS` en orden;
- densidad de pantalla;
- ancho/alto;
- locales;
- `PackageManager.systemAvailableFeatures`;
- shared libraries;
- OpenGL y extensiones;
- fabricante/modelo/fingerprint cuando Play lo requiera.

Dividir el modelo en:

```text
GenericDeviceProfile
    sdk
    codename
    supportedAbis[]
    densityDpi
    locales[]
    systemFeatures[]
    glVersion
    ...

PlayDeviceProfile
    GenericDeviceProfile +
    fingerprint/build props/GSF/Vending/etc.
```

El motor de compatibilidad nunca debe depender directamente de Play.

---

## 8. Compatibilidad básica de APK

### Fuente principal: F-Droid client

Repositorio:
- https://github.com/f-droid/fdroidclient

Licencia verificada del repo: GPLv3.

Referencia concreta:
- `libs/index/src/androidMain/kotlin/org/fdroid/CompatibilityChecker.kt`
- `CompatibilityCheckerImpl`

Este componente ya comprueba:

- `minSdkVersion`;
- `maxSdkVersion`;
- `targetSdkVersion` mínimo instalable en Android recientes;
- código nativo frente a `SUPPORTED_ABIS`;
- features de hardware requeridas.

Debe convertirse en la base de `BaseCompatibilityChecker`.

También debe mantenerse la lógica de `minInstallableTargetSdk`, vigilando cambios de AOSP.

---

## 9. Selección de la mejor actualización compatible

### Fuente principal: F-Droid client

Referencia concreta:
- `libs/index/src/androidMain/kotlin/org/fdroid/UpdateChecker.kt`

El `UpdateChecker` de F-Droid ya implementa el patrón correcto:

1. versiones ordenadas de mayor a menor `versionCode`;
2. descartar `versionCode <=` instalado;
3. descartar versiones incompatibles;
4. descartar canales no permitidos;
5. comprobar signer;
6. devolver el candidato válido con mayor `versionCode`.

Adaptar esta semántica al modelo `ArtifactCandidate`.

No reinventar este bucle salvo necesidad justificada.

---

## 10. Matching preciso de ABI, densidad, idioma y splits

### Fuente principal: Google bundletool

Repositorio:
- https://github.com/google/bundletool

Licencia: Apache-2.0 en los archivos estudiados.

Símbolos prioritarios a estudiar/portar:

- `ApkMatcher`
- `AbiMatcher`
- `MultiAbiMatcher`
- `SdkVersionMatcher`
- `ScreenDensityMatcher`
- `ScreenDensitySelector`
- `LanguageMatcher`
- `VariantMatcher`

Motivo:

`bundletool` implementa la semántica oficial de targeting de Android App Bundles y APK Sets. Es una referencia muy superior a un match manual de strings.

### ABI

Debe respetarse el **orden de preferencia** de `Build.SUPPORTED_ABIS`.

No basta con:

```text
candidateAbi in supportedAbis
```

Deben conservarse las reglas de valores/alternativas de bundletool cuando sean aplicables.

### Densidad

No usar “DPI más cercano” por diferencia aritmética.

`ScreenDensitySelector` sigue la lógica del Android Framework, donde el escalado hacia arriba y hacia abajo no tiene el mismo coste.

### Idioma

Usar la semántica de `LanguageMatcher`, incluido fallback cuando las alternativas no cubren completamente los locales del dispositivo.

### Estrategia de implementación

No incorporar bundletool completo en la APK salvo que el peso/dependencias resulten aceptables.

Preferencia inicial:

- portar/adaptar el subconjunto de matching;
- portar también sus tests;
- mantener trazabilidad por símbolo en `UPSTREAMS.yml`.

---

## 11. Google Play

### Fuente primaria de implementación: GPlayApi

Proyecto:
- https://gitlab.com/AuroraOSS/gplayapi

Consumir como **dependencia**, no copiar su implementación si puede evitarse.

### Fuente primaria de integración: Aurora Store

Repositorio:
- https://github.com/AuroraOSS/AuroraStore

Piezas a estudiar:

- autenticación y gestión de sesiones;
- búsqueda y detalles de aplicaciones;
- `PurchaseHelper`;
- descarga por `packageName + versionCode`;
- device profile;
- `DownloadWorker`;
- gestión de `PlayFile`;
- shared libraries;
- OBB;
- verificación de hashes;
- URLs expiradas;
- reintentos/resume;
- firma/certificados de update;
- descarga manual de `versionCode` histórico.

### Versiones antiguas

Aurora permite solicitar una versión concreta por `versionCode`.

La versión actual también contiene un `VersionPicker`, pero el historial mostrado se alimenta de reportes de **Exodus Privacy**, no de un catálogo histórico completo de Google Play.

Esta idea debe generalizarse:

```text
VersionCatalog =
    Play actual
    + APKMirror history
    + APKPure history
    + Exodus (opcional)
```

Una vez hallado un `versionCode` compatible histórico:

```text
1. intentar Google Play;
2. si Play todavía sirve ese versionCode, usar Play;
3. si no, APKMirror;
4. si no, APKPure.
```

---

## 12. APKMirror

### 12.1. Descubrimiento de actualizaciones / metadata

Fuente principal de referencia: APKUpdater 3.x.

APKUpdater ya obtiene metadata como:

- `versionCode`;
- publicación;
- arquitectura;
- DPI;
- `minapi`;
- capacidades;
- signatures SHA-1/SHA-256;
- link.

También utiliza un mecanismo batch útil para consultar muchas aplicaciones instaladas.

**Advertencia:** no asumir que endpoints/credenciales privadas o específicas de APKUpdater pueden reutilizarse legítimamente. Implementar un backend desacoplado y disponer siempre de alternativa web/scraper.

### 12.2. Navegación por releases, variantes y URL final

Fuente principal: APKMD / APKMirror Downloader.

Repositorio:
- https://github.com/tanishqmanuja/apkmirror-downloader

Usar como referencia/port para:

- `getVersions`;
- `getVariants`;
- parsing de tabla de variantes;
- distinción APK/BUNDLE;
- arquitectura;
- Android mínimo;
- DPI;
- resolución de URL final de descarga.

No copiar literalmente su `getFilteredVariant()` como autoridad de compatibilidad: su filtrado es deliberadamente sencillo.

Pipeline correcto:

```text
APKMD scraper
    -> List<ArtifactCandidate>
    -> CompatibilityEngine propio
    -> candidato seleccionado
```

### 12.3. Riesgo de Cloudflare / HTML cambiante

El provider APKMirror debe estar completamente aislado del resto de la app.

```text
ApkMirrorProvider
├── MetadataBackend
├── WebParserBackend
├── DirectDownloadBackend
└── AssistedWebBackend
```

Si cambia el HTML o Cloudflare bloquea automatización, el resto del producto debe seguir operativo.

---

## 13. APKPure

Usar varias implementaciones abiertas como fuentes cruzadas.

### APKUpdater

Tomar como referencia para:

- búsqueda;
- latest/update checking;
- endpoints actuales;
- URL de asset;
- distinción APK/XAPK.

### Obtainium

Repositorio:
- https://github.com/ImranR98/Obtainium

Tomar como referencia para:

- historial mediante endpoints de versiones;
- agrupación por release;
- fallback a releases anteriores cuando la más reciente no es utilizable.

Esto se aproxima mucho al requisito “última versión compatible”.

### apkeep (EFF)

Repositorio:
- https://github.com/EFForg/apkeep

Usarlo principalmente como:

- segunda implementación independiente de APKPure;
- referencia para versiones concretas / arquitectura;
- fuente para tests de regresión.

### StoreKit

Repositorio:
- verificar la implementación seleccionada y su licencia antes de incorporar.

Usar como referencia para:

- abstracción de providers;
- fallback configurable;
- resolución de enlaces APKPure.

No convertir StoreKit en el cerebro de selección: el `CompatibilityEngine` debe seguir siendo propio y común a todas las fuentes.

---

## 14. Lectura y normalización de paquetes descargados

### Fuente principal: App Manager

Repositorio:
- https://github.com/MuntashirAkon/AppManager

Pieza principal:
- `app/.../apk/ApkFile.java`

App Manager ya soporta:

- `.apk`;
- `.apks`;
- `.apkm`;
- `.xapk`;
- `base.apk`;
- split APKs;
- OBB;
- `idsig`;
- feature splits;
- `configForSplit`;
- splits obligatorios;
- isolated splits;
- clasificación ABI / densidad / idioma.

Crear un `PackageArchiveReader` inspirado en este código.

### APKM

App Manager usa:
- `unapkm-android`

para convertir/abrir APKM cuando corresponde.

Usar esa biblioteca o una alternativa compatible antes de reimplementar la lógica.

### Selección final de splits

App Manager debe aportar:

- clasificación del split;
- relaciones entre base/feature/config;
- required/isolated;
- conservación de splits relevantes en updates.

La decisión final de **qué ABI/DPI/locale es mejor** debe delegarse al matcher derivado de bundletool.

---

## 15. Verificación de integridad, package y firma

### Autoridad recomendada: apksig-android

Proyecto:
- https://github.com/MuntashirAkon/apksig-android

No implementar manualmente los esquemas de firma Android.

Antes de instalar cualquier archivo descargado comprobar:

```text
packageName esperado
versionCode esperado
versionCode > instalado, salvo reinstalación explícita
target/min/max SDK
artefactos completos
integridad/hash cuando la fuente lo entregue
firma válida
firma compatible con la instalación existente
```

Considerar rotación de certificados y signing lineage en Android moderno.

Para una aplicación ya instalada, una fuente inferior puede ser preferible si es la única cuya firma puede actualizar la instalación existente.

Por tanto, el orden de decisión real es:

```text
1. package correcto
2. firma/update compatible
3. dispositivo compatible
4. versionCode máximo
5. fuente preferida: Play > APKMirror > APKPure
```

---

## 16. Descargas

### Fuente principal de arquitectura: Aurora Store `DownloadWorker`

Generalizar la lógica de Aurora desde `PlayFile` a un modelo neutral `DownloadArtifact`.

Funciones a conservar conceptualmente:

- WorkManager para operaciones largas;
- foreground progress cuando corresponda;
- resume de descargas parciales;
- retry con backoff;
- detectar cancelación del usuario versus interrupción del sistema;
- validar espacio antes de descargar;
- verificación de hash;
- evitar re-hashear archivos ya verificados;
- recuperación de URL expirada cuando el provider lo soporte;
- estado persistido de descarga;
- pipeline Downloading → Verifying → Installing.

### Carpeta

Los paquetes deben descargarse preferentemente a almacenamiento **propio de la app**, no a `Downloads` público.

Ejemplo conceptual:

```text
Android/data/<package>/files/packages/
```

La app debe poder:

- instalar;
- conservar;
- eliminar;
- reusar;
- compartir/exportar si el usuario lo solicita.

---

## 17. Fallback de descarga asistida

La aplicación nunca debe quedar inútil solo porque una fuente ya no permita automatizar la URL final.

Definir niveles:

| Nivel | Método | Intervención |
|---|---|---|
| L0 | API / delivery directo | ninguna |
| L1 | URL final resuelta + downloader propio | ninguna |
| L2 | WebView asistido + captura de descarga | mínima |
| L3 | Custom Tab / navegador + importar archivo por SAF | mayor |
| L4 | instrucciones / enlace como último recurso | manual |

### L2 — WebView asistido

Debe ser el fallback preferente para APKMirror/APKPure cuando falla el download automático.

Objetivo UX:

```text
“No podemos descargar automáticamente esta variante.
Ya hemos identificado la versión compatible.
Continúa en APKMirror para confirmar la descarga.”
```

Abrir directamente la URL más profunda conocida:

- ideal: página de variante;
- aceptable: release;
- último recurso: ficha de app.

El WebView debe:

- usar navegación restringida a dominios esperados;
- presentar claramente qué versión/ABI debe escoger el usuario;
- interceptar `DownloadListener`;
- capturar URL, User-Agent, Content-Disposition y MIME;
- reutilizar cookies de la sesión cuando corresponda;
- transferir el download al `DownloadCoordinator`;
- guardar el archivo en almacenamiento propio;
- volver automáticamente al pipeline normal de verificación/instalación.

### L3 — navegador real / Custom Tab

Usar cuando el WebView no funciona o la web exige un navegador completo.

El navegador controlará la descarga. Al volver a la app:

```text
[Seleccionar archivo descargado]
```

Usar Storage Access Framework para importar el APK/APKM/APKS/XAPK y volver al mismo pipeline.

---

## 18. Instalación

### Router de instaladores: Droid-ify

Repositorio:
- https://github.com/Droid-ify/client

Licencia verificada: GPLv3.

Tomar/adaptar:

- `InstallManager` / selección de modo;
- detección de disponibilidad;
- fallback entre instaladores;
- colas/estado;
- opción equivalente a `deleteApkOnInstall`.

Exponer al usuario:

```text
Automático
Sistema / Session
Shizuku
Root
Legacy (si corresponde)
```

### Session installer: Aurora Store

Preferir Aurora como referencia principal para Session Installer porque ya escribe múltiples archivos/splits dentro de una misma `PackageInstaller.Session`.

Tomar/adaptar:

- `SessionInstaller`;
- escritura de todos los splits;
- `setSize`;
- callbacks de progreso;
- flags diferenciados por versión Android;
- shared libraries;
- cola de sesiones.

### Shizuku

Tomar de Droid-ify la lógica base basada en:

```text
pm install-create
pm install-write
pm install-commit
```

Generalizarla para escribir múltiples APK/splits antes de `commit`.

### Root

Tomar la estrategia Droid-ify / libsu y generalizarla a split APKs.

### Legacy

Mantener como fallback de compatibilidad/UX cuando corresponda.

---

## 19. Política post-instalación

Tras instalación exitosa:

```text
Aplicación actualizada correctamente.
Paquete descargado: 124 MB

[Eliminar paquete]
[Conservar]
```

Preferencia global:

- eliminar automáticamente;
- preguntar;
- conservar siempre.

Valor inicial recomendado: **preguntar**.

La app debe incluir una **biblioteca local** de paquetes conservados:

```text
Zoom 6.4.2    124 MB   [Reinstalar] [Compartir] [Eliminar]
Firefox ...
```

Esto tiene especial valor en dispositivos antiguos, porque una versión compatible podría desaparecer de la fuente en el futuro.

---

## 20. Flujo A — detectar actualizaciones de aplicaciones instaladas

```text
PackageManager
    ↓
InstalledAppRepository
    ↓
packageName + versionCode + firmas
    ↓
consultar proveedores / catálogos
    ├── Google Play
    ├── APKMirror
    └── APKPure
    ↓
normalizar ArtifactCandidates
    ↓
CompatibilityEngine
    ↓
UpdateCandidateSelector
    ↓
SignatureVerifier
    ↓
SourcePreferenceResolver
    ↓
Play > APKMirror > APKPure (solo entre candidatos equivalentes y válidos)
    ↓
DownloadCoordinator
    ↓
PackageArchiveReader
    ↓
verificación final
    ↓
InstallerRouter
    ↓
post-instalación / retención del archivo
```

Debe soportar checks periódicos mediante WorkManager, inspirados en APKUpdater.

---

## 21. Flujo B — búsqueda de una app y “última compatible”

Ejemplo: Android 8, usuario busca `Zoom`.

```text
“Zoom”
  ↓
búsqueda primaria Google Play
  ↓
identificar packageName
  ↓
VersionCatalog combinado
  ├── Play current
  ├── APKMirror history
  ├── APKPure history
  └── Exodus opcional
  ↓
ordenar versiones por versionCode descendente
  ↓
CompatibilityEngine
  ↓
encontrar primera versión compatible
  ↓
resolver artefacto / variante ideal
  ↓
¿Google Play aún sirve ese versionCode?
  ├── Sí -> descargar Play
  └── No
       ↓
    APKMirror
       ↓
    ¿download automático?
       ├── Sí
       └── No -> WebView asistido
             ↓
          si falla -> navegador/SAF
       ↓
    APKPure como tercer fallback
```

UI recomendada:

```text
ZOOM WORKPLACE

Última publicada
6.x
Android 9+
No compatible

Última compatible con este dispositivo
6.y
Android 6+
arm64-v8a · nodpi

Fuentes
● Google Play      disponible / no disponible
○ APKMirror        disponible
○ APKPure          disponible

[Descargar e instalar]
```

Debe existir además:

- “mostrar todas las versiones compatibles”;
- posibilidad de seleccionar manualmente otra versión;
- información clara sobre por qué una versión fue descartada.

---

## 22. UI / UX adaptativa

### Navegación principal

Secciones iniciales:

1. **Actualizaciones**
2. **Buscar**
3. **Biblioteca**
4. **Ajustes**

Opcional posteriormente:
- Descargas activas/historial.

### Teléfono / Compact

- Navigation Bar inferior.
- Un pane por vez.
- Search → Results → Details.
- Update list → App detail.

### Tablet / Expanded

- Navigation Rail o drawer lateral.
- patrón list-detail.
- lista en panel medio y detalle a la derecha.
- mantener selección al cambiar tamaño/orientación.

### Principios visuales

- Material 3 contemporáneo.
- No imitar visualmente Play Store de forma literal.
- Mostrar fuente con icono/etiqueta discreta.
- Compatibilidad debe ser legible de un vistazo.
- Estados de descarga e instalación explícitos.
- No esconder fallbacks: explicar cuándo la app necesita intervención mínima.
- No presentar Cloudflare/WebView como “error técnico” si el usuario puede continuar normalmente.

### Referencias principales

- Aurora Store: arquitectura Compose/Material3/Adaptive y flujo de tienda.
- Droid-ify: listas de aplicaciones, estados, updater FOSS.
- APKUpdater: UX de update checker multifuente.

---

## 23. Proveniencia y watcher de upstreams

Este subsistema es obligatorio desde el inicio.

### Archivo `UPSTREAMS.yml`

Ejemplo:

```yaml
components:

  inventory.installed-apps:
    upstream: rumboalla/apkupdater
    ref: 3.x
    symbols:
      - AppsRepository
    integration: adapted
    license: GPL-3.0
    last_seen_commit: null
    last_reviewed_commit: null
    last_integrated_commit: null

  compatibility.base:
    upstream: f-droid/fdroidclient
    symbols:
      - CompatibilityCheckerImpl
      - CompatibilityCheckerUtils.minInstallableTargetSdk
    integration: adapted
    license: GPL-3.0

  compatibility.abi:
    upstream: google/bundletool
    symbols:
      - AbiMatcher
      - MultiAbiMatcher
    integration: ported
    license: Apache-2.0

  compatibility.density:
    upstream: google/bundletool
    symbols:
      - ScreenDensityMatcher
      - ScreenDensitySelector
    integration: ported
    license: Apache-2.0

  compatibility.language:
    upstream: google/bundletool
    symbols:
      - LanguageMatcher
    integration: ported
    license: Apache-2.0

  update.selection:
    upstream: f-droid/fdroidclient
    symbols:
      - UpdateChecker
    integration: adapted
    license: GPL-3.0

  provider.play:
    upstream: AuroraOSS/gplayapi
    integration: dependency

  provider.play.integration:
    upstream: AuroraOSS/AuroraStore
    symbols:
      - NativeDeviceInfoProvider
      - DownloadWorker
      - ManualDownloadScreen
    integration: adapted

  provider.apkmirror.metadata:
    upstream: rumboalla/apkupdater
    integration: adapted

  provider.apkmirror.scraper:
    upstream: tanishqmanuja/apkmirror-downloader
    symbols:
      - getVersions
      - getVariants
      - getFinalDownloadUrl
    integration: ported

  provider.apkpure.history:
    upstream: ImranR98/Obtainium
    integration: ported

  archive.reader:
    upstream: MuntashirAkon/AppManager
    symbols:
      - ApkFile
      - SplitApkChooser
    integration: adapted

  installer.router:
    upstream: Droid-ify/client
    integration: adapted

  installer.session:
    upstream: AuroraOSS/AuroraStore
    symbols:
      - SessionInstaller
    integration: adapted
```

### Estados

Guardar por upstream:

- `last_seen_commit`: último commit que el watcher conoce.
- `last_reviewed_commit`: último commit cuyo impacto fue evaluado.
- `last_integrated_commit`: último commit incorporado total o parcialmente.

No deben confundirse.

### GitHub Action `upstream-watch`

Periodicidad sugerida inicial: semanal.

Por cada upstream:

```text
obtener HEAD/tag actual
    ↓
comparar con last_seen
    ↓
¿cambió?
    ↓
¿afecta path/símbolo relevante?
    ├── No -> actualizar last_seen
    └── Sí -> generar informe/Issue o draft PR
```

Para código portado, nunca auto-merge.

### Análisis semántico asistido por IA

Cuando un símbolo upstream cambie, el agente debe recibir:

- versión upstream anterior;
- versión upstream nueva;
- implementación local;
- documentación de adaptaciones;
- tests de paridad;
- tests de producto.

Resultado esperado:

```text
- cambio relevante / irrelevante
- motivo
- comportamiento local afectado
- parche sugerido
- tests que deberían añadirse o modificarse
```

---

## 24. Tests requeridos desde el MVP

### 24.1. Compatibilidad

Fixtures de dispositivos:

```text
API 23 armeabi-v7a mdpi
API 26 arm64+armv7 420dpi es-CL
API 28 x86_64
API actual arm64 xxhdpi multi-locale
```

Casos:

- minSdk incompatible;
- maxSdk incompatible;
- targetSdk demasiado antiguo en Android moderno;
- ABI preferida versus secundaria;
- universal APK;
- multi-ABI;
- densidad no exacta;
- nodpi;
- locale split;
- required features;
- feature splits;
- firma incompatible;
- key rotation.

### 24.2. APKMirror

Guardar HTML fixtures sanitizadas de:

- página de versiones;
- tabla de variantes;
- APK simple;
- Bundle/APKM;
- redirect a variante única;
- página modificada/fallida;
- Cloudflare/JS-required response.

### 24.3. APKPure

Fixtures de:

- current version;
- historical versions;
- APK;
- XAPK;
- arquitectura múltiple;
- release inexistente.

### 24.4. Archives

Fixtures de:

- APK;
- APKS;
- APKM;
- XAPK;
- base + ABI + density + locale;
- feature split;
- OBB.

### 24.5. Instalación

Mocks/abstracciones que permitan verificar:

- lista de archivos escrita a Session;
- orden de `pm install-write`;
- fallback de instalador;
- cancelación;
- success/failure;
- cleanup post-instalación.

---

## 25. Política de seguridad

La app será un instalador/actualizador privilegiado desde el punto de vista del usuario. Por tanto:

1. **No instalar automáticamente un archivo cuyo `packageName` difiera del esperado.**
2. **No actualizar una app si la firma no puede continuar la instalación existente.**
3. Mostrar warning explícito si el usuario fuerza una reinstalación que exige desinstalar primero.
4. Nunca relajar validación porque el archivo provenga de una fuente “confiable”.
5. Verificar integridad cuando haya hashes provistos por la fuente.
6. Aislar WebView y limitar navegación cuando se use como downloader asistido.
7. No ejecutar archivos descargados distintos de los tipos esperados.
8. Mantener trazabilidad de origen de cada artefacto descargado.

Registrar en metadata local:

```text
source
source URL / release URL
packageName
versionCode
hash
signer
fecha de descarga
método de descarga: direct / assisted-web / external
```

---

## 26. Fases de implementación

### Fase 0 — skeleton

- proyecto Kotlin/Compose;
- `minSdk 23`;
- Material 3 Adaptive;
- módulos definidos;
- `UPSTREAMS.yml`;
- licencia del proyecto;
- CI básico.

### Fase 1 — inventario + compatibilidad local

- listar apps instaladas;
- `DeviceProfile`;
- adaptar F-Droid CompatibilityChecker;
- portar matcher esencial de bundletool;
- tests ABI/DPI/SDK.

### Fase 2 — Google Play

- GPlayApi;
- búsqueda;
- ficha;
- current version;
- descarga de versión actual;
- `versionCode` manual;
- DownloadCoordinator;
- SessionInstaller.

### Fase 3 — última versión compatible

- VersionCatalog;
- historial externo;
- selección de máxima versión compatible;
- UI de “última publicada” versus “última compatible”.

### Fase 4 — APKMirror

- metadata/update checker;
- parser versions/variants;
- resolver URL directa;
- APK/APKM;
- assisted WebView fallback.

### Fase 5 — APKPure

- búsqueda;
- historial;
- APK/XAPK;
- fallback.

### Fase 6 — instaladores avanzados

- Shizuku;
- Root;
- Legacy;
- router/fallback automático.

### Fase 7 — updater periódico

- checks WorkManager;
- lista de updates;
- notificaciones;
- batch updates cuando el método de instalación lo permita.

### Fase 8 — watcher de upstreams

- compare commits;
- path/symbol filters;
- dashboard;
- Issues/draft PRs;
- análisis asistido por IA.

---

## 27. MVP recomendado para el primer agente

No intentar todo a la vez.

La primera versión funcional debe demostrar el **núcleo diferenciador**:

1. Android 6+.
2. UI Compose Material 3 adaptativa teléfono/tablet.
3. Listar aplicaciones instaladas.
4. Buscar una app por Google Play.
5. Crear `DeviceProfile`.
6. Consultar versiones/candidatos.
7. Resolver compatibilidad SDK + ABI + DPI.
8. Mostrar:
   - versión instalada;
   - versión corriente;
   - última compatible.
9. Descargar desde Google Play cuando sea posible.
10. Instalar mediante `PackageInstaller.Session`.
11. Mantener el archivo o eliminarlo según preferencia.
12. Tener `UPSTREAMS.yml` y documentación de procedencia desde el primer commit.

Después incorporar APKMirror y APKPure como providers, en vez de mezclar todos los riesgos en el primer build.

---

## 28. Riesgos conocidos

### Google Play

- GPlayApi reproduce un protocolo no oficial/públicamente documentado de Play; puede requerir mantenimiento.
- versiones históricas necesitan `versionCode` conocido y que Google todavía conserve el artefacto.

Mitigación:
- VersionCatalog multifuente;
- watcher de GPlayApi/Aurora.

### APKMirror

- HTML cambiante;
- Cloudflare/rate limiting;
- descarga automática no garantizable permanentemente.

Mitigación:
- provider desacoplado;
- fixtures/tests;
- L2 WebView asistido;
- L3 navegador/SAF;
- watcher específico de parser.

### APKPure

- endpoints privados/no oficiales pueden cambiar;
- XAPK y múltiples formatos.

Mitigación:
- varias implementaciones de referencia;
- provider aislado;
- App Manager como normalizador de archivo.

### AndroidX / compatibilidad

- APIs mínimas de bibliotecas pueden volver a subir.

Mitigación:
- `minSdk 23` deliberado;
- Dependabot/Renovate;
- no congelar librerías salvo necesidad real.

---

## 29. Repositorios upstream prioritarios

### Núcleo / inventario / updates

- APKUpdater — https://github.com/rumboalla/apkupdater
- F-Droid client — https://github.com/f-droid/fdroidclient

### Compatibilidad y targeting

- bundletool — https://github.com/google/bundletool
- App Manager — https://github.com/MuntashirAkon/AppManager
- apksig-android — https://github.com/MuntashirAkon/apksig-android
- unapkm-android — https://github.com/MuntashirAkon/unapkm-android

### Google Play

- GPlayApi — https://gitlab.com/AuroraOSS/gplayapi
- Aurora Store — https://github.com/AuroraOSS/AuroraStore

### APKMirror

- APKUpdater — https://github.com/rumboalla/apkupdater
- APKMirror Downloader / APKMD — https://github.com/tanishqmanuja/apkmirror-downloader
- GlassDown — usar solo como referencia secundaria para UX/navegación APKMirror si el código disponible sigue siendo útil.

### APKPure

- APKUpdater — https://github.com/rumboalla/apkupdater
- Obtainium — https://github.com/ImranR98/Obtainium
- apkeep — https://github.com/EFForg/apkeep
- StoreKit — verificar repo/versión exacta antes de integrar.

### Instalación

- Droid-ify — https://github.com/Droid-ify/client
- Aurora Store — https://github.com/AuroraOSS/AuroraStore
- SAI — https://github.com/Aefyr/SAI — referencia histórica/secundaria para edge cases de split APKs.

---

## 30. Instrucciones explícitas para el agente implementador

1. **No reinventar funciones si existe un upstream maduro identificado en este documento.**
2. Antes de copiar/adaptar código, registrar el componente en `UPSTREAMS.yml`.
3. Registrar commit/ref exacto del upstream usado.
4. Conservar encabezados SPDX/licencia correspondientes.
5. Preferir dependencia publicada a copia de código cuando sea razonable.
6. Para código portado, copiar también tests conceptuales del upstream cuando la licencia lo permita.
7. Mantener providers completamente desacoplados del motor de compatibilidad.
8. No permitir que APKMirror/APKPure definan directamente “la variante correcta”.
9. No usar heurística propia de DPI si puede reproducirse la semántica de bundletool.
10. No implementar criptografía/firma Android desde cero; usar apksig/SigningInfo.
11. No condicionar la UI a teléfono; usar layouts adaptativos desde la primera versión.
12. No reducir `minSdk` por debajo de 23 en la aplicación principal.
13. Si una fuente falla, degradar la automatización, no la capacidad funcional: Direct → Assisted Web → Browser/SAF.
14. Un artefacto manualmente descargado debe pasar por exactamente el mismo verificador que uno automático.
15. El objetivo del MVP no es replicar una app store completa: es demostrar correctamente **“encuentra e instala la última versión compatible con este dispositivo”**.

---

## 31. Criterio de éxito de la primera versión

Considerar validado el concepto cuando, en al menos dos dispositivos/emuladores de APIs distintas, la aplicación pueda:

1. identificar correctamente el perfil del equipo;
2. listar apps instaladas;
3. buscar una app disponible en Google Play;
4. distinguir entre versión más reciente y versión más reciente compatible;
5. seleccionar ABI/densidad apropiadas sin heurísticas triviales;
6. descargar el conjunto de archivos requerido;
7. verificar package/firma;
8. instalar base + splits mediante Session Installer;
9. mostrar la misma información correctamente en layout Compact y Expanded;
10. registrar en metadata el origen del artefacto y la procedencia del código reutilizado.

Después de esa validación, incorporar APKMirror y APKPure de forma incremental.

---

## 32. Nota final de diseño

El valor del proyecto no consiste en crear otra tienda Android genérica. Su propuesta es unir piezas FOSS ya maduras para resolver un problema que hoy está fragmentado:

> **Dado este dispositivo concreto, encontrar la versión más nueva de una aplicación que todavía puede instalarse y funcionar, obtenerla desde la mejor fuente disponible, validar rigurosamente el paquete y completar la instalación con la mínima intervención posible.**

La arquitectura debe privilegiar tres cualidades:

1. **compatibilidad correcta**, antes que heurísticas rápidas;
2. **degradación elegante**, antes que depender de automatización frágil;
3. **proveniencia mantenible**, antes que copiar código sin trazabilidad.

Ese criterio debe guiar cualquier decisión no resuelta por esta especificación.
