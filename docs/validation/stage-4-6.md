# Catálogo y proveedores web: avance verificable

Continuación desde `058dfb3`, descendiente de `8afd186`, en
`codex/version-catalog`. El catálogo quedó registrado en `05c50fb`.
El usuario autorizó continuar pese al HTTP 403 del acceso anónimo.
No se ha repetido esa solicitud ni se ha cerrado su validación pendiente.

## Implementación

- Catálogo ordenado por código numérico, sin convertir datos desconocidos en
  compatibilidad confirmada. Prioridad de fuentes solo entre candidatos elegibles.
- Historial de APKPure con todas las variantes, código, SDK, ABI, APK/XAPK,
  tamaño y SHA-256 cuando están disponibles. Una consulta nueva reemplaza los
  datos anteriores de esa fuente para evitar duplicados por URLs temporales.
- Historial y variantes de APKMirror, con resolución de descarga separada del parser.
  Los códigos conocidos se incorporan al catálogo; los ausentes se solicitan al usuario.
- Solicitud en Play de códigos históricos encontrados en otras fuentes.
- Descarga a almacenamiento privado y pipeline existente de verificación. El flujo
  directo y el asistido comprueban tamaño/hash anunciados; la importación por SAF
  aplica las mismas expectativas de identidad, versión, tamaño y hash.
- WebView sin puente JavaScript ni acceso a archivos locales, navegación limitada
  a dominios de la fuente y bloqueo de contenido mixto. Cookies solo para la URL
  inicial de la descarga; no se propagan a redirecciones. Fallback a navegador/SAF.
- Catálogo de la sesión, no un historial completo ni persistente. La UI permite
  mostrar más versiones, sin limitar la elección a las primeras cincuenta.

## Evidencia

Primera ejecución conjunta: `test lintDebug assembleDebug
:app:assembleDebugAndroidTest`, un worker y heap de 1536 MiB, terminó correctamente
en 9 min 45 s. Lint: cero errores, dieciséis advertencias. Las advertencias nuevas
incluyen JavaScript deliberadamente habilitado para la navegación asistida y la
comprobación conservadora de espacio disponible.

Las 24 pruebas de compatibilidad incluyen seis casos del catálogo. Los cinco tests
de fuentes cubren los contratos HTML/JSON, ABI múltiples, códigos numéricos,
respuestas rechazadas, dominios engañosos, redirecciones y archivos incompletos.
Los fixtures son sintéticos; no prueban por sí solos el servicio en vivo.

Las consultas ligeras del 6 de septiembre devolvieron HTTP 200 para el historial
público de Fossify Calculator en APKMirror y para `version_list` de APKPure.
APKPure devolvió código 10, SDK mínimo 26, target SDK 36, variantes ARM de XAPK,
tamaño y `file_sha256`. No se descargó ni instaló esa aplicación. La respuesta
temporal no se conserva en el repositorio ni se usa como fixture de regresión.

El 16 de septiembre se reanudó revisando los resultados anteriores antes de repetir
trabajo. Se añadieron verificación de hash/tamaño, actualización de datos por fuente
y acceso a las versiones restantes. La validación conjunta final terminó correctamente
en 6 min 35 s: 62 pruebas JVM, cero fallos y cero errores; lint sin errores y nueve
advertencias; APK de aplicación y de instrumentación ensamblados. Log local:
`build/stage4-6/verify-final.txt`.

APK debug de ese checkpoint: 16.416.264 bytes; SHA-256
`a97b8669d11ed105272dd9f33dcdc43dab6993c0b6795d7cfb937e0b198d4fc1`.

Una sola prueba offline en `Frank_API23_Phone` pasó en 4,776 s: abrió Buscar,
mostró las fuentes web, ejecutó jsoup en Android 6 y comprobó acceso a la sección
de Play. Resultado `OK (1 test)` en `build/stage4-6/api23-smoke.txt`. Gradle había
terminado antes de iniciar el emulador; se cerró este al terminar la prueba.
No se inició Android Studio ni se solicitaron cuentas o descargas de terceros.

## Comprobaciones reales desde JVM, 16 de septiembre

`WebSourcesLiveTest` se ejecuta únicamente con `FRANK_LIVE_WEB=1`. Las ejecuciones
normales omiten sus dos casos y no solicitan metadatos ni descargan aplicaciones.
La ejecución explícita terminó en 2 min 35 s, con dos casos aprobados y ninguno
omitido. No se abrió ningún emulador.

- APKPure: `org.fossify.math`, código 10, XAPK de 6.742.497 bytes. Se comprobó el
  tamaño y SHA-256 anunciados, se extrajeron tres APK y se verificaron sus firmas
  con apksig para SDK 26, sus manifiestos y la coherencia de package/versión/firmante.
  Se usó un perfil de prueba ARM64/SDK 26; no fue una instalación en Android.
  Todos los archivos temporales se eliminaron después.
- APKMirror: tres releases y dos variantes. La prueba inicial mostró cero códigos
  numéricos reconocidos. La tabla pública sitúa el código en `.colorLightBlack`,
  separado del nombre, lo que se corrigió con un fixture reducido y un test de
  regresión. La etiqueta «Android 8.0+» se interpreta como SDK 26; las etiquetas
  desconocidas siguen sin confirmarse. El release observado se anuncia como Beta/
  Early Access; no se debe presentar como una publicación estable.

OkHttp emitió una advertencia de detección de `android.util.Log` en el entorno de
tests JVM y continuó con su plataforma JVM. No hubo fallos ni mocks del transporte,
del hash o de apksig. El log local es `build/stage4-6/live-web.txt`.

La corrección de APKMirror pasó cinco tests de regresión y su prueba real: tres
releases, dos variantes y dos códigos numéricos. No se repitió la descarga de
APKPure. Se ensambló el APK actualizado; ejecución total de 2 min 26 s, sin emulador
(`build/stage4-6/mirror-fix.txt`). APK actual: 16.416.264 bytes, SHA-256
`3bad47962a5f7e6aaa6ac38b999e0b566c7ffd141d027e34dbc06cf7367163ac`.

El indicador de pruebas reales es una entrada explícita de Gradle y se entrega al
proceso de tests. Las ejecuciones reales no reutilizan resultados de caché; cambiar
el indicador invalida los resultados anteriores. Esto evita confundir un resultado
anterior con una nueva consulta o heredar accidentalmente una ejecución real.
La comprobación sin indicador terminó en 1 min 45 s con ambos tests omitidos,
cero fallos y sin salida de consultas/descargas (`build/stage4-6/live-disabled.txt`).

Para repetir estas comprobaciones de forma deliberada, configurar `FRANK_LIVE_WEB=1`
solo para la invocación y ejecutar `:app:testDebugUnitTest --tests '*WebSourcesLiveTest'`.
No añadir esa variable a CI ni a la configuración habitual.

## Pendientes para el cierre de las etapas

Las etapas 4 a 6 permanecen abiertas: falta demostrar la descarga real de APKMirror
y el fallback asistido. La descarga de APKPure y su verificación independiente de
Android sí están demostradas. Los metadatos parciales no permiten
afirmar automáticamente cuál es la última versión compatible; falta completar los
requisitos necesarios para esa decisión, ni separar todavía canales estables/beta.
El catálogo no enumera páginas de historial
que todavía no se consultaron. Las etapas 7 y 8 no están implementadas en este cambio.

No se repiten las pruebas en vivo de inventario ni de instalación de las etapas 1 y 2.
Las próximas pruebas deben concentrarse en esos pendientes, no reiniciar el proyecto.
