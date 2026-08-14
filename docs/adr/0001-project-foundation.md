# ADR 0001: Fundación del proyecto

- Estado: aceptado
- Fecha: 2026-08-13

## Contexto

FrankUpdater debe funcionar en Android antiguo, integrar múltiples fuentes frágiles y reutilizar algoritmos FOSS sin perder trazabilidad.

## Decisión

- `applicationId`: `io.github.n_a_monterocarvajal.frankupdater`.
- `minSdk`: 23.
- `targetSdk`: 37 inicialmente.
- UI: Kotlin, Compose, Material 3 y Material 3 Adaptive.
- Distribución: GitHub Releases y plataformas libres como F-Droid; no Google Play.
- Licencia del proyecto: GPL-3.0-or-later.
- Arquitectura inicial: `app`, `core:model` y `core:compatibility`.
- Los providers entregan candidatos normalizados y no seleccionan la variante final.
- Ningún permiso sensible se declara antes de implementar y documentar su uso.
- Todo código externo se registra en `UPSTREAMS.yml` antes de integrarse.

## Consecuencias

La GPL permite adaptar upstreams GPL identificados en la especificación, pero exige conservar avisos y facilitar el código fuente correspondiente. El identificador de aplicación debe revisarse antes de la primera release firmada; cambiarlo después crearía otra aplicación.
