# Prueba voluntaria de Google Play

La suite normal no contacta servidores anónimos. `PlayLiveTest` se omite salvo
que se proporcione explícitamente el argumento `playDispenser`.

Para la validación de este proyecto, el usuario eligió un servidor de terceros.
Su dirección no forma parte de los valores predeterminados de la aplicación: la
introduce quien la usa, y no se documenta aquí.

Con el APK y el runner de pruebas instalados en el único emulador activo:

```powershell
adb -s emulator-5580 shell am instrument -w `
  -e class io.github.n_a_monterocarvajal.frankupdater.play.PlayLiveTest `
  -e playDispenser <dirección del servidor> `
  io.github.n_a_monterocarvajal.frankupdater.test/androidx.test.runner.AndroidJUnitRunner
```

El test usa el perfil nativo, crea una sesión, busca Fossify Calculator, consulta
`org.fossify.math`, solicita su versión actual y descarga como máximo 25 MiB.
Verifica los APK mediante el pipeline real y comprueba la biblioteca. Elimina
solo la entrada creada por la prueba y sus temporales. No instala la calculadora.

No imprime cuentas, tokens, URLs firmadas ni cuerpos de respuesta. Ante un fallo
registra únicamente la etapa, clase de excepción y último código HTTP. No
reintenta autenticación automáticamente ni solicita varias cuentas.

## Resultado del intento autorizado

- API 23, `Frank_API23_Phone`, un único intento.
- Runner preparado con `BUILD SUCCESSFUL` en 1 min 7 s, cuatro tareas ejecutadas
  y 52 reutilizadas. El APK de la aplicación no cambió.
- La prueba llegó a «acceso anónimo» y terminó en 1,882 s con `IOException`,
  último código HTTP 403. No alcanzó búsqueda, detalles ni descarga.
- Una consulta HEAD posterior al dispensador, sin solicitar credenciales,
  también recibió 403, `Server: cloudflare` y contenido HTML. No incluyó
  `cf-mitigated` ni `Retry-After`. Esto no permite distinguir una regla de acceso,
  bloqueo de IP u otra política; no se atribuye una causa concreta.
- No se reintentó la solicitud, ni se cambió de IP, ni se pidieron nuevas
  sesiones. Se cerró el emulador.
- Evidencia local: `build/stage-3/api23-play-live.txt`.

La dirección elegida queda documentada para configuración voluntaria. La etapa 3
permanece abierta: esta prueba demuestra un rechazo del servicio, no un flujo de
Play exitoso. Reanudar la validación cuando el servidor acepte la solicitud o el
usuario elija otro dispensador compatible.
