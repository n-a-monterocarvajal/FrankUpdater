# Watcher de upstreams

Implementación local del 17 de septiembre de 2026. El workflow semanal y manual
genera artefactos; no crea issues, PRs, commits ni merges. Su ejecución remota
queda pendiente de publicar el workflow, fuera del alcance autorizado.

`scripts/upstream-watch/watch.mjs` lee `UPSTREAMS.yml` con YAML 2.9.1 y consulta
las API públicas de GitHub y GitLab. Reutiliza respuestas por URL durante cada
ejecución. El token opcional de GitHub solo se envía a `api.github.com` y las
redirecciones no se siguen. Una consulta fallida conserva el estado registrado.

La comparación parte del último commit revisado, no del último observado:
un cambio pendiente de revisión no desaparece en la siguiente ejecución.
El dashboard muestra `last_seen_commit`, `last_reviewed_commit` y
`last_integrated_commit` por separado. Ninguna consulta modifica los dos últimos
ni las referencias del manifiesto. La observación nueva se conserva en el informe.

Los filtros aceptan paths exactos o directorios y símbolos. Renombres, parches
ausentes, respuestas truncadas y ausencia de filtros suficientes conservan una
clasificación de impacto desconocido. La coincidencia textual indica qué revisar;
no pretende sustituir una revisión semántica.

## Evidencia

- Tres pruebas Node aprobadas: selección por paths/símbolos, renombres, respuestas
  incompletas y separación de los tres estados.
- Lectura offline del manifiesto y generación del dashboard correctas.
- Primera ejecución real: 17 de septiembre, 13:09 UTC. Quince componentes
  consultados; ningún `query-failed`. Se detectaron cambios que requieren revisión
  en Obtainium y App Manager, además de cambios con impacto todavía desconocido.
- Artefactos locales: `build/upstream-watch/report.json` y `dashboard.md`.

Reproducir sin red:

```powershell
npm ci --prefix scripts/upstream-watch --ignore-scripts
node --test scripts/upstream-watch/watch.test.mjs
node scripts/upstream-watch/watch.mjs --offline
```

Omitir `--offline` solicita una nueva observación real. Los PRs del workflow usan
el modo offline; solo las ejecuciones manuales y semanales consultan upstreams.
