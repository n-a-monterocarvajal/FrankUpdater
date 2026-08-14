# Contribuir a FrankUpdater

## Flujo básico

1. Crea una rama desde `main`.
2. Mantén cada cambio acotado a un objetivo comprobable.
3. Ejecuta `./gradlew lintDebug test assembleDebug`.
4. Documenta cambios de arquitectura mediante un ADR en `docs/adr/`.

## Regla de procedencia

Antes de copiar, adaptar o portar código de otro proyecto:

1. verifica la licencia del repositorio y del archivo concreto;
2. registra el componente en `UPSTREAMS.yml`;
3. fija commit, tag o ref exacta;
4. conserva los avisos SPDX y atribuciones aplicables;
5. porta también los tests conceptuales cuando la licencia lo permita;
6. documenta cualquier divergencia intencional en `docs/upstreams/`.

Clasifica cada incorporación como `dependency`, `adapted`, `ported` o `reference`. No se acepta código de procedencia desconocida.

## Seguridad

Todo artefacto, automático o importado manualmente, debe atravesar el mismo pipeline de verificación. Nunca se desactiva una validación por considerar confiable una fuente.
