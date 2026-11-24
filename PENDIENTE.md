# PENDIENTE — Estado del trabajo y siguientes pasos

Notas para continuar desde cualquier agente (Claude Code, Codex, Antigravity…). Las reglas generales del
proyecto están en [AGENTS.md](AGENTS.md). **Actualiza este archivo al terminar cada sesión.**

Última actualización: 24/11/2026.

---

## Dónde estamos

- **Publicada la versión 1.2.0** (`versionCode 6`): la rama `imap` se fusionó en `main` y la release `v.1.2.0` está en
  GitHub con `MercaStats-v.1.2.0.apk`, firmada con la clave OneRate.
- Novedades de la 1.2.0: importación por IMAP con contraseña de aplicación, sincronización en segundo plano,
  histórico de precios por producto y **eliminación del inicio de sesión con Google** (API de Gmail con OAuth).
  En la pestaña **Tickets**, el botón principal es «Conectar correo» (abre el formulario IMAP) o «Sincronizar».
- Probado en el Pixel con la debug: la sincronización IMAP al abrir revisa 231 correos sin errores.

## Siguientes pasos

- Nada pendiente pedido por el usuario. Trabaja en ramas nuevas desde `main`.

### Ideas para más adelante (no pedidas todavía)

- Soporte de Outlook/Hotmail con OAuth de Microsoft + XOAUTH2 en Jakarta Mail.
- Pasar la importación de PDFs al mismo esquema de servicio en primer plano.

---

## Firma de la release (decisión tomada)

- El usuario ha decidido firmar con **la clave de la carpeta `C:\Users\Ruben\Documents\claves`** (`onerate-clave`,
  alias `onerate`, certificado `CN=OneRate`), que es la que usa para sus otras apps.
  - SHA-1: `05:2F:AC:CF:C5:D7:F2:3D:4D:F6:D8:BF:EE:DA:CF:19:5C:2D:21:26`
  - SHA-256: `08:0C:C3:81:C5:60:9F:AB:C2:A1:0D:77:39:24:CC:95:1C:66:E4:0D:C2:45:75:34:E6:EC:BA:B2:8E:77:F1:10`
- `local.properties` contiene `RELEASE_KEYSTORE_PROPERTIES=C:/Users/Ruben/Documents/claves/keystore.properties`.
  Dentro de ese archivo, `storeFile` está escrito con «Rubén» (con tilde), una ruta que no existe; por eso el build
  busca el keystore en la misma carpeta que el `.properties`. **No leas ni muestres las contraseñas.**
- **Importante:** las releases hasta la **1.1.3 se firmaron con la clave de depuración** de este PC
  (`C:\Users\Ruben\.android\debug.keystore`, `CN=Android Debug`, SHA-256 `C6:24:74:F8:…:01:41:18`). Por eso:
  - La 1.2.0 **no se instala encima de la 1.1.3**: hay que desinstalar la anterior, lo que borra los tickets
    guardados (avisado en el README y en las notas de la release). Se recuperan volviendo a sincronizar.
- Comprobar la firma de una APK:
  `C:\Android\Sdk\build-tools\<versión>\apksigner.bat verify --print-certs <apk>`.

---

## Entorno y pruebas

- Móvil de pruebas conectado por USB (`adb devices`). La debug instalada es `io.github.neoruben.mercadonastats.debug`
  y tiene una cuenta IMAP configurada con unos 231 tickets. La release no está instalada en ese móvil.
- Si el móvil está bloqueado, pide al usuario que lo desbloquee: no introduzcas su PIN.
- En Git Bash, para los comandos `adb` con rutas del móvil (`/sdcard/…`), usa `MSYS_NO_PATHCONV=1`, o Git Bash
  convierte la ruta.
