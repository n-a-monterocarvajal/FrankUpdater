# Fuentes de la etapa 3

## GPlayApi

- Repositorio: https://gitlab.com/AuroraOSS/gplayapi
- HEAD revisado: `18ec2bd74995d30e500b756359a4de3e37976f03`.
- Dependencia exacta: `com.auroraoss:gplayapi:3.6.4`, Maven Central.
- AAR SHA-256: `7df649ec98e059b974342d863cc08d088defb3c554e44337f8e2b734d9209242`.
- Sources JAR SHA-256: `8dc2a0af00d1ac7ad77bb56599b021a51ce30a267e5ec70eb6fed6b4f32da6e4`.
- Fuentes efectivamente consultadas: AuthHelper, AuthData, DeviceInfoProvider,
  AppDetailsHelper, SearchHelper, PurchaseHelper, PlayFile, App, StreamBundle,
  StreamCluster, IHttpClient, DefaultHttpClient, HeaderProvider y DeviceManager.
- Licencia de esos archivos: GPL-3.0-or-later.

La publicación 3.6.4 no tenía tag del mismo nombre en el listado consultado.
El hash del artefacto y sus fuentes identifica lo consumido; no se afirma que
HEAD sea el commit exacto de publicación. Por eso `last_integrated_commit` queda
en null. El README ofrece un ejemplo con groupId `com.aurora`, pero el POM y
metadata publicados confirman `com.auroraoss`.

Dependencias directas reutilizadas: OkHttp 5.3.2 y Gson 2.14.0, las mismas
versiones que declara el POM de GPlayApi. No se usa el cliente HTTP predeterminado
porque registra URLs y lee respuestas sin límite propio.

## Aurora Store

- Repositorio: https://github.com/AuroraOSS/AuroraStore
- Ref: `660670a35cd6980afaf1f9667b7df2144ddc435c`.
- AuthProvider.buildAnonymousAuthData: adaptación del contrato JSON y Token.AUTH; archivo GPL-2.0-or-later.
- NativeDeviceInfoProvider: adaptación de propiedades, archivo GPL-2.0-or-later;
  se conserva atribución en PlayDeviceProperties.kt.
- No se reutilizan dispensadores predeterminados, credenciales, suplantación de
  Huawei/Pixel, persistencia de cuentas ni aceptación automática de condiciones.

URLs reproducibles de los artefactos:

- https://repo.maven.apache.org/maven2/com/auroraoss/gplayapi/3.6.4/gplayapi-3.6.4.pom
- https://repo.maven.apache.org/maven2/com/auroraoss/gplayapi/3.6.4/gplayapi-3.6.4-sources.jar
