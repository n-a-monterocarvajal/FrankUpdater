# Plan operativo para agentes IA

## Estado

- Etapa 0 — Fundación: completada y validada.
- Etapa 1 — Inventario y compatibilidad local: completada y validada.
- Etapa 2 — Pipeline local seguro: activa; implementación terminada y cierre de instalación pendiente.
- Etapas 3 a 8: pendientes; no adelantarlas salvo que sean necesarias para diseñar una interfaz estable de la etapa activa.

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

### Estado de validación

- La implementación, los tests automatizados, SAF, los cuatro formatos, firma, integridad, biblioteca, retención y UI adaptable están verificados.
- `lintDebug`, `test` y `assembleDebug` finalizaron correctamente.
- Las pruebas instrumentadas pasaron en `Frank_API23_Phone` y `Frank_API36_Tablet`.
- Un APKS real de `com.android.vending`, con base y seis splits, fue importado y verificado en API 36.
- El cierre permanece pendiente: Android solicitó habilitar `Allow from this source` para FrankUpdater y no se cambió esa opción persistente sin autorización explícita.
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
