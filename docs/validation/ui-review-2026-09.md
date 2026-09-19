# Revisión de UI y flujo — septiembre de 2026

Revisión manual del 18 de septiembre de 2026 sobre `cff7071` (rama
`codex/version-catalog`), APK de depuración recién compilado con
`scripts/verify.ps1` (lint, tests y `assembleDebug` correctos).

Entorno: AVD `Frank_API36_Phone` (Android 16, x86_64 + traducción arm64,
420 dpi), arranque en frío, instalación limpia. Apps de prueba instaladas en
versiones antiguas: Aurora Store 4.8.1 (73), Droid-ify 0.7.5, Obtainium 1.6.14,
F-Droid 1.23.2, APKUpdater 0.0.604-ci y Firefox Focus 150.0 (firma de Mozilla).
Google Play queda fuera de alcance: requiere cuenta o dispenser.

Severidad: **P0** bloquea el flujo central; **P1** lo degrada seriamente;
**P2** fricción notable; **P3** pulido.

## Flujo central observado

Inventario → seleccionar → *Comprobar* → resultados → (manual) Buscar →
escribir el paquete → Consultar APKPure → *Elegir esta variante* → bajar hasta
el panel → *Descargar y verificar* → Biblioteca → *Actualizar*.

Son unos 10 pasos, con dos saltos de pantalla en los que se pierde el contexto
y se reescribe a mano el nombre del paquete.

## Hallazgos

