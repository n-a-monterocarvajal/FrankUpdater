# Validación de la etapa 3

- Fecha: 2026-09-06.
- Estado: implementación inicial; servicio vivo pendiente, etapa abierta.
- Base: `8afd186`, rama `codex/stage-3-play`.

## Evidencia local

La reconciliación previa quedó en `179779b`, con 42 pruebas JVM aprobadas.
Después de integrar Play se ejecutaron `lintDebug`, `test`, `assembleDebug` y
el ensamblado de la prueba Android con un worker, heap de 1536 MiB y Kotlin en
el mismo proceso. Android Studio permaneció cerrado.

- 51 pruebas JVM: cero fallos, cero errores.
- Las nueve nuevas pruebas cubren contrato anónimo, URL HTTPS, rechazo de
  redirecciones de metadatos, conjunto de delivery, Range 206, reinicio ante 200,
  tramo incorrecto, hash alterado, URL caducada y downgrade de HTTPS.
- El transporte de pruebas usa interceptores de OkHttp con respuestas locales.
  No hay llamadas a Google Play ni dispensadores en los tests JVM.
- Lint: cero errores; 13 advertencias sobre versiones disponibles, preferencias
  KTX y consulta de espacio libre. No se actualizó el conjunto de herramientas
  durante este cambio.
- APK debug generado: 14.732.348 bytes.
- SHA-256: `43878d47a404158de9eb90b49326205937a5363734add52d17cdce472c0d715c`.

La invocación agrupada tardó 8 min 42 s. Las tareas de aplicación, lint y tests
terminaron correctamente, pero la compilación de la prueba Android encontró una
referencia directa al tipo Protobuf transitivo, no expuesto en su classpath.
Se corrigió la aserción para comprobar ABI en las propiedades nativas, manteniendo
la construcción real de DeviceInfoProvider. El ensamblado exclusivo del APK de
pruebas corregido terminó con `BUILD SUCCESSFUL` en 3 min 20 s; no se repitió toda
la compilación. Queda una advertencia de migración futura de la regla Compose de
pruebas a su API v2.

Otros ajustes del spike: DeviceManager es interno en GPlayApi; se usa el recurso
Android publicado mediante R.raw. La primera invocación offline necesitó obtener
kotlinx-coroutines-test 1.11.0, requerido por el grafo actualizado. Ninguno de estos
fallos se clasificó como problema del dispositivo o de autenticación.

## Prueba de plataforma

Se ejecutó una única prueba en `Frank_API23_Phone`, después de terminar Gradle:
`PlayIntegrationSmokeTest.nativeProfileAndAnonymousScreenLoadWithoutNetwork`.

- Resultado: `OK (1 test)`, 2,949 s.
- La dependencia construyó DeviceInfoProvider con el SDK y las ABI nativas.
- Buscar mostró «Acceso anónimo» y «Conectar».
- No se configuró servidor ni se solicitaron credenciales.
- Log local: `build/stage-3/api23-smoke.txt` (artefacto no versionado).
- Al recuperar la sesión tras la interrupción se leyó ese resultado antes de
  ejecutar nada. No se repitió el test. No quedaron procesos de emulador ni Java.

No se repitieron instalación de paquetes reales ni validación en API 36: esta
pasada solo comprueba la nueva dependencia y la pantalla inicial en la API mínima.

## Pendiente para cerrar la etapa

El usuario autorizó posteriormente usar `https://auroraoss.com/api/auth` de forma
voluntaria para la prueba real. La aplicación mantiene el campo vacío por defecto.
El procedimiento opt-in está en [prueba real de Play](play-live-test.md).
El intento real en API 23 recibió HTTP 403 durante el acceso anónimo; una consulta
HEAD al dispensador también recibió 403 desde Cloudflare. No se reintentó la
autenticación. El emulador se cerró. Por tanto no se consideran demostrados
acceso anónimo real, búsqueda contra Play, ficha actual ni descarga de una versión
servida realmente. La decisión del spike es continuar la implementación, con el
go operativo condicionado a esa demostración.

Los resultados de las etapas 1 y 2 se conservan y no se presentan como repetidos.
No se abre la etapa 4 mientras falte la validación de la etapa 3.
