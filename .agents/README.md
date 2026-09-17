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

## Ponytail (instalación local)

Las seis habilidades oficiales están en `skills/ponytail*`, sin modificaciones,
importadas de https://github.com/DietrichGebert/ponytail en el commit
`974d940a1c5344210874150b98ff0d2c861fab6a`. Licencia MIT:
[`skills/PONYTAIL-LICENSE`](skills/PONYTAIL-LICENSE). Procedencia registrada en
[`../UPSTREAMS.yml`](../UPSTREAMS.yml).

Codex descubre estas habilidades para este repositorio. Estarán disponibles
al siguiente turno; si no aparecen, reinicia Codex. Puedes invocarlas por nombre:

- `@ponytail`: soluciones mínimas; modos `lite`, `full` (predeterminado) y `ultra`.
- `@ponytail-review`: revisar complejidad innecesaria en el diff.
- `@ponytail-audit`: revisar complejidad en todo el repositorio.
- `@ponytail-debt`: recopilar comentarios de deuda `ponytail:`.
- `@ponytail-gain`: mostrar las cifras publicadas por el autor.
- `@ponytail-help`: consultar la ayuda.

En CLI se usa el prefijo `$`, por ejemplo `$ponytail-review`.
Para desactivar el modo en una conversación, indica `stop ponytail`.

Esta instalación contiene habilidades de instrucciones, sin los hooks del plugin
global. La configuración global de modos, la inyección al inicio de cada sesión y
las actualizaciones del marketplace descritas en la ayuda upstream no se aplican
a esta instalación. El modo se puede pedir en el mensaje al invocar `ponytail`.
Las cifras de `ponytail-gain` corresponden al benchmark antiguo incluido en esa
habilidad, no a mediciones de FrankUpdater; consulta el README upstream para la
metodología más reciente.

Para actualizar, importa las mismas seis carpetas desde una nueva revisión fija,
conserva la licencia y actualiza la referencia aquí y en `UPSTREAMS.yml`.