| ID | Pantalla | Tipo | Sev. | Observado | Esperado |
|---|---|---|---|---|---|
| F-01 | Buscar | Flujo | P0 | La descarga APKPure de Focus 156.0 (XAPK de 238 MB) falla a los 5 min exactos. Causa: `callTimeout(5, MINUTES)` en `WebSourceClient.kt:17` se aplica también a la descarga. Se borra el parcial y no se reanuda. | Sin límite total en descargas (solo `readTimeout`), con reanudación por `Range`. |
| F-02 | Buscar | UI | P0 | Durante la descarga no hay progreso, ni tamaño, ni botón de cancelar. El mensaje de estado queda fuera de la vista. | Barra de progreso (bytes/total) junto al botón y opción de cancelar. |
| F-03 | Buscar | Flujo | P1 | El error de la descarga es genérico ("No se pudo completar la operación…"). La excepción se descarta sin registrar nada (`act()` en `WebSourcesScreen.kt:88`). | Motivo concreto (tiempo agotado, hash, firma, espacio, HTTP) y `Log.w` con la causa. |
| F-04 | Actualizaciones | Flujo | P1 | Los resultados de la comprobación no tienen acción: filas no pulsables y el texto "Consulta este paquete en Buscar". | Pulsar la fila abre Buscar con el paquete ya consultado, o bien botón directo "Descargar y verificar". |
| F-05 | Buscar | Flujo | P1 | Tras *Elegir esta variante*, el panel *Descargar o importar* aparece al final de la lista (unos 14 desplazamientos por debajo), sin aviso. Parecía que el botón no hacía nada. | Panel fijo o hoja inferior al elegir, o desplazamiento automático. |
| F-06 | Buscar | Rendimiento | P1 | Frames de 0,7 a 1,6 s (`Davey!`) y toques perdidos. Toda la pantalla es un `Column` que compone hasta 50 evaluaciones más las variantes. | `LazyColumn` con claves. |
| F-07 | Buscar | UI | P1 | Los campos de paquete y URL no declaran `KeyboardOptions`. Gboard autocorrige y capitaliza: "orrg.mozilla.f", "httphttps://…", "/apk/Mozilla/…", barra final eliminada. | Paquete: `KeyboardType.Ascii`, sin autocorrección ni mayúsculas. URL: `KeyboardType.Uri`. |
| F-08 | Buscar | Flujo | P1 | El estado de Buscar (paquete, resultados, selección) se pierde al cambiar de pestaña. | Mantener el estado en un ViewModel o `rememberSaveable`. |
| F-09 | Buscar / APKMirror | Flujo | P1 | La página de la app en APKMirror lista unas 10 releases (152.0.6–155.0.1), pero la app muestra solo 2. | Listar todas las releases de la página (y paginar). |
| F-10 | Buscar / APKMirror | UI | P2 | Las variantes aparecen dos veces: primero como lista cruda ("universal · Android 12L+ …") y luego evaluadas ("ABI no especificada · Apkm"). "Universal" se convierte en "ABI no especificada". | Una sola lista; "universal" mapeado a todas las ABI. |
| F-11 | Buscar | UI | P2 | "Última compatible: Pendiente de comprobar" aunque hay variantes sin incompatibilidades (APKMirror sin minSdk resuelto). No se indica qué falta. | Explicar qué requisito falta, por variante. |
| F-12 | Buscar | UI | P2 | Enums sin traducir: "No compatible: Abi", "ApkPure", "Xapk", "MonolithicApk", "Update". | Textos legibles ("Arquitectura no compatible", "APKPure", "XAPK", "APK"). |
| F-13 | Buscar | UI | P2 | Las variantes incompatibles ocupan la mitad de la lista y hay entradas duplicadas (155.0.1 armeabi-v7a dos veces). | Ocultar las incompatibles tras "Mostrar N no compatibles" y deduplicar. |
| F-14 | Buscar | UI | P3 | "Solicitar primero en Play" aparece deshabilitado en cada variante cuando Play no está conectado. | Ocultarlo si Play no está conectado. |
| F-15 | Buscar | Flujo | P2 | Buscar no busca por nombre: pide el paquete exacto, y APKMirror pide pegar una URL. | Un campo único (nombre o paquete) que consulte todas las fuentes. |
| F-16 | Actualizaciones | UI | P2 | Los resultados muestran solo el nombre del paquete y códigos de versión ("401052158 → 402522045"), sin etiqueta, icono ni versionName. | Etiqueta, icono y "150.0 → 156.0". |
| F-17 | Actualizaciones | Flujo | P2 | La comprobación es asíncrona pero no hay progreso: hay que pulsar "Actualizar resultados" a mano. Conviven "Comprobar ahora" y "Actualizar resultados". | Observar el `WorkInfo` y refrescar solo; un único botón. |
| F-18 | Actualizaciones | Flujo | P2 | La comprobación consulta solo APKPure. Droid-ify 0.7.5 → 0.7.7 existe en F-Droid y APKMirror, pero aparece "Sin versión superior". | Catálogo multifuente también en la comprobación. |
| F-19 | Actualizaciones | UI | P2 | El texto dice "APKPure consulta los paquetes elegidos en Ajustes", pero se eligieron en el Inventario. La selección escribe en silencio la lista de Ajustes. | Una sola fuente de verdad, con texto coherente. |
| F-20 | Inventario | UI | P2 | El filtro por defecto es "Todas": la lista empieza con overlays de sistema ("2 Button Navigation Bar"). Las 7 apps de usuario quedan enterradas entre 249. | Filtro por defecto "Usuario". |
| F-21 | Inventario | UI | P2 | La cabecera y los controles fijos ocupan la mitad de la pantalla: solo caben 2 tarjetas. | Cabecera colapsable, tarjetas compactas. |
| F-22 | Inventario | UI | P3 | Filas sin icono de la app. Se pueden seleccionar paquetes que nunca se actualizan (RRO/overlays). | Icono y selección desactivada para overlays. |
| F-23 | Navegación | UI | P3 | Etiquetas abreviadas "Actual." y "Biblio.". "Actualizar" (refrescar inventario) se confunde con la pestaña Actualizaciones. | Etiquetas completas ("Apps", "Biblioteca") y "Recargar". |
| F-24 | Actualizaciones | UI | P3 | FrankUpdater aparece en su propia comprobación ("Fuente no disponible"). | Excluirlo o tratarlo aparte. |
| F-25 | Ajustes | UI | P2 | La lista de comprobación es un campo de texto multilínea con nombres de paquete; duplica la selección del Inventario. | Gestionarla desde el Inventario; en Ajustes, solo resumen y frecuencia. |
| F-26 | Biblioteca | UI | P3 | La tarjeta de paquete verificado ocupa unos 2/3 del ancho. No compara con la versión instalada (73 → 76). "Actualizar" y "Conservar" tienen el mismo peso visual. | Ancho completo, "Instalada 4.8.1 → 4.8.4" y una sola acción primaria. |
| F-27 | Biblioteca | Flujo | P3 | Al pulsar *Actualizar* se abre directamente el ajuste "Instalar apps desconocidas", sin explicación previa. Al volver: "Permiso revisado. Pulsa instalar de nuevo". | Explicación breve antes y reanudación automática al volver con el permiso concedido. |
| F-28 | General | i18n | P3 | Todos los textos están en el código Kotlin (sin `strings.xml`). | Recursos de strings para traducir y para pruebas. |
| F-29 | Biblioteca | Flujo | P1 | El paquete importado (Aurora 4.8.4), pendiente de decisión, desaparece tras ir al ajuste de permiso y cambiar de pestaña. Hay que importarlo de nuevo. | Conservar en la biblioteca el paquete pendiente hasta que se descarte. |
| F-30 | Biblioteca | UI | P2 | Fossify Calculator muestra "Reinstalar" aunque no está instalado. | "Instalar" si el paquete no está instalado; "Actualizar" si la versión es superior. |
| F-31 | Buscar / APKMirror | Flujo | P1 | Con "Incluir versiones preliminares" desactivado, la lista cruda de variantes de APKMirror permite elegir y descargar una beta. La lista evaluada sí la bloquea ("Canal preliminar"). | Aplicar el filtro de canal también a la lista cruda (o eliminarla, ver F-10). |
| F-32 | Buscar / APKMirror | Flujo | P2 | La página de variante de APKMirror ya publica minSdk, targetSdk, ABI, densidades, permisos y features. No se extraen, y por eso "Última compatible" queda pendiente (F-11). | Extraer esos metadatos de la página de variante. |
| F-34 | Instalación | Flujo | P1 | El APKM de Fossify Calculator se instaló con los 12 splits (`config.arm64_v8a`, `armeabi_v7a`, `x86`, `x86_64` y siete densidades). El targeting de bundletool no se aplica al instalar. | Solo base + `config.x86_64` + la densidad del dispositivo (420 dpi → xxhdpi). |
| F-35 | Biblioteca | UI | P3 | Tras instalar o actualizar, la tarjeta sigue mostrando "Instalar"/"Actualizar". Pulsar "Reinstalar" abre otra tarjeta de verificación con otro "Instalar", es decir, dos pasos. | Estado "Instalada 1.4.0" y acción en un solo paso. |
| F-33 | Web asistida | UI | P3 | El WebView muestra anuncios a pantalla completa y un banner de APKMirror Premium; hay que desplazarse bastante hasta "Download APK Bundle". Tras la descarga el diálogo se cierra sin confirmar nada. | Aviso de "descarga capturada, verificando…" y progreso. |

