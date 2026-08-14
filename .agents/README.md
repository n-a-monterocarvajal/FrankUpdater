# Cápsula de contexto para agentes IA

Esta carpeta contiene contexto operativo para agentes que trabajen en FrankUpdater. Está deliberadamente encapsulada:

- sus documentos pueden consultar y enlazar cualquier parte del repositorio;
- ningún archivo exterior debe depender de esta carpeta ni enlazarla;
- eliminar `.agents/` no debe afectar la compilación, los tests, la aplicación ni la documentación destinada a personas usuarias o colaboradoras.

## Orden de lectura

1. Leer [`PLAN.md`](PLAN.md) para conocer la etapa activa, sus entregables y criterios de cierre.
2. Consultar [`../docs/spec/ANDROID_COMPAT_UPDATER_SPEC.md`](../docs/spec/ANDROID_COMPAT_UPDATER_SPEC.md) como especificación fundante.
3. Consultar [`../docs/ROADMAP.md`](../docs/ROADMAP.md) para la secuencia oficial de etapas.
4. Revisar [`../docs/adr/`](../docs/adr/) y [`../UPSTREAMS.yml`](../UPSTREAMS.yml) antes de adoptar o portar decisiones externas.

Ante un conflicto entre esta cápsula y la documentación principal, prevalece la documentación principal. El agente debe actualizar esta cápsula para recuperar la coherencia, sin agregar referencias hacia `.agents/` desde el resto del repositorio.
