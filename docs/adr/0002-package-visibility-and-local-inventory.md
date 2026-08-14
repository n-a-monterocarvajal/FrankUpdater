# ADR 0002: Visibilidad de paquetes e inventario local

- Estado: aceptado
- Fecha: 2026-08-14

## Contexto

FrankUpdater necesita comparar las aplicaciones instaladas con catálogos de actualización. Desde Android 11, `PackageManager` filtra los resultados según la visibilidad de paquetes de la aplicación. Una lista estática de elementos `<queries>` no puede representar el conjunto abierto de paquetes que un actualizador debe descubrir.

FrankUpdater se distribuye mediante GitHub Releases, F-Droid y otras plataformas libres, no mediante Google Play. Aun así, el permiso debe limitarse a la función principal declarada y explicarse con claridad.

## Decisión

- Declarar `android.permission.QUERY_ALL_PACKAGES` para que el inventario sea completo en Android 11 y posteriores.
- Consultar el inventario mediante `PackageManager.getInstalledPackages` con el flag de certificados apropiado para cada API.
- Incluir aplicaciones de usuario, aplicaciones del sistema, actualizaciones de aplicaciones del sistema y aplicaciones deshabilitadas. La interfaz debe identificarlas; no se descartan silenciosamente.
- Conservar localmente por aplicación: nombre visible, nombre de paquete, versión, `versionCode`, historial de certificados SHA-256, rutas de splits, fuente instaladora cuando Android la expone, estado de sistema y estado habilitado.
- Ejecutar la enumeración y transformación fuera del hilo principal.
- No transmitir el inventario ni persistirlo durante esta etapa. Los providers futuros recibirán solo los datos mínimos necesarios y deberán documentar su tratamiento antes de introducir red o almacenamiento.

## Consecuencias

El inventario puede incluir una cantidad grande de paquetes y datos sensibles sobre el dispositivo, por lo que la UI necesita estados de carga/error y renderizado perezoso. La distribución fuera de Google Play evita depender de su política de permisos, pero no elimina la obligación de transparencia ante las personas usuarias ni la minimización de datos.

Si una plataforma futura rechaza `QUERY_ALL_PACKAGES`, deberá ofrecerse una variante con inventario limitado y una advertencia explícita; no se simulará que la lista filtrada es completa.
