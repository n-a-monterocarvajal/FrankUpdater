# Plan operativo para agentes IA

## Estado

- Etapa 0 — Fundación: completada y validada.
- Etapa 1 — Inventario y compatibilidad local: siguiente etapa activa.
- Etapas 2 a 8: pendientes; no adelantarlas salvo que sean necesarias para diseñar una interfaz estable de la etapa activa.

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
