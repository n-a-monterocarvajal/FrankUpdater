# Validación de la etapa 7

Fecha: 17 de septiembre de 2026.

## Resultado

La implementación de instaladores avanzados y comprobaciones periódicas compila y
pasa la validación local. El instalador del sistema sigue siendo el camino
predeterminado; Shizuku, Root y Legacy requieren selección explícita.

Se verificó:

- contrato AIDL del servicio Shizuku con IDs consistentes;
- sesiones privilegiadas con `install-create`, `install-write`, `install-commit`
  y abandono ante errores;
- selección automática Sistema/Shizuku sin fallback implícito a Root;
- comprobaciones periódicas opt-in mediante WorkManager;
- notificaciones condicionadas al permiso de Android 13+;
- compatibilidad de la dependencia Shizuku `12.2.0` con `minSdk 23`.

## Evidencia

```text
test lintDebug assembleDebug
BUILD SUCCESSFUL
65 actionable tasks: 43 executed, 22 up-to-date
```

La prueba unitaria de `ShellSessionInstaller` cubre orden de comandos,
transferencia por stdin, rechazo de rutas en shell y abandono de sesiones
fallidas. No se realizó una instalación privilegiada en un dispositivo real;
requiere un dispositivo con Root o Shizuku configurado.

La validación del servicio vivo de Google Play continúa pendiente por el HTTP 403
documentado en la etapa 3.