## Comparación con APKUpdater (misma selección de apps)

APKUpdater 0.0.604-ci, con un toque en "Look for Updates", encontró 8
actualizaciones entre GitHub, GitLab, F-Droid, APKMirror y APKPure. FrankUpdater
encontró 2 (Aurora y Focus) y ninguna se puede descargar desde la pantalla de
resultados.

Cada tarjeta de APKUpdater muestra icono, etiqueta, paquete, "4.8.1 → 4.8.4",
"73 → 76", changelog, icono de la fuente, *Ignore Version* e *Install App*. Es la
referencia para F-04, F-16 y F-18.

APKUpdater también muestra riesgos que FrankUpdater debe evitar. Ofrece
Droid-ify "0.7.5 → v0.7.8" desde GitHub con código "?", sin saber si la firma
coincide ni si es compatible. La ventaja de FrankUpdater es precisamente
verificar paquete, versión, firma y compatibilidad antes de ofrecer la
instalación.

## Verificaciones en vivo (`FRANK_LIVE_WEB=1`)

- `WebSourcesLiveTest` en la JVM: 3/3 correctos. Descarga real de APKMirror
  (Fossify Calculator, APKM de 4,3 MB, 12 APK verificados) y de APKPure.
- En la app, en el emulador: descarga directa de APKMirror de Fossify
  Calculator 1.4.0 (APKM) verificada y guardada en Biblioteca en 40 s.
- En la app, en el emulador: fallback de web asistida (WebView →
  "Download APK Bundle") capturado, verificado y guardado.
- En la app: Focus 155.0.1 de APKMirror (114 MB) y 156.0 de APKPure (238 MB)
  fallan por F-01. El emulador descarga a unos 125 KB/s.

Con esto quedan demostrados en la app los dos pendientes de la etapa 5:
descarga real de APKMirror y fallback asistido.

Instalación con el permiso "Instalar apps desconocidas" concedido:

- Fossify Calculator 1.4.0 (APKM de APKMirror): instalación nueva correcta,
  pero con todos los splits (F-34).
- Aurora Store 4.8.1 → 4.8.4 (APK importado por SAF): actualización correcta
  (`versionCode=76`), seguida de la pregunta de retención ("Preguntar").

## Funcionó correctamente

- Inventario, filtros y selección por lotes.
- Consulta del historial APKPure con evaluación de ABI (arm64 compatible, armeabi-v7a rechazada).
- Consulta en vivo de releases y variantes de APKMirror con `FrankUpdater/0.1`, desde el emulador y desde el host.
- Importación por SAF de Aurora 4.8.4: verificación de paquete, versión, SHA-256 y clasificación como "Update".

