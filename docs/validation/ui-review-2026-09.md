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

| F-18 (firma remota) | F-Droid e IzzyOnDroid no publican firma. Para las dos versiones estables más nuevas por encima de la instalada, la app lee por HTTP `Range` solo el final del APK (fin del directorio central y APK Signing Block, 2–3 peticiones de unos KB) y extrae el certificado del primer firmante (v3.1/v3 si existe, si no v2). Si no coincide con la firma instalada, la versión se descarta. Es una pista para ordenar candidatos, no una verificación: el archivo descargado pasa igualmente la verificación completa. APK solo v1 o servidores sin rangos quedan como "firma sin confirmar". | Tests con los APK dorados de apksig (v1v2v3, con rotación de clave y v1-only) contra `ApkVerifier`, a través de un servidor de rangos simulado. Emulador: Droid-ify ya no propone la 0.7.8 de IzzyOnDroid (build del desarrollador con otra clave); Obtainium 1.6.17 de F-Droid pasa a "firma coincide con la instalada". |

| F-36 (completo) | Las descargas directas corren en `PackageDownloadWorker` (WorkManager en primer plano, `dataSync`), con notificación de progreso y *Cancelar*; Buscar refleja su progreso y resultado. La web asistida sigue en proceso porque sus cookies no deben guardarse en la base de WorkManager. | Emulador: con la app en segundo plano (pantalla de inicio), el worker descargó y verificó Fossify 1.2.0 y lo rechazó correctamente por *Downgrade* frente a la 1.4.0 instalada. |
| Actualización automática | Opción por app en los resultados. La comprobación periódica encola descarga e instalación solo para versiones con la firma confirmada. La sesión usa `USER_ACTION_NOT_REQUIRED` (Android 12+, permiso `UPDATE_PACKAGES_WITHOUT_USER_ACTION`): Android no pregunta cuando FrankUpdater es el instalador registrado. Si aún pide confirmación, el receptor muestra una notificación en lugar de abrir una actividad desde segundo plano. La retención sigue la política (*Conservar siempre* guarda; el resto no deja archivo). | Emulador: Obtainium reinstalada 1.6.14 con FrankUpdater como instalador; con la opción activa, la comprobación encontró 1.6.17 en F-Droid (firma leída del APK remoto) y la instaló sin ningún diálogo en unos 90 s (23533 → 23563). |
| F-35 | La acción de la tarjeta "Paquete verificado" se calcula con la versión instalada en el momento (misma lógica que F-30), no con la acción fijada al importar. La tarjeta conservada instala en un solo paso: vuelve a verificar el archivo y abre la sesión sin pasar por un segundo botón. Se quitó el nombre interno en inglés de la acción ("· Update") de la línea del SHA-256. | API 36: Aurora 4.8.1 → 4.8.4 (F-Droid) desde Biblioteca; tras confirmar en Android, las dos tarjetas pasan a "Reinstalar" y el mensaje a "Aplicación instalada correctamente". Con la nueva build, "Reinstalar" en la tarjeta conservada envía la sesión directamente y termina sin diálogo (FrankUpdater ya es el instalador registrado). API 23: Biblioteca se abre sin errores. |
| F-46 | El contenido de cada pestaña se limita a 840 dp de ancho y se centra; en teléfono no cambia nada (360–411 dp). Las acciones de la barra inferior del Inventario se alinean al final ("Seleccionar N" junto a "Comprobar"). | `Frank_API36_Tablet` (2560×1600) con solo ese emulador abierto: Apps, resultados, Buscar, Biblioteca y Ajustes se muestran sin crashes de la app (solo falló el Bluetooth del sistema); tras el cambio, Apps y Buscar quedan centrados y la barra agrupa sus acciones a la derecha. API 23: la barra inferior y la pantalla Apps se ven igual que antes. |
| F-47 | La notificación toma el paquete de la sesión de instalación y muestra el nombre de la app instalada ("Aurora Store lista para instalar"); si no hay nombre, el paquete. | Compilado y `verify.ps1` correcto. La notificación solo aparece en instalaciones en segundo plano que Android pide confirmar; falta verla en el S22. |
| F-51 (parcial) | El filtro del inventario solo se recalcula cuando cambian la lista, el texto o el filtro; los iconos quedan en una `LruCache` de proceso (300 entradas, ~11 MB como máximo). Para medir en el dispositivo se preparó un APK de release sin depuración, firmado con la misma clave de depuración. | `verify.ps1` correcto. En los emuladores la medición no es concluyente: con GPU por software o recién arrancados, casi todos los frames superan el umbral con y sin el cambio. Falta comparar en el S22. |
| F-48 | Navegación de cinco destinos: Actualizar (pantalla inicial, resultados y comprobación), Apps (inventario y selección), Buscar, Biblioteca y Ajustes. Desaparece el botón superior "Ver resultados de comprobaciones"/"Volver a seleccionar aplicaciones". "Comprobar (N)" en Apps guarda la selección, lanza la comprobación y pasa a Actualizar; Actualizar enlaza a "Elegir apps"/"Cambiar apps elegidas (N)". | API 36 (teléfono): abre en Actualizar con las cinco etiquetas completas; en Apps, "Comprobar (6)" lleva a Actualizar con "Comprobando 1 de 6…"; sin crashes. |
| F-49 | Cada resultado guarda la versión recomendada (firma confirmada o, si no la hay, la mejor sin firma publicada) como petición de descarga. La tarjeta muestra "Actualizar" (o "Cancelar" mientras trabaja) y "Otra versión" (Buscar); arriba, "Actualizar todas (N)" con las que siguen atrasadas. El estado de la descarga se ve en la tarjeta: en cola, porcentaje y tamaño, verificando e instalando, confirmar o error; al terminar pasa a "Actualizada a …". Con la app en pantalla, la confirmación de Android se abre directamente en vez de ir a una notificación (antes, sin permiso de notificaciones, la instalación quedaba esperando sin forma de confirmarla). El permiso de notificaciones se pide una vez por visita. | API 36: Aurora 4.8.1 (F-Droid, instalada por adb) → "Actualizar" en su tarjeta: descarga, verificación, diálogo de Android abierto directamente con notificaciones denegadas, confirmación y tarjeta en "Actualizada a 4.8.4"; el contador de "Actualizar todas" se recalcula al terminar. |
| F-50 | Apps suma dos filtros: "Ocultar desactivadas" (activo por defecto) y "Ocultar de Play Store" (Play Store o Amazon como instalador; las de Galaxy Store no se ocultan). La elección de Todas/Usuario/Sistema y de ambos filtros se guarda. Además, el último inventario leído queda en memoria: al volver a la pestaña se muestra al instante y se refresca en segundo plano (antes, al ser un destino propio, cada visita esperaba la lectura completa). "Comprobar ahora" vuelve a ser el botón principal cuando no hay varias actualizaciones pendientes. | API 36: con Todas y ambos filtros, 249 de 256 visibles; tras cerrar y reabrir la app se conservan. La primera lectura tarda unos 5 s en el emulador; al volver a Apps no aparece la carga. Test unitario de los filtros (Galaxy Store visible, Play Store y desactivadas ocultas). API 23 (fuente grande): las cinco etiquetas de la barra caben; sin apps elegidas, Actualizar muestra "Elegir apps" como botón principal y lleva a Apps; los filtros nuevos se ven ("Ocultar de Play Store" en dos líneas); sin crashes. |
| F-52 | Con Google Play sin conectar, la barra de progreso y el mensaje de estado se muestran dentro de la tarjeta, bajo "Conectar"; conectado, siguen tras la búsqueda. | API 23: con `http://example.invalid`, el error "No se pudo completar la operación…" aparece justo debajo del botón. No se probó el dispenser real desde el emulador (enviaría el perfil del dispositivo a un tercero). |
| F-54 | Si el texto no es un paquete, Buscar ofrece "Buscar «texto» en APKMirror" (búsqueda de apps, `searchtype=app`, hasta 10 resultados con desarrollador; primero los nombres que empiezan por lo buscado). Al elegir una, el paquete se toma del enlace a Play Store de la página de la app (o, si no está en Play, de la primera variante de su release más reciente); la página queda recordada para las comprobaciones y se consultan todas las fuentes como con un paquete escrito. "Consultar fuentes" solo se activa con un paquete válido. Todas las peticiones a APKMirror pasan por el mismo espaciado y pausa tras 429. | API 23: "firefox" → 10 apps de APKMirror → "Firefox Fast & Private Browser · Mozilla" → `org.mozilla.firefox` → recomendada 143.0.4 (código 2016118135), la última compatible con Android 6, aunque la más reciente conocida es 2016183655. Test unitario del parser (filas de release descartadas, desarrollador, paquete desde el enlace a Play). |
| F-55 | "Buscar «texto» en la web" consulta APKPure (`v3/search_query_new`, que ya trae el paquete) y APKMirror a la vez. De APKPure se toma el primer resultado no publicitario de cada sección, la regla de `ApkPureRepository.search` de APKUpdater (69b6fcd, GPL-3.0); de ambas fuentes solo quedan los nombres que comparten alguna palabra con lo buscado, y de APKMirror se omiten los que repiten un nombre de APKPure. Si una fuente falla, se muestran los de la otra. | API 23: "zoom workplace" → Zoom Workplace, Zoom Workplace for Chromebook… (sin los juegos populares que APKPure intercala) → `us.zoom.videomeetings` → recomendada 6.1.10.23840 (código 6601100), la última compatible con Android 6; 10 versiones más nuevas quedan como no compatibles. Test unitario (primer resultado por sección, anuncios fuera, filtro por palabras). |
| F-56 | El error de Google Play incluye el código HTTP ("El servicio respondió HTTP 403") o "sin conexión"; otros detalles no se muestran porque podrían contener URL firmadas. Acceso anónimo tiene un campo "User-Agent (opcional)" que se guarda con la dirección y solo se envía en las peticiones al servidor anónimo (`postAuth`, `getAuth`), nunca a Google Play; admite texto ASCII visible de hasta 256 caracteres. La respuesta del servidor admite `authToken` además de `auth` como nombre del testigo de sesión, y se ignoran los campos que no se usan. | API 23, con un servidor que exige User-Agent: "Acceso anónimo conectado", "Google Play conectado" y una búsqueda de Play con resultados. Tests unitarios: la cabecera va solo a ese servidor, se rechazan caracteres de control y se aceptan ambos nombres del testigo. |
| F-57 | La búsqueda de Google Play usa `WebSearchHelper` de la dependencia `gplayapi` que ya teníamos, en vez del endpoint nativo; el idioma del dispositivo viaja con la consulta. | API 36: "zoom workplace" devuelve Zoom Workplace (`us.zoom.videomeetings`) primero, seguido de las versiones para Chromebook, Intune, Rooms Controller y TV. Antes salían WhatsApp, ChatGPT y Pinterest. |
| F-27 | Al volver del ajuste "Instalar apps desconocidas" con el permiso concedido, la instalación sigue sola. El permiso se comprueba cada vez que la app vuelve a primer plano, no al recibir el resultado: en Android 15+ Ajustes abre una actividad intermedia que responde al instante y la app vuelve a primer plano unos segundos antes de que aparezca el interruptor (por eso el mensaje anterior "Permiso revisado" salía antes de actuar). Si se vuelve sin conceder, la espera continúa. El mensaje previo explica que la instalación sigue sola al volver. | API 36, con el permiso revocado por el usuario: "Reinstalar" en la tarjeta conservada abre el ajuste; tras conceder y volver, "Sesión enviada a Android" y "Aplicación instalada correctamente" sin otro toque. Los dos primeros intentos (comprobar al recibir el resultado y en el primer regreso) fallaron por la actividad intermedia. En API 23 no hay permiso por app. |
| F-24 | La comprobación ignora el paquete de FrankUpdater y "Seleccionar N" no lo incluye (el contador lo descuenta). | API 36: "Seleccionar 6" con 7 visibles; la comprobación de esas 6 no genera fila para `io.github.n_a_monterocarvajal.frankupdater`. |
| F-22 | Cada fila del Inventario muestra el icono de la app (36 dp), cargado fuera del hilo principal y solo para las filas que la cuadrícula compone. La selección de overlays queda mitigada por el filtro "Usuario" por defecto (F-20). | API 36: iconos de las 7 apps de usuario. API 23: iconos de sistema en 360 dp. `InventoryScreenTest` correcto en API 23. |
| F-43 | Cada app del Inventario es un elemento compacto: casilla al inicio, nombre como título, "versión · paquete" en una línea de apoyo e insignias solo cuando informan (Sistema, Deshabilitada, splits). Toda la fila marca o desmarca (`toggleable`, rol de casilla). | API 36: caben las 7 apps de usuario (antes unas 3,5). API 23: 4 elementos legibles en 360 dp. Tocar "Droid-ify" pasa a "1 seleccionadas" y "Comprobar (1)". `InventoryScreenTest` correcto en API 23. |
| F-45 | `LegacyTrust` añade la raíz pública ISRG Root X1 (huella SHA-256 96BC…08C6, comprobada con el PEM de letsencrypt.org y con el almacén de Windows) a las raíces del sistema, solo para OkHttp y solo en API ≤ 24 (en API 23 no existe `network_security_config`). Buscar registra cada fallo de fuente y el mensaje distingue "No se pudo consultar ninguna fuente", "Ninguna fuente tiene este paquete… Sin respuesta: …" y "Fuentes consultadas: … Sin respuesta: …". | Test JVM: la raíz incluida es la publicada y se suma a las del sistema. API 23 antes: F-Droid e IzzyOnDroid con `Trust anchor … not found`. Después, Droid-ify en API 23: "Fuentes consultadas: APKPure, F-Droid, IzzyOnDroid, APKMirror", sin fallos en el log. |
| F-40 | Cada versión de Buscar es una tarjeta: versión como título; código y fuente como apoyo; ABI y formato en una línea; canal, firma y estado como insignias de color (error para "No compatible", terciaria para "Instalada"); acción "Elegir" alineada a la derecha y solo en versiones instalables. La recomendada (última compatible, canal permitido, superior a la instalada) va en `primaryContainer` con insignia "Recomendada" y botón relleno. | API 36 (Firefox Focus): 156.0 recomendada con "Firma coincide" y botón "Elegir" relleno; el resto con botón con contorno. API 23 (Droid-ify): tarjetas legibles en 360 dp, recomendada resaltada. |
| F-42, F-23 | Navegación con iconos Material (update, search, folder, settings) copiados como vectores en `res/drawable` (Apache 2.0, sin dependencias nuevas) en lugar de símbolos de texto; etiquetas completas "Apps", "Buscar", "Biblioteca", "Ajustes". La barra superior pasa de `headlineSmall` con 18 dp de margen vertical a `titleLarge` con 12 dp. | API 23 (360 dp) y API 36: los cuatro iconos se ven nítidos, las etiquetas caben completas y la barra superior baja de unos 200 px a unos 155 px. |
| F-41 (parcial) | Esquema de color propio a partir del azul del icono (#0A65CC), claro y oscuro, con todos los roles de superficie; color dinámico en Android 12+ y el mismo esquema estático por debajo. `MaterialExpressiveTheme`, `MotionScheme.expressive()` y los componentes expresivos son internos en Material 3 1.4.0 (el que trae el BOM `2026.08.00`): queda como decisión D-02. | API 23: esquema azul estático aplicado a barra, chips, botones y navegación. API 36: color dinámico del fondo de pantalla. |
| F-30 | La acción de cada paquete conservado se decide con la versión instalada en el momento: "Instalar" (no instalada), "Actualizar" (conservada más nueva), "Reinstalar" (misma versión) o "Anterior a la instalada" (deshabilitado; Android rechaza bajar de versión). | API 36 con Aurora 4.8.4 conservada: "Reinstalar" con 4.8.4 instalada, "Instalar" tras desinstalarla y "Actualizar" con 4.8.1 instalada. API 23: Biblioteca se abre sin errores. Importar una versión inferior a la instalada se sigue rechazando en la verificación (*Downgrade*). |
| F-21 (+ F-23 parcial) | Resumen del dispositivo, búsqueda, filtros y contador pasan a ser cabecera desplazable de la cuadrícula; "Seleccionar N" y "Comprobar (n)" quedan en una barra fija inferior. "Actualizar" (recargar inventario) pasa a "Recargar", sin chocar con la pestaña Actualizaciones. Las tarjetas siguen siendo altas: se compactarán en el trabajo de F-39 por pantalla. | API 36: al desplazar se ven unas 3,5 tarjetas (antes 2) con la barra visible. API 23 (360 dp): la barra cabe en una línea (una primera versión con el contador en la barra se partía letra a letra y se corrigió). `InventoryScreenTest` correcto en API 23. |
| F-20 | El Inventario abre con el filtro "Usuario". El test instrumentado lo comprueba: la app de sistema no aparece por defecto y sí tras pulsar "Todas". | `InventoryScreenTest` correcto en API 23 y API 36; en la app, la lista abre con las apps de usuario. |
| F-19, F-25 | La selección de apps vive solo en Actualizaciones. Ajustes deja de tener el campo de paquetes y muestra un resumen ("N apps elegidas en Actualizaciones · M con actualización automática"). Los textos se corrigen: Ajustes ya no dice "solo APKPure; no se descarga ni instala nada" (falso desde F-18 y la actualización automática), y Actualizaciones remite a su propia selección en vez de a Ajustes. | API 36: "7 apps elegidas · 1 con actualización automática". API 23: "0 apps elegidas · 0 …" y el interruptor se muestran. |
| F-17 | La pantalla de resultados observa el trabajo de comprobación (manual o periódico): barra y "Comprobando N de M…" mientras corre, y recarga sola al terminar. Se quitan "Actualizar resultados" y el aviso de "actualiza cuando termine". | API 36: "Comprobando 1 de 7…" a "7 de 7…" y "Última consulta" pasó sola de 2:19 a 4:20. API 23: la pantalla se muestra sin errores. |
| F-15 | El campo de Buscar acepta "nombre de app instalada o paquete": si el texto no es un nombre de paquete, sugiere hasta 5 apps de usuario instaladas cuyo nombre o paquete coincida; tocar una rellena el paquete y consulta las fuentes. La búsqueda por nombre en fuentes web queda como decisión pendiente. | API 36: "droid" sugiere Droid-ify, F-Droid y Obtainium; tocar Droid-ify consulta APKPure, F-Droid, IzzyOnDroid y APKMirror ("Última compatible: 770"). API 23: "frank" sugiere FrankUpdater. |
| F-09 (no reproducible) | Sin cambio de código. Con el HTML real de la página de Focus, `MirrorParser.releases` devuelve las 10 releases del widget "All versions", y la app las muestra todas al desplazar (152.0.6 → 155.0.1). El "solo 2" original venía de la medición: `uiautomator` solo informa de los elementos visibles en pantalla. Límite conocido, no defecto: la página de la app solo publica las ~10 releases más recientes. | Test temporal con el HTML descargado; emulador API 36 (`emulator-5554`). |
| Downgrades en Buscar | Las variantes inferiores a la versión instalada muestran "Inferior a la versión instalada" y las iguales "Es la versión instalada", sin botón de descarga. | Emulador: Fossify 1.4.0 instalada; 1.4.0 marcada como instalada y 1.3.0 como inferior. |

Pendiente de validar en un teléfono Samsung: apps de Good Guardians desde APKMirror (librerías y features de One UI; las features del modal no se evalúan) y el primer paso con Galaxy Store como instalador registrado, que pedirá confirmación una vez.

Nota sobre las pruebas: los caracteres perdidos al escribir ("orrg.mozilla.f", "org.foo") venían de `adb shell input`, que el emulador trataba como lápiz: activaba la escritura a mano de Gboard. Con `input touchscreen`/`input keyboard` el texto llega completo. La autocorrección y las mayúsculas de F-07 sí eran de la app.

Límite encontrado: la búsqueda de APKMirror (`/?s=`) responde HTTP 429 con desafío de Cloudflare a nuestro User-Agent tras unas pocas búsquedas, mientras que las páginas de app y release y el RSS responden 200. Por eso la búsqueda se usa una sola vez por paquete: la app recuerda la página de la app en APKMirror (también cuando el usuario consulta una URL a mano) y después lee su RSS. Hay 1,5 s entre peticiones HTML y, tras un 429, 10 min sin preguntar. Decisión del 18 de septiembre: como Obtainium (af286fa), las peticiones a APKMirror envían `APKUpdater-v3.5.9 FrankUpdater/0.1`; con ese token la búsqueda responde 200. Se mantienen la búsqueda única por paquete, el RSS, el espaciado y el backoff.

Nuevo hallazgo durante la corrección:

| ID | Pantalla | Tipo | Sev. | Observado | Esperado |
|---|---|---|---|---|---|
| F-36 | Buscar | Flujo | P1 | La descarga vive en el scope de la pantalla: cambiar de pestaña la cancela. | Descarga en un servicio o `WorkManager` en primer plano, con notificación. |
| F-37 | Actualizaciones | UI | P3 | Tras actualizar Aurora a 76, los resultados siguen mostrando "Instalada: 73". | Recalcular con la versión instalada al mostrar resultados. |
| F-38 | Buscar | UI | P3 | Tras llegar a "239 MB de 239 MB", la verificación del XAPK tarda unos 4 min sin indicarlo; la barra se queda llena. | Mostrar "Verificando paquete…" al terminar la descarga. |
| F-39 | General | UI | P2 | La interfaz usa Material 3 base con ajustes propios. No sigue Material 3 Expressive (M3E): formas, tipografía enfatizada, contenedores y componentes nuevos. | Adoptar M3E en líneas generales (tema, formas, tipografía, navegación) y en particular pantalla por pantalla, con los componentes que ya ofrece el BOM `2026.08.00`. Condición: la app debe seguir sirviendo en dispositivos antiguos (`minSdk 23`). Solo componentes de Compose que funcionen desde API 23; color dinámico solo en Android 12+, con un esquema estático equivalente por debajo; nada que dependa de APIs recientes sin alternativa (desenfoque o `RenderEffect`, API 31+); movimiento y cambios de forma moderados para no penalizar hardware lento. Cada cambio se verifica también en `Frank_API23_Phone`. |
| F-41 | Tema | UI | P2 | Tema M3 base (`lightColorScheme()`/`darkColorScheme()` por defecto, morado genérico), sin color dinámico, sin formas ni tipografía propias. | Parte de F-39. Esquema propio de la marca (azul del icono) con color dinámico en Android 12+ y el mismo esquema estático por debajo; `MaterialExpressiveTheme` si la versión de Material 3 lo ofrece desde API 23; formas y tipografía expresivas moderadas. |
| F-42 | Barra superior y navegación | UI | P2 | La barra "FrankUpdater" ocupa unos 200 px en cada pantalla (más en API 23 con fuente grande). La navegación usa símbolos de texto (↻ ⌕ ▣ ⚙) y etiquetas abreviadas ("Actual.", "Biblio."; F-23). | Parte de F-39. Barra superior compacta o integrada en el contenido; iconos Material Symbols como vectores propios en `res/drawable` (sin dependencias nuevas); etiquetas completas. |
| F-43 | Inventario | UI | P2 | Tarjetas altas (casilla, nombre, paquete, versión y chips en cuatro líneas): caben 3,5 en API 36. | Parte de F-39. Elementos de lista compactos (`ListItem`: icono o casilla al inicio, nombre como título, paquete y versión como apoyo, chips al final). |
| F-45 | Fuentes (Android 6–7.0) | Flujo | P1 | En API 23, F-Droid e IzzyOnDroid fallan con `SSLHandshakeException: Trust anchor for certification path not found`: su certificado es de Let's Encrypt, cuya raíz ISRG Root X1 no existe en Android antes de 7.1.1. Además, Buscar decía "Ninguna fuente tiene este paquete" aunque las fuentes no hubieran respondido, y no registraba el motivo. | Confiar también en ISRG Root X1 en esas versiones; mensaje que distinga "sin respuesta" de "no figura"; registrar la causa. |
| F-46 | Diseño ancho (tableta) | UI | P3 | En `Frank_API36_Tablet` (2560×1600) la navegación lateral funciona, pero el contenido ocupa todo el ancho: el campo de búsqueda del Inventario mide unos 1800 px y "Seleccionar N" queda centrado en una barra inferior de todo el ancho. | Limitar el ancho del contenido (por ejemplo 840 dp, centrado) y alinear las acciones de la barra al final. |
| F-47 | Notificaciones | Flujo | P2 | Galaxy S22 Ultra (Android 16, One UI 8): la notificación de confirmación de instalación dice "Actualización lista para instalar · Toca para confirmar" sin nombrar la app. Con varias pendientes no se sabe cuál es cuál. | El título nombra la app ("Aurora Store lista para instalar"). |
| F-48 | Navegación | UI | P1 | Galaxy S22 Ultra: las actualizaciones, que son el centro de la app, quedan escondidas tras el botón superior "Ver resultados de comprobaciones" dentro de Apps. APKUpdater tiene destinos propios en la barra inferior para el inventario y para las actualizaciones. | Destino propio "Actualizaciones" en la navegación, separado de "Apps" (inventario y selección). |
| F-49 | Actualizaciones → Buscar | Flujo | P1 | Galaxy S22 Ultra: tocar una actualización lleva a Buscar para elegir la versión; Buscar guarda una sola consulta, así que al pasar a la segunda app se pierde el estado de la primera, y las descargas en curso solo se pueden seguir desde la barra de notificaciones. | Actualizar desde la propia tarjeta (y "Actualizar todas") encolando la versión recomendada con la firma confirmada, como ya hace la actualización automática; estado de descarga e instalación visible en cada tarjeta. Buscar queda para elegir otra versión. |
| F-50 | Apps (inventario) | UI | P2 | APKUpdater tiene en su inventario tres interruptores persistentes: excluir apps de sistema (también las de sistema actualizadas), excluir desactivadas (activo por defecto) y excluir las instaladas desde tiendas (Play Store, Amazon). FrankUpdater solo filtra Todas/Usuario/Sistema y no recuerda la elección. Los datos ya están en `InstalledApp` (`isEnabled`, `installerSource`). | Añadir "Ocultar desactivadas" (activo por defecto) y "Ocultar las de Play Store" (inactivo) junto a los filtros actuales, y recordar la elección. Las apps de Galaxy Store no se ocultan, porque son las de Good Guardians. |
| F-51 | Apps (inventario) | Rendimiento | P2 | Galaxy S22 Ultra: caen los FPS mientras carga la lista y las primeras veces que se escribe en el campo de búsqueda. El filtro se recalculaba en cada recomposición y los iconos no tenían caché: cada fila que volvía a mostrarse decodificaba su icono otra vez. Además, el APK de prueba era de depuración (Compose sin optimizar). | Filtro memorizado y caché de iconos por proceso; comparar en el dispositivo con un APK de release. |
| F-52 | Buscar → Google Play | UI | P2 | Galaxy S22 Ultra: al pulsar "Conectar" con un servidor anónimo no se ve si funcionó o falló: la barra de progreso y el mensaje se dibujaban al final de la pantalla, debajo de "Buscar en Google Play". | Progreso y resultado junto al botón. |
| F-54 | Buscar | Flujo | P1 | Solo se podía buscar por nombre entre las apps instaladas; una app no instalada exigía conocer su paquete. Es justo el caso de un dispositivo que quedó con un Android antiguo antes de instalarla. | Búsqueda por nombre en APKMirror (D-01, opción 1) que lleve a la consulta normal por paquete. |
| F-55 | Buscar | Flujo | P1 | Galaxy S22 Ultra: "Zoom Workplace" no está en APKMirror, así que la búsqueda por nombre (solo APKMirror) devolvía apps sin relación. | Buscar también en APKPure y descartar resultados que no compartan ninguna palabra con lo buscado. |
| F-56 | Buscar → Google Play | Flujo | P2 | Galaxy S22 Ultra: el servidor anónimo probado rechaza la conexión (HTTP 403) y el error no decía por qué. Algunos servidores solo atienden a clientes que se identifican con un User-Agent concreto. | Mostrar el código HTTP en el error y permitir indicar un User-Agent opcional para el servidor. |
| F-57 | Buscar → Google Play | Flujo | P1 | Galaxy S22 Ultra y emuladores: buscar "Zoom" o "Zoom Workplace" en Google Play devuelve apps sin relación (WhatsApp, ChatGPT, Pinterest). El endpoint nativo de búsqueda responde a una sesión anónima con apps populares en vez de coincidencias. | Usar la búsqueda de la tienda web (`WebSearchHelper` de gplayapi, a la que Aurora Store cambió en 660670a), que devuelve los paquetes que coinciden. |
| F-44 | Biblioteca y Ajustes | UI | P3 | Tarjetas y opciones sin jerarquía clara entre acción principal y secundarias; bloques de texto largos en Ajustes. | Parte de F-39. Se registra; se trabajará tras F-41 a F-43 y F-40. |
| F-40 | Buscar | UI | P2 | La lista de versiones se ve como texto plano: varias líneas seguidas por versión (versión y fuente, canal y firma, estado, "Inferior a la versión instalada", botón), sin separación entre versiones ni jerarquía entre dato principal y secundario. | Cada versión como elemento propio (tarjeta o `ListItem`): versión y fuente como título, ABI y formato como apoyo, canal, firma y estado como chips o insignias, y la acción alineada. Agrupar por versión y marcar visualmente la recomendada. |

## Pendientes

Cola de trabajo. Cada hallazgo abierto de la tabla anterior es un pendiente; aquí se resumen por severidad junto a lo que no es un F-xx.

Hallazgos abiertos:

- **P1:** ninguno (F-48 y F-49 cerrados; falta verlos en el S22).
- **P2:** ninguno automatizable. De F-39 queda la parte expresiva (componentes y movimiento), a la espera de Material 3 1.5 estable (D-02).
- **P3:** F-28 (pantalla por pantalla, D-03), F-33 (cubierto por F-02; falta probar el flujo de web asistida en el emulador), F-44.
- **Condicional:** F-06, pasar las filas de Buscar a elementos de la `LazyColumn` si en un dispositivo real sigue habiendo tirones.

Otros pendientes:

- Descargar por Google Play una versión concreta mediante su código, que es el caso que motivó conectar Play.
- La búsqueda web de Play no necesita sesión: se podría ofrecer sin conectar, dejando la sesión solo para descargar.

- Revisar la jerarquía lógica y visual del resto de pantallas con el mismo criterio que F-40: dato principal frente a secundario, agrupación y acción principal destacada.
- ~~Actualizar `last_reviewed_commit` en `UPSTREAMS.yml`~~ **hecho**: Obtainium (`af286fa`), App Manager (`a6f6628`), Aurora Store (`660670a`, perfil de dispositivo e instalador) y apksig-android (`c120428`); `last_integrated_commit` sin cambios. El vigilante lee el manifiesto (tests de Node correctos y lectura sin red).
- Recorrido de referencia en `Frank_API23_Phone` (API 23, 1080×1920, 480 dpi, fuente grande): **hecho**. Las cinco pantallas (Inventario, resultados, Buscar, Biblioteca, Ajustes) se muestran sin errores ni crashes; tras la navegación, `gfxinfo` marca un 55 % de frames lentos (el emulador usa GPU por software, así que sirve solo como comparación relativa antes y después de F-39). La barra superior y los bloques de texto ocupan gran parte de la altura útil (360 dp de ancho).
- Recorrido en `Frank_API36_Tablet`: **hecho** (19 de septiembre, con solo la tableta abierta; F-46 corregido). Antes, **parcial**: La pantalla Apps en diseño ancho se muestra con la barra lateral (iconos y etiquetas completas) y sin crashes; con tres emuladores abiertos el sistema de la tableta entró en "system isn't responding" y no se pudieron recorrer las demás pestañas. Hallazgo registrado: F-46. Repetir con solo la tableta abierta.
- Probar la confirmación de instalación por notificación, con el permiso de notificaciones concedido.

Requieren a una persona:

- ~~Volver a conceder "Instalar apps desconocidas" en `emulator-5554` y validar F-35~~ **hecho** (19 de septiembre).
- ~~F-27~~ **hecho** (19 de septiembre), con el permiso revocado y concedido por el usuario.

- Validar en un teléfono Samsung las apps de Good Guardians desde APKMirror y la primera actualización con Galaxy Store como instalador registrado.
- Google Play: el acceso anónimo funciona con un servidor configurado por el usuario (probado en API 23). Falta probarlo en el S22 y descargar una versión concreta por su código.
- ~~Publicar la rama~~ **hecho** (19 de septiembre): la rama es ahora `main` en GitHub, en `5bed473`.

Nota de proceso: `connectedDebugAndroidTest` desinstala la app al terminar y borra sus datos (biblioteca, preferencias, mapeos de APKMirror). En esta sesión se perdieron así los datos de prueba de `emulator-5554`. Los tests instrumentados se ejecutan solo en `emulator-5556` (API 23).

## Decisiones pendientes

Decisiones de producto que el trabajo desatendido no toma. Cada una lleva las opciones y una recomendación.

### D-01 · Búsqueda por nombre en fuentes web (de F-15)

Hoy Buscar encuentra por nombre solo las apps instaladas; en la web necesita el nombre de paquete. Opciones:

1. **Búsqueda de APKMirror por nombre.** Muestra apps y releases, pero no el paquete: habría que abrir una página de variante por resultado para confirmarlo (unas 2 peticiones más por resultado, dentro del límite de Cloudflare ya documentado).
2. **Búsqueda de F-Droid e IzzyOnDroid.** Sus índices incluyen nombre y paquete, pero el índice completo pesa decenas de MB; habría que descargarlo y guardarlo en caché (por ejemplo semanal).
3. **Dejarlo como está.** El uso principal es actualizar apps instaladas, que ya se encuentran por nombre.

Recomendación: 3 por ahora. Si hace falta instalar apps nuevas, empezar por 1 limitada a 5 resultados.

**Decidido (19 de septiembre): opción 3.** Buscar sigue encontrando por nombre solo las apps instaladas.

**Reabierto el mismo día: opción 1.** El usuario pidió encontrar apps no instaladas, para instalar en un dispositivo antiguo la última versión que aún admite su Android. Implementado como F-54.

### D-02 · Material 3 Expressive requiere una versión alfa (de F-39/F-41)

En Material 3 1.4.0, el que fija el BOM `2026.08.00`, `MaterialExpressiveTheme`, `MotionScheme` y los componentes expresivos (botones con forma variable, barras de herramientas flotantes, indicadores de carga) son internos. Solo son públicos en la línea 1.5.x, hoy en alfa. Opciones:

1. **Fijar `androidx.compose.material3:material3` en la 1.5 alfa** por encima del BOM. Da acceso a M3E completo, con API experimental que puede cambiar entre alfas y con el riesgo de una dependencia no estable en la app.
2. **Esperar a la 1.5 estable** y mientras tanto aplicar M3E con lo estable: esquema de color (hecho), formas y tipografía propias, jerarquía y componentes estándar.
3. **Recrear a mano** algunos patrones expresivos con componentes estables. Da más código propio que mantener y que tirar cuando llegue la versión estable.

Recomendación: 2. Coincide con la vocación de la app (estabilidad en dispositivos antiguos) y no bloquea F-42, F-43 ni F-40.

**Decidido (19 de septiembre): opción 2.** Se sigue con Material 3 estable; la parte expresiva de F-39 espera a la 1.5 estable.

### D-03 · Textos a `strings.xml` (de F-28)

Todos los textos de la interfaz están en el código Kotlin (varios cientos). Opciones:

1. **Migrarlos ahora a `res/values/strings.xml`.** Permite traducir y probar textos, pero es un cambio amplio y mecánico que toca todas las pantallas y choca con el trabajo de UI en curso.
2. **Migrar pantalla por pantalla** cuando cada una se rehaga (F-44, D-02), dejando las nuevas ya con recursos.
3. **Posponerlo** hasta que haya un segundo idioma.

Recomendación: 2. Evita conflictos y reparte el coste.

**Decidido (19 de septiembre): opción 2.** Cada pantalla que se rehaga pasa sus textos a `strings.xml`; las nuevas nacen con recursos.

## Resumen de la sesión desatendida (19 de septiembre, 00:41–08:45)

Rama `codex/version-catalog`. Emuladores: `emulator-5554` (API 36), `emulator-5556` (API 23) y, al final, `Frank_API36_Tablet`. `scripts/verify.ps1` correcto tras cada cambio de código.

**Pendientes cerrados (15):**

- P1: F-09 (no reproducible: artefacto de medición), F-45 (TLS en Android ≤ 7.1: ISRG Root X1).
- P2: F-15 (buscar apps instaladas por nombre), F-17 (progreso de la comprobación y recarga automática), F-19/F-25 (una sola selección), F-20 (Inventario abre en "Usuario"), F-21 (cabecera desplazable y acciones fijas), F-30 (acción según la versión instalada), F-40 (lista de versiones en tarjetas), F-41 (esquema de marca y color dinámico, parte estable), F-42 (iconos Material, etiquetas completas, barra superior más baja), F-43 (Inventario compacto).
- P3: F-22 (iconos de apps), F-24 (FrankUpdater fuera de su propia comprobación), F-35 (implementado; falta validar tras instalar).
- Mantenimiento: `UPSTREAMS.yml` con la revisión de septiembre; recorrido de referencia en API 23.

**Hallazgos nuevos:** F-41 a F-44 (división de F-39 por pantalla), F-45 (P1, cerrado) y F-46 (P3, diseño ancho en tableta, solo registrado).

**Decisiones pendientes:** D-01 (búsqueda por nombre en fuentes web), D-02 (Material 3 Expressive requiere la alfa 1.5) y D-03 (migración a `strings.xml`).

**Bloqueos y lo que requiere a una persona:**

- El permiso "Instalar apps desconocidas" se perdió en `emulator-5554` al reinstalar la app en un test instrumentado. Hay que concederlo de nuevo para validar F-35 y para probar F-27.
- F-33 (feedback de la web asistida): el progreso ya se muestra por el cambio de F-02, pero no se pudo recorrer el flujo en el emulador porque estaba saturado.
- Recorrido completo en tableta: repetir con solo ese emulador abierto.
- Validación en un teléfono Samsung (Good Guardians) y publicación de la rama.

**Lecciones de proceso:** los tests instrumentados desinstalan la app y borran sus datos, así que se ejecutan solo en API 23. Con tres emuladores abiertos, el sistema de los emuladores entra en "isn't responding".

**Commits (19):** `0d1723e`, `bb3c2aa`, `3eb74f0`, `5ba4d17`, `e9a902e`, `b9cc2ba`, `05fb40e`, `54aa65b`, `177c162`, `f44589c`, `1342973`, `4bcaeb7`, `64e135b`, `c953d47`, `668ff6d`, `943168b`, `866f91b`, `4d9b85a`, `8d5f66f` (más el de este resumen).
