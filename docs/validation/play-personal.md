# Cuenta personal de Google Play

Fecha: 17 de septiembre de 2026.

FrankUpdater ahora ofrece un modo personal opcional basado en el contrato de
GPlayApi: el usuario introduce su correo y un AAS token generado por un flujo
compatible con Aurora. No se solicita ni se almacena la contrasena.

El token se cifra con una clave del Android Keystore y puede borrarse desde la
misma pantalla. FrankUpdater no inspecciona ni extrae automaticamente cuentas o
tokens de Google Play Services o microG.

Como alternativa sin compartir tokens con FrankUpdater, cada aplicacion de Play
puede abrirse mediante un Intent dirigido a Play Store. En ese caso Play Store
mantiene su propia sesion y realiza la instalacion, sin pasar el APK por la
biblioteca ni por el verificador de FrankUpdater.

El dispenser anonimo continua siendo configurable y opt-in. Un HTTP 403 del
dispenser no activa el modo personal automaticamente: el cambio de cuenta debe
ser una accion explicita del usuario.