## Correcciones aplicadas (18 de septiembre)

| ID | Cambio | Comprobación |
|---|---|---|
| F-01 | Sin `callTimeout` global: solo `readTimeout` de 30 s por lectura y 30 s por consulta de metadatos. Reanudación con `Range` (hasta 3 reintentos) tras un corte, aceptando solo la continuación exacta (`206` + `Content-Range`); el hash se calcula sobre el archivo completo. | Test `download resumes a dropped transfer…`. Los CDN reales (winudf y R2) responden `206` con `Content-Range`. Emulador: Focus 156.0 (XAPK de 239 MB) descargado en unos 11 min, verificado y guardado, superando un corte de red de 12 s (móvil → wifi). Los dos intentos anteriores fallaron con cortes de unos 19 s porque los reintentos (2+4+6 s) eran demasiado cortos; ahora son hasta 5, de 5 s a 25 s, y el contador se reinicia cuando llegan datos. |
| F-02 | Barra de progreso determinada con "X MB de Y MB" y botón *Cancelar* bajo *Descargar y verificar*. | Emulador: "16 MB de 239 MB" visible junto al botón. |
| F-03 | El motivo se muestra entre paréntesis y la excepción se registra con `Log.w`. La cancelación se distingue del corte de red. | Emulador: "(Software caused connection abort)" tras un cambio de red wifi → celular. |
| F-04 | Las tarjetas de resultados son pulsables: abren Buscar y consultan APKPure para ese paquete sin reescribirlo. | Emulador: tocar `org.mozilla.focus` abre Buscar con el historial ya cargado. |
| F-05 | Al elegir una variante, el panel *Descargar o importar* se desplaza a la vista (`BringIntoViewRequester`). | Emulador: panel visible sin desplazamiento manual. |
| F-26 | Las tarjetas de la Biblioteca ocupan todo el ancho. | Emulador. |
| F-29 | El paquete pendiente vive fuera de la composición; no se cierra al cambiar de pestaña. | Emulador: Aurora importado sigue tras pasar por Ajustes y Buscar. |
| F-34 | `ConfigSplitSelector` (core/compatibility) elige por módulo el mejor split de ABI (orden del dispositivo) y de densidad (`ScreenDensitySelector`); el router instala solo esos. | Tests unitarios con el conjunto real de Fossify. Emulador: `splits=[base, config.x86_64, config.xxhdpi]` y la app arranca. |
| F-07 | `KeyboardOptions` sin autocorrección ni mayúsculas en los campos de paquete (`Ascii`) y URL (`Uri`) de Buscar, en el servidor de Play y en la lista de paquetes de Ajustes. | Emulador: la URL de APKMirror queda en minúsculas y conserva la barra final. |
| F-08, F-36 | El estado de Buscar (consulta, resultados, selección, progreso) y su scope viven fuera de la composición: cambiar de pestaña no cancela la descarga ni borra la consulta. Límite conocido: solo mientras viva el proceso (anotado con `ponytail:`); el siguiente paso es una descarga en `WorkManager` en primer plano con notificación. | Emulador: APKM de Focus (276 MB) siguió de 12 a 119 MB mientras se pasaba por Biblioteca y Ajustes; al volver, la selección seguía ahí. *Cancelar* muestra "Operación cancelada." y borra el parcial. |
| F-38 | Al terminar la descarga se muestra "Descarga completa. Verificando paquete…". | Compilación. |
| F-32, F-11 | Al abrir una release de APKMirror se leen hasta 12 páginas de variante: versionCode, minSdk, targetSdk, ABI, tamaño exacto y, en APK únicos, el SHA-256 del archivo. La entrada pasa a `constraintsKnown` con min y target conocidos; tamaño y hash verifican la descarga. Las features del modal no se usan: la página no indica cuáles son obligatorias (Focus lista cámara y huella, que son opcionales). | Tests con un fixture de la estructura real. Comprobado en el host: el SHA-256 publicado coincide con el APK descargado; los APKM no publican hash, pero el tamaño coincide. Emulador: Focus 155.0.1 pasa de "Última compatible: Pendiente de comprobar" a "402470008", y su APK de 114 MB se descarga y pasa la verificación de tamaño y SHA-256 publicados. |
| F-10, F-31 | Se retira la lista cruda de variantes de APKMirror: solo queda la lista evaluada, que aplica el canal y la compatibilidad. "universal" muestra las ABI reales de la página. | Emulador: "arm64-v8a, armeabi-v7a, x86_64 · Apkm" en lugar de "ABI no especificada". |
| F-18 | `lookupSources` consulta APKPure, APKMirror, F-Droid e IzzyOnDroid por nombre de paquete; lo usan la comprobación periódica y Buscar. Se descartan las entradas cuya firma publicada no coincide con la instalada (APKMirror publica el SHA-256 del certificado, APKPure el SHA-1). En la comprobación se prefiere una versión con firma confirmada a otra más alta con firma desconocida, y el estado lo dice. F-Droid e IzzyOnDroid no publican firma ni requisitos: sus versiones quedan como "firma sin confirmar hasta descargar". Las superiores a `suggestedVersionCode` se tratan como preliminares. | Emulador, mismas 8 apps (antes: 2 resultados, uno con código de versión ilegible): Droid-ify 750 → 770 (APKMirror, firma coincide; se descarta la 780 de IzzyOnDroid, build del desarrollador con otra clave), Focus → 156.0 (APKPure, firma coincide por SHA-1), Obtainium → 23563 (F-Droid, firma sin confirmar), F-Droid client sin actualización (ya no propone la 2.0-alpha8), FrankUpdater "No figura". De punta a punta: Droid-ify actualizado a 770 desde el resultado de la comprobación. |
| F-12 (parcial) | La fuente se muestra con su nombre ("APKMirror", "F-Droid", "IzzyOnDroid"). La regex de canal reconoce `alpha8`, `beta2`, `rc1`. | Tests. |

