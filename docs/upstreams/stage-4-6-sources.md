# Catálogo y proveedores web

Revisión del 6 de septiembre de 2026. El catálogo no confía en el orden de las
fuentes ni en etiquetas de versión. Ordena códigos numéricos y distingue requisitos
desconocidos de compatibilidad confirmada por metadatos. La firma del archivo sigue
siendo responsabilidad del pipeline local, incluso si una fuente anuncia una firma.

## Selección

Referencia: [F-Droid UpdateChecker](https://github.com/f-droid/fdroidclient/blob/f71539794471a123441de16c775f7ff5ecaef460/libs/index/src/androidMain/kotlin/org/fdroid/UpdateChecker.kt),
licencia Apache-2.0 de `libs/LICENSE`. Se adapta el recorrido descendente y el corte
respecto a la versión instalada. No se adopta la comparación por intersección de
firmantes ni se incorporan canales de lanzamiento que nuestro modelo no representa.
`VersionCatalogTest` comprueba orden, variantes, fuentes ausentes, datos parciales
y ausencia de sugerencias de downgrade.

## APKMirror

Referencia: [APKMD](https://github.com/tanishqmanuja/apkmirror-downloader/tree/b6bba610f180169c0c5f3eceaa6eac0660511eca/src/lib/scrapers),
MIT. Se portan selectores de historial, tabla de variantes y los dos pasos de
resolución de descarga. Se conserva el aviso de licencia en
[apkmd-MIT.txt](licenses/apkmd-MIT.txt).

No se porta `getFilteredVariant`: todas las ABI y variantes permanecen separadas.
Un código de versión ausente no se deduce del nombre del release; se solicita al
usuario para comprobarlo contra el archivo. HTML cambiado, desafíos y enlaces
fuera del dominio se rechazan. El fallback conserva el enlace más profundo conocido.

## APKPure

Referencia: [Obtainium APKPure](https://github.com/ImranR98/Obtainium/blob/aec5dabb3f7b9aa8212f7d228344a4e722d23e89/lib/app_sources/apkpure.dart),
GPL-3.0-only. Se adapta `version_list`, `package_name`, `version_code`, `native_code`
y `asset`, así como los encabezados públicos que describen el SDK del dispositivo.
No se adopta la agrupación por nombre de versión ni la selección de la primera ABI.
No se utilizan claves privadas ni credenciales de APKUpdater.

## Dependencias y límites

[jsoup 1.23.2](https://jsoup.org/download) analiza HTML; su documentación requiere
desugaring NIO en Android, activado con `desugar_jdk_libs_nio:2.1.5` para API 23.
JSON y HTTP reutilizan Gson y OkHttp ya presentes. Las respuestas de metadatos tienen
un límite de 8 MiB; las descargas, 1,5 GiB y reserva de espacio. No se registran cookies
ni enlaces de descarga. Cada redirección comprueba HTTPS y el dominio de la fuente.
Los encabezados de sesión no se reenvían a destinos de redirección.

Los fixtures HTML y JSON son sintéticos, derivados de esos contratos públicos;
no constituyen capturas ni evidencia de funcionamiento actual de los servicios.
Los metadatos parciales nunca se etiquetan como compatibilidad completa.
