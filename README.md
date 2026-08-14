# FrankUpdater

Cliente Android libre para encontrar, verificar e instalar la versión más reciente de una aplicación que sea realmente compatible con un dispositivo concreto.

## Estado

El repositorio se encuentra en fase de inicialización. Esta primera base contiene:

- Kotlin y Jetpack Compose con Material 3;
- UI adaptativa para ventanas compactas y amplias;
- `minSdk 23` y `compileSdk 37`;
- modelos de dominio independientes de los proveedores;
- comprobación básica y determinista de SDK/features;
- trazabilidad de upstreams desde el primer commit;
- CI para compilación, lint y tests.

La especificación fundante está en [`docs/spec/ANDROID_COMPAT_UPDATER_SPEC.md`](docs/spec/ANDROID_COMPAT_UPDATER_SPEC.md) y la secuencia de implementación en [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Compilar

Requisitos:

- Android Studio Quail o posterior;
- JDK 17 o posterior compatible con Gradle 9.5;
- Android SDK Platform 37;
- Android SDK Build Tools 36 o posterior.

En Windows:

```powershell
.\gradlew.bat lintDebug test assembleDebug
```

En Linux/macOS:

```bash
./gradlew lintDebug test assembleDebug
```

## Módulos iniciales

- `app`: shell Compose y punto de entrada Android.
- `core:model`: modelos normalizados sin dependencias Android.
- `core:compatibility`: contratos y reglas deterministas de compatibilidad.

Los providers y subsistemas adicionales se crearán cuando comience su fase, evitando módulos vacíos y acoplamientos prematuros.

## Distribución

FrankUpdater se distribuirá mediante GitHub Releases y plataformas libres como F-Droid. No se publicará en Google Play.

## Licencia y procedencia

FrankUpdater se distribuye bajo `GPL-3.0-or-later`. Antes de adaptar o portar código de un upstream, debe registrarse su repositorio, ref exacta, licencia y símbolos en [`UPSTREAMS.yml`](UPSTREAMS.yml). Consulta [`CONTRIBUTING.md`](CONTRIBUTING.md) antes de incorporar código externo.