| F-06, F-13, F-14 | Buscar: `VersionCatalog().select()` se memoriza por entradas (antes se recalculaba en cada recomposición, incluidos los avisos de progreso cada 256 KB y cada tecla); la barra de progreso lee su estado sola, así que cada aviso recompone solo la barra; se muestran 10 filas (antes 50) y +20 por paso; se deduplican variantes idénticas; las no compatibles quedan tras "Mostrar N no compatibles"; "Solicitar primero en Play" solo aparece con Play conectado. | En el emulador, las métricas de frames no sirven (GPU por software: también el Inventario, que es `LazyColumn`, sale con un 100 % de frames atascados), así que la mejora se apoya en el análisis del código. Si en un dispositivo real sigue habiendo tirones, el siguiente paso es pasar las filas a elementos de la `LazyColumn` de la pantalla. |
| F-12 | Tipos y motivos en texto legible: "APK/APKM/XAPK", "todas las ABI", "arquitectura no compatible", "requiere una versión de Android más nueva", etc. | Emulador. |
| F-16, F-37 | Resultados de la comprobación con icono, nombre, paquete y "0.7.7 → 0.7.8 · IzzyOnDroid" (versionName en lugar de versionCode). La versión instalada se lee al mostrar: si ya se actualizó, se muestra "Actualizada a …". | Emulador (captura). |

Límite encontrado: la búsqueda de APKMirror (`/?s=`) responde HTTP 429 con desafío de Cloudflare a nuestro User-Agent tras unas pocas búsquedas, mientras que las páginas de app y release y el RSS responden 200. Por eso la búsqueda se usa una sola vez por paquete: la app recuerda la página de la app en APKMirror (también cuando el usuario consulta una URL a mano) y después lee su RSS. Hay 1,5 s entre peticiones HTML y, tras un 429, 10 min sin preguntar. Decisión del 18 de septiembre: como Obtainium (af286fa), las peticiones a APKMirror envían `APKUpdater-v3.5.9 FrankUpdater/0.1`; con ese token la búsqueda responde 200. Se mantienen la búsqueda única por paquete, el RSS, el espaciado y el backoff.

Nuevo hallazgo durante la corrección:

| ID | Pantalla | Tipo | Sev. | Observado | Esperado |
|---|---|---|---|---|---|
| F-36 | Buscar | Flujo | P1 | La descarga vive en el scope de la pantalla: cambiar de pestaña la cancela. | Descarga en un servicio o `WorkManager` en primer plano, con notificación. |
| F-37 | Actualizaciones | UI | P3 | Tras actualizar Aurora a 76, los resultados siguen mostrando "Instalada: 73". | Recalcular con la versión instalada al mostrar resultados. |
| F-38 | Buscar | UI | P3 | Tras llegar a "239 MB de 239 MB", la verificación del XAPK tarda unos 4 min sin indicarlo; la barra se queda llena. | Mostrar "Verificando paquete…" al terminar la descarga. |
