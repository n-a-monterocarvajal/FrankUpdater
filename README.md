# FrankUpdater

Cliente Android libre para encontrar, verificar e instalar la versión más reciente de una aplicación que sea realmente compatible con un dispositivo concreto.

## Estado

Las etapas 0, 1 y 2 están completadas. La validación real de Google Play sigue
pendiente tras un HTTP 403 del servidor anónimo. El desarrollo continúa con
catálogo multifuente y proveedores web (etapas 4 a 6).
La base actual contiene:

- Kotlin y Jetpack Compose con Material 3;
- UI adaptativa para ventanas compactas y amplias;
- `minSdk 23` y `compileSdk 37`;
- inventario local asíncrono de aplicaciones de usuario, sistema y deshabilitadas;
- perfiles genérico y Play separados;
- modelos de dominio independientes de los proveedores;
- comprobación determinista de SDK, target SDK, ABI y features;
- targeting ABI, multi-ABI, densidad y SDK portado del subconjunto esencial de bundletool;
- UI de inventario adaptativa con navegación compacta y amplia;
- trazabilidad de upstreams desde el primer commit;
- CI para compilación, lint y tests.

El pipeline local importa APK/APKS/APKM moderno/XAPK, verifica identidad y firma,
instala base y splits y permite conservar paquetes. Buscar incorpora acceso
anónimo mediante un servidor HTTPS configurable, consulta de Play y descarga
verificada a la biblioteca. No incluye un servidor predeterminado; la validación
del servicio vivo sigue pendiente. Consulta el [ADR de Play](docs/adr/0004-google-play-anonymous-provider.md).

Buscar también incorpora historial de APKMirror/APKPure, selección por código de
versión, descarga verificada y alternativas web/navegador con importación. Los
requisitos desconocidos se muestran como pendientes, sin afirmar compatibilidad.
El alcance probado y los pendientes están en [etapas 4 a 6](docs/validation/stage-4-6.md).

La especificación fundante está en [`docs/spec/ANDROID_COMPAT_UPDATER_SPEC.md`](docs/spec/ANDROID_COMPAT_UPDATER_SPEC.md) y la secuencia de implementación en [`docs/ROADMAP.md`](docs/ROADMAP.md).

La validación de las etapas cerradas está en [etapa 1](docs/validation/stage-1.md)
y [etapa 2](docs/validation/stage-2.md). La continuación desde la rama correcta y
las mejoras rescatadas están en [reconciliación de ramas](docs/validation/branch-reconciliation.md).

## Compilar

Requisitos:

- Android Studio es opcional para compilar; basta el JDK y el SDK;
- JDK 17 o posterior compatible con Gradle 9.5;
- Android SDK Platform 37;
- Android SDK Build Tools 36 o posterior.

En Windows:

```powershell
.\scripts\verify.ps1
```

En Linux/macOS:

```bash
./gradlew lintDebug test assembleDebug
```

## Módulos iniciales

- `app`: shell Compose y punto de entrada Android.
- `core:model`: modelos normalizados sin dependencias Android.
- `core:compatibility`: contratos y reglas deterministas de compatibilidad.
- `core:archive`: extracción y verificación de integridad de archivos locales.

La implementación Android de inventario y perfil vive en `app` y depende de los modelos puros. La estrategia de visibilidad y los datos tratados están documentados en [`docs/adr/0002-package-visibility-and-local-inventory.md`](docs/adr/0002-package-visibility-and-local-inventory.md).

Los providers y subsistemas adicionales se crearán cuando comience su fase, evitando módulos vacíos y acoplamientos prematuros.

## Distribución

FrankUpdater se distribuirá mediante GitHub Releases y plataformas libres como F-Droid. No se publicará en Google Play.

## Licencia y procedencia

FrankUpdater se distribuye bajo `GPL-3.0-or-later`. Antes de adaptar o portar código de un upstream, debe registrarse su repositorio, ref exacta, licencia y símbolos en [`UPSTREAMS.yml`](UPSTREAMS.yml). Consulta [`CONTRIBUTING.md`](CONTRIBUTING.md) antes de incorporar código externo.
