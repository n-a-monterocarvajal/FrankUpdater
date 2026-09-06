# ADR 0004: Google Play con acceso anónimo configurable

- Estado: aceptado para implementación; servicio vivo pendiente de validación.
- Fecha: 2026-09-06.

## Decisión

Consumir `com.auroraoss:gplayapi:3.6.4` desde Maven Central. El artefacto declara
API mínima 21; FrankUpdater conserva API mínima 23. El proveedor queda en `app/play`
porque la dependencia es Android. No se copia el protocolo Protobuf ni se crea un
módulo vacío para una sola implementación.

El usuario priorizó acceso anónimo con servidor configurable. No se incluye URL
predeterminada ni se usa el dispensador de otra aplicación automáticamente. Al
pulsar Conectar, la pantalla explica que el perfil del dispositivo se envía al
servidor seleccionado y a Google. Se conserva únicamente la dirección HTTPS; la
sesión y sus credenciales quedan en memoria y se descartan al salir de Buscar.

El usuario autorizó usar `https://auroraoss.com/api/auth` para la validación de
desarrollo. También puede introducir esa dirección voluntariamente en Buscar.
El campo inicial continúa vacío. El mantenedor de Aurora contempla esta elección
explícita y pide no ofrecer su servidor como backend predeterminado de otros
proyectos: [petición del 28 de mayo de 2026](https://github.com/thejaustin/ObtainiumPlus/issues/215).
La disponibilidad del servicio no está garantizada.

El contrato del dispensador sigue `AuthProvider.buildAnonymousAuthData` de Aurora:
POST JSON de propiedades nativas y respuesta con cadenas `email` y `auth`.
`AuthHelper.Token.AUTH` realiza check-in y configura la sesión anónima. No se
solicitan contraseñas, no se almacenan tokens y no se inicia sesión por abrir la
aplicación. No hay reintentos automáticos de autenticación.

El transporte de metadatos implementa el contrato público `IHttpClient` con el
OkHttp ya requerido por GPlayApi. Exige HTTPS, limita las respuestas a 8 MiB,
deshabilita redirecciones de autenticación, establece tiempos de espera y evita
registrar URLs, cabeceras o respuestas privadas. Las credenciales del dispensador
se limitan a 64 KiB al interpretarlas. Se activa la cabecera de limitación de
seguimiento publicitario.

El perfil usa SDK, ABI, densidad, idiomas, features, bibliotecas y datos de build
reales. Solo las versiones del cliente de protocolo GSF/Vending proceden del
recurso publicado por GPlayApi. No se suplanta un Pixel para seleccionar APK.
Las extensiones EGL aún no se enumeran; la ausencia puede reducir disponibilidad.

## Descarga y biblioteca

La búsqueda y los detalles no adquieren ni descargan aplicaciones. Descargar a
biblioteca solicita explícitamente delivery de una aplicación gratuita por
package y `versionCode`, incluida una versión manual. La disponibilidad histórica
depende de que Play siga sirviendo ese código; no se promete un catálogo histórico.

La primera integración acepta base y splits con SHA-256 y tamaño suministrados.
OBB y parches se rechazan explícitamente. Los archivos se descargan uno a uno,
con límites y comprobación de espacio, a nombres privados derivados del hash.
Los redirects de archivos deben conservar HTTPS y no llevan credenciales.

Un parcial se reanuda mediante Range. Un 206 debe declarar el tramo exacto; un 200
reinicia el archivo. Tamaño y SHA-256 se comprueban antes de completar. Un 401,
403 o 410 conserva el parcial y pide repetir la acción; el nuevo intento solicita
delivery otra vez, de modo que puede renovar URLs caducadas. No se persisten URLs
firmadas. Los parciales viven en caché y Android puede eliminarlos.

Base y splits forman un APKS local que recorre el mismo extractor y verificador
que SAF, con package y versión esperados. La biblioteca registra `direct-play`
y la ficha pública como origen. Instalar requiere abrir Biblioteca y continuar
por el Session Installer existente, incluida la confirmación de Android.

## Límites de la decisión

El spike permite continuar con GPlayApi como dependencia; no acredita todavía
éxito contra un servidor real. La etapa 3 permanece abierta hasta validar acceso,
búsqueda, detalles y descarga reales con un servidor configurado por el usuario.
Las pruebas locales de transporte no equivalen a comprobar disponibilidad de Play.
El primer intento autorizado con Aurora devolvió HTTP 403; el detalle está en
[la validación voluntaria](../validation/play-live-test.md).

Se muestra la versión ofrecida por Play, no «la última compatible». La selección
histórica multifuente pertenece a la etapa 4. Esta integración no transforma un
APKS con variantes arbitrarias en un conjunto compatible; usa el delivery del
perfil nativo y deja la decisión final de instalación a Android.
