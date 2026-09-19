# Plan operativo para agentes IA

## Estado

- Etapa 0 — Fundación: completada y validada.
- Etapa 1 — Inventario y compatibilidad local: completada y validada.
- Etapa 2 — Pipeline local seguro: completada y validada.
- Etapa 3 — Google Play: en desarrollo desde 8afd186, en codex/stage-3-play. Acceso anónimo con servidor configurado por el usuario; sin URL predeterminada. Validación de servicio vivo pendiente.
- Etapas 4 a 6: en desarrollo en codex/version-catalog, descendiente de 8afd186. Catálogo en 05c50fb; proveedores web, integridad y fallback en validación. Ver ../docs/validation/stage-4-6.md.
- Etapas 7 y 8: pendientes.

Cada etapa debe terminar con tests automatizados, una demostración verificable y documentación suficiente antes de iniciar la siguiente.

## Etapa 1 — Inventario y compatibilidad local

Objetivo: implementar inventario local y evaluación de compatibilidad sin descargas ni instalación de paquetes.

Entregables:

- `InstalledAppRepository` basado en Android `PackageManager`;
- modelos separados `GenericDeviceProfile` y `PlayDeviceProfile`;
- adaptación trazable del `CompatibilityChecker` de F-Droid;
- port mínimo del targeting de bundletool;
- tests de compatibilidad para ABI, densidad y SDK;
- representación inicial del inventario en la interfaz.

La estrategia de visibilidad de paquetes debe respetar las restricciones modernas de Android y quedar justificada en un ADR o en la documentación técnica correspondiente.

### Criterios de cierre

- El inventario real se obtiene en un emulador compatible y se presenta sin bloquear la interfaz.
- Las decisiones de visibilidad, datos recolectados y tratamiento de aplicaciones del sistema están documentadas.
- Los perfiles genérico y Play no comparten supuestos incompatibles.
- El checker produce resultados deterministas cubiertos por tests ABI/DPI/SDK.
- `lint`, tests y ensamblado debug finalizan correctamente.
- Se valida como mínimo en un teléfono API 23 y en un dispositivo moderno con diseño adaptativo.

### Registro de cierre

- `InstalledAppRepository` enumera el inventario completo mediante `PackageManager` fuera del hilo principal.
- El permiso y tratamiento local de datos están justificados en `../docs/adr/0002-package-visibility-and-local-inventory.md`.
- `GenericDeviceProfile` no contiene propiedades de Play; `PlayDeviceProfile` lo compone y mantiene sus propiedades específicas separadas.
- El checker base adapta F-Droid y el targeting ABI/multi-ABI/DPI/SDK porta el subconjunto esencial de bundletool con tests de paridad.
- `lintDebug`, `test` y `assembleDebug` finalizaron correctamente.
- El inventario y la UI se demostraron en `Frank_API23_Phone` y `Frank_API36_Tablet`; el detalle reproducible está en `../docs/validation/stage-1.md`.

## Etapa 2 — Pipeline local seguro

Objetivo: importar, verificar, instalar y retener paquetes locales sin confiar en nombres de archivo ni rutas externas revocables.

Entregables:

- importación mediante SAF con copia previa a almacenamiento privado;
- lectura segura y unificada de APK, APKS, APKM moderno y XAPK;
- verificación de package, versión, integridad, firma y continuidad con la aplicación instalada;
- instalación del APK base y todos sus splits en una sola `PackageInstaller.Session`;
- biblioteca privada con metadatos atómicos, reutilización, compartir y eliminar;
- políticas persistentes de retención: eliminar, preguntar y conservar siempre.

### Criterios de cierre

- Los cuatro formatos recorren el mismo pipeline y los contenedores inválidos o peligrosos se rechazan antes de instalar.
- Un paquete real con APK base y splits se verifica completo y se escribe en una sola sesión.
- La sesión finaliza con éxito mediante el flujo autorizado del sistema, sin eludir la protección de orígenes desconocidos.
- La biblioteca conserva únicamente el original verificado y aplica las tres políticas de retención.
- `lint`, tests y ensamblado debug finalizan correctamente.
- Se valida como mínimo en un teléfono API 23 y en un dispositivo moderno con diseño adaptativo.
- La evidencia distingue defectos reproducibles, limitaciones del entorno y resultados no concluyentes.

### Registro de cierre

- La implementación, los tests automatizados, SAF, los cuatro formatos, firma, integridad, biblioteca, retención y UI adaptable están verificados.
- `lintDebug`, `test` y `assembleDebug` finalizaron correctamente.
- Las pruebas instrumentadas pasaron en `Frank_API23_Phone` y `Frank_API36_Tablet`.
- Un APKS real de `com.android.vending`, con base y seis splits, fue importado, verificado e instalado correctamente mediante una sola sesión en API 36.
- Android mostró la confirmación de actualización, FrankUpdater recibió el resultado exitoso y `pm path` confirmó las siete rutas instaladas.
- La política `Preguntar` conservó el original verificado y mantuvo las acciones de biblioteca después de instalar.
- El permiso temporal `REQUEST_INSTALL_PACKAGES` se aplicó únicamente tras autorización explícita y se devolvió a `default` al terminar.
- El detalle reproducible y la clasificación de incidencias están en `../docs/validation/stage-2.md`.

