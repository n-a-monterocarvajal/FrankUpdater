# Instaladores opcionales y comprobaciones periódicas

Fecha: 17 de septiembre de 2026.

El instalador del sistema sigue siendo el valor inicial. Los métodos privilegiados
requieren selección explícita y permisos del dispositivo; la comprobación periódica
no concede permisos, no obtiene sesiones anónimas ni instala paquetes.

El router toma como referencia `InstallManager`, `ShizukuInstaller` y `RootInstaller`
de Droid-ify en `ff1453ed957f3b2382abce9a7eb9a9db8def3a3a` (GPL-3.0-only).
Se reutiliza el protocolo de sesión de Android: create, write de cada APK y commit.
No se adopta la interpolación de nombres de archivo en comandos de shell ni el
flag de aceptar paquetes de prueba. Los APK llegan por stdin o descriptor abierto.

Shizuku se integra mediante su UserService público. `newProcess` ya no es una API
pública en la referencia revisada; no se usa reflexión para recuperarla.
Root usa el mismo protocolo de sesión mediante `su`. Legacy solo admite APK
monolíticos y delega la confirmación al instalador de Android.

Los errores posteriores a enviar un commit no provocan una segunda instalación
automática. El fallback por indisponibilidad ocurre antes de crear una sesión.
Los lotes se limitan a métodos que pueden confirmar cada resultado; el sistema
mantiene confirmación individual. La política de retención se aplica únicamente
después de un resultado exitoso.

WorkManager programa comprobaciones opt-in. Los hallazgos de metadatos parciales
se muestran como versiones por verificar; no se anuncian como actualizaciones
compatibles ni se instalan sin pasar por el pipeline común.
