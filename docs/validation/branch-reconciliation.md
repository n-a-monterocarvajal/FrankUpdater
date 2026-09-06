# Reconciliación de ramas, 6 de septiembre de 2026

La base correcta es `8afd186` (`codex/stage-2-local-pipeline`). Sus etapas 1 y 2
ya tenían implementación, pruebas y demostración documentadas. La continuación
se realiza en `codex/stage-3-play`, descendiente directo de esa base.

La rama `codex/local-inventory`, commit `60531f6`, partió por error de `536b389`.
Duplicó la etapa 1 e inició otra implementación de la etapa 2. Ese commit se
conserva. Sus cambios pendientes se guardaron en un stash identificado como
`recovery: duplicated stage 2 before resuming 8afd186`; no se fusionó la rama.
La instalación local de habilidades y sus cambios del usuario se preservaron.

## Comparación y rescate

- Se mantiene la biblioteca, SAF, retención, interfaz y Session Installer de
  `8afd186`: tienen mayor cobertura funcional y demostración real.
- Se mantiene su lector de manifiestos compatible con API 23. No se reincorpora
  ARSCLib: la validación anterior documenta fallos en esa API; el intento nuevo
  de resolverlos con desugaring aún no tenía prueba en dispositivo.
- Se rescata la comprobación criptográfica por API concreta y la rotación de
  firma mediante apksig: solo un firmante, rotación hacia adelante y capacidad
  `installedData`. Un historial por sí solo, una identidad instalada vacía o un
  subconjunto de múltiples firmantes no autorizan una actualización.
- Se rescatan fixtures reales de apksig y sus pruebas JVM; el puente Base64 está
  exclusivamente en `src/test`, usa la biblioteca estándar Java y no simula la
  criptografía.
- Se añade rechazo de nombres de split duplicados y un límite de 2 MiB para el
  manifiesto descomprimido. El lector de `split` exige atributo sin namespace.
- Se corrige la referencia de apksig: la dependencia 4.4.0 corresponde al tag
  `1bd3a0c000c56e752b41d6f65c3a3d3d8ad7f049`, no al HEAD que se registró antes.
- Se rescata `scripts/verify.ps1`: un worker, heap de 1536 MiB, compilación Kotlin
  en el mismo proceso y uso del JDK instalado sin abrir Android Studio.

El selector nuevo de conjuntos completos de splits no se trasplanta: supone
otros modelos y un lector distinto, y aún rechaza variantes/regiones válidas.
La selección del conjunto ofrecido por Play debe revisarse con los metadatos de
delivery en la etapa 3. La etapa 2 recibe e instala conjuntos ya preparados;
sus pruebas no demuestran selección arbitraria entre variantes de un bundle.

La evidencia de dispositivos de las etapas anteriores se conserva. Esta revisión
no vuelve a abrir emuladores ni presenta esas pruebas históricas como ejecutadas
de nuevo.

Validación del rescate: 42 pruebas JVM, cero fallos; compilación de pruebas
completada en 2 min 47 s con un worker. Sin emuladores.