## Reglas permanentes

- Mantener `minSdk 23` mientras la especificación no cambie.
- Trabajar por etapas y con commits locales pequeños e intencionales.
- No hacer `push`, publicar releases ni abrir PR sin autorización explícita.
- FrankUpdater no se distribuirá mediante Google Play. Los canales previstos son GitHub Releases, F-Droid y otras plataformas libres. La posible consulta de Google Play como fuente de artefactos es una cuestión distinta y se rige por la especificación y el roadmap.
- No introducir telemetría, cuentas obligatorias ni servicios propietarios para funciones básicas.
- Respetar licencias y registrar toda adaptación o port de upstream en [`../UPSTREAMS.yml`](../UPSTREAMS.yml).
- Preservar cambios del usuario y evitar operaciones destructivas sobre el repositorio.
- Los textos de navegación son nombres de producto: `Actualizaciones` debe conservarse completo semánticamente y usar elipsis visual cuando no quepa.

## Validación habitual

Desde la raíz del repositorio:

```powershell
.\gradlew.bat --no-daemon lintDebug test assembleDebug
```

Las pruebas visuales en emulador complementan, pero no sustituyen, los tests automatizados.

## Continuación verificada, 6 de septiembre de 2026

- Base correcta: `8afd186`, etapas 1 y 2 cerradas. `main` estaba retrasado.
- No repetir la implementación de `codex/local-inventory` (`60531f6`). Su trabajo pendiente quedó en el stash `recovery: duplicated stage 2 before resuming 8afd186`.
- Rescate selectivo registrado en `179779b`; 42 pruebas JVM aprobadas. Ver `../docs/validation/branch-reconciliation.md`.
- Etapa 3: GPlayApi 3.6.4, contrato de acceso anónimo, búsqueda, detalles, delivery por versión y descarga secuencial con SHA-256, Range y renovación en el siguiente intento.
- Los APK descargados recorren el pipeline existente y se conservan en biblioteca. No hay instalación automática.
- El usuario eligió servidor anónimo configurable, con un User-Agent opcional para el servidor que lo exija. La dirección la pone quien usa la aplicación; no se propone ninguna. Mantener el campo vacío por defecto y no solicitar sesiones repetidamente.
- Mantener la etapa 3 abierta hasta validar el servicio vivo. No afirmar última versión compatible: pertenece a la etapa 4.
- Revisar `../docs/adr/0004-google-play-anonymous-provider.md` y `../docs/upstreams/stage-3-sources.md`.

## Recursos de la estación

Usar `scripts/verify.ps1`: un worker, heap de 1536 MiB y Kotlin en el mismo proceso.
Agrupar tests, lint y ensamblado. No abrir Android Studio. Ejecutar como máximo un
emulador a la vez, una vez terminado Gradle, solo para cambios que necesitan Android.
No repetir validaciones en vivo de etapas cerradas por cambios de documentación o lógica JVM.

Validación inicial de etapa 3: 51 pruebas JVM sin fallos; lint sin errores y APK debug ensamblado. Una prueba API 23 aprobada en 2,949 s; ver ../docs/validation/stage-3.md. No hay emuladores activos. Servidor Aurora autorizado para probar el flujo real; no cerrar etapa 3 ni avanzar a etapa 4 todavía.


Intento real autorizado contra un servidor de terceros: una prueba API 23 terminó en 1,882 s con HTTP 403 durante acceso anónimo; HEAD al dispensador también devolvió 403 (Cloudflare). Sin nuevas sesiones ni evasión del rechazo. Emulador cerrado. No atribuir causa concreta ni afirmar éxito de búsqueda/descarga. Ver ../docs/validation/play-live-test.md. Etapa 3 abierta hasta que el servicio acepte la solicitud o el usuario elija otro servidor.


El usuario autorizó continuar con las siguientes etapas pese al rechazo externo. Etapa 4 activa desde 058dfb3, rama codex/version-catalog. La validación real pendiente de etapa 3 queda registrada; esta autorización sustituye la restricción anterior de no avanzar. Priorizar catálogo/selección y fallbacks multifuente con fixtures y pruebas JVM; conservar las comprobaciones en vivo ya válidas.

Continuación del 16 de septiembre: catálogo 05c50fb y proveedores web 0f24980, siempre descendientes de 8afd186. Validación: 62 pruebas JVM, lint sin errores, APK ensamblado y una prueba offline API 23 aprobada en 4,776 s. Dos pruebas reales JVM posteriores aprobaron historial/variantes de APKMirror y XAPK de APKPure (6.742.497 bytes, tres APK con hash, identidad y firma verificados para SDK 26). Se corrigió lectura del código separado en .colorLightBlack; regresión y consulta real comprobaron dos códigos. Pruebas de red solo con FRANK_LIVE_WEB=1; se verificó omisión de ambas sin indicador. No repetir descargas ni emuladores sin cambio que lo justifique. No hay emuladores activos. Consultar ../docs/validation/stage-4-6.md para alcance y pendientes: metadata suficiente para última compatible, descarga/fallback real de APKMirror y luego etapas 7–8. No afirmar cierre completo de 4–6.
