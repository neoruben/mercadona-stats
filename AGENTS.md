# AGENTS.md — Guía para agentes de código (Claude Code, Codex, Antigravity…)

Este archivo es la referencia común para cualquier agente que trabaje en el repositorio.
`CLAUDE.md` y `GEMINI.md` solo lo importan: **edita aquí** y no dupliques contenido en ellos.
Para el detalle de cada pieza, consulta [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Lo que ve el usuario está en el [README.md](README.md).

## Qué es

**MercaStats**: app Android (Kotlin + Jetpack Compose, sin backend) que importa los tickets digitales PDF de
Mercadona, los guarda en SQLite en el propio móvil y muestra estadísticas de gasto. Tiene tres vías de importación:

1. **Gmail API** (OAuth, `gmail.readonly`): solo funciona en la versión release, que es la registrada en Google Cloud.
2. **IMAP con contraseña de aplicación** (Jakarta Mail): Gmail, iCloud o cualquier servidor IMAP.
3. **PDF compartido o abierto** desde otra app, o elegido con el selector de archivos.

El remitente de los tickets es `ticket_digital@mail.mercadona.com`.

## Idioma y estilo

- **Todo en español**: textos de la interfaz, mensajes de error, comentarios, documentación y mensajes de commit.
  Los commits recientes usan infinitivo («Importar tickets por IMAP…», «Reescribir syncFromImap…»).
- Los errores que ve el usuario deben ser comprensibles, no técnicos. Para IMAP, pásalos por
  `ImapMercadonaService.friendly()`.
- Estilo Kotlin oficial (`kotlin.code.style=official`), con indentación de 4 espacios.
- Licencia «todos los derechos reservados»: no añadas cabeceras ni licencias de código abierto.

## Estructura

```
app/src/main/java/io/github/neoruben/mercadonastats/
  MainActivity.kt          Estado de la pantalla, Gmail, importación de PDF; lanza la sync IMAP
  data/                    TicketDatabaseHelper (SQLite, instancia única con get())
  gmail/                   Cliente REST de Gmail y OAuth
  mail/                    IMAP: conexión, partes MIME, ajustes y contraseña cifrada (Keystore)
  parser/                  Extracción de texto del PDF (pdfbox-android) y parser del ticket
  stats/                   Agregados por mes, semana y producto
  sync/                    Sincronización IMAP en segundo plano (servicio, importador, estado)
  ui/                      Compose: pestañas y formulario IMAP
app/src/test/              Tests JVM (parser, estadísticas, partes MIME de IMAP)
samples/                   PDFs personales de prueba (ignorados por git, NO subir)
docs/ARCHITECTURE.md       Guía técnica detallada
.github/workflows/         CI: tests + APK debug
```

## Compilar y probar

Entorno de desarrollo (Windows): JDK de Android Studio (`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`)
y SDK en `C:\Android\Sdk`, indicado en `local.properties`.

```powershell
./gradlew :app:testDebugUnitTest      # tests JVM
./gradlew :app:assembleDebug          # APK en app/build/outputs/apk/debug/app-debug.apk
```

- **Ejecuta siempre `assembleDebug`, no solo la compilación de Kotlin.** Algunos fallos solo aparecen al pasar a DEX (ver «Trampas»).
- El wrapper de Gradle (`gradlew`, `gradle/wrapper/`, 9.6.0) existe en local pero **no está versionado**.
  La CI usa Gradle 9.9.2 con `gradle/actions/setup-gradle`. Si añades o actualizas el wrapper, alinea las dos versiones.
- Versiones: AGP 9.5.1, Kotlin (plugin Compose) 2.2.10, compileSdk/targetSdk 35, minSdk 26, Java 17.
- **CI** (`.github/workflows/android.yml`): en cada push ejecuta los tests y genera la APK debug como artefacto
  `MercaStats-debug-apk`. Consulta el estado con `gh run list --branch <rama>`.

### Probar en el móvil

- La versión debug se instala como app aparte (`io.github.neoruben.mercadonastats.debug`), así que no toca la release ni sus tickets.
- Instala con `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Si falla con `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
  la debug instalada está firmada con otra clave (otro PC o la CI). La única salida es desinstalarla, lo que **borra sus datos**:
  pide permiso al usuario antes. **Nunca** desinstales el paquete release.
- Registro: `adb logcat | grep -E "MercadonaImap|MercadonaSync|AndroidRuntime"`.
- Para comprobar el trabajo en segundo plano: `adb shell input keyevent KEYCODE_SLEEP`, y después
  `dumpsys activity services <paquete>`, `dumpsys power | grep MercaStats` y `dumpsys notification --noredact`.
- **No escribas tú la contraseña de aplicación** del usuario en el formulario: la prueba de la cuenta la hace él.

## Reglas y trampas conocidas

### D8 y corrutinas
Con este AGP/Kotlin, D8 falló al procesar una lambda de corrutina que combinaba **llamadas `suspend` dentro de `finally`**
y **`return@launch` cruzando un `try`** (`NullPointerException` / «Invalid stack map table» en `dexBuilderDebug`).
- Evita esa combinación en `launch { … }`.
- Prefiere funciones **bloqueantes normales** ejecutadas en `Dispatchers.IO`, como hace `ImapTicketImporter`, y `runCatching` en lugar de `try/finally` con `suspend`.

### Trabajo largo en segundo plano
- **No uses `lifecycleScope` para sincronizaciones largas de red.** Al apagarse la pantalla, Android cortó la conexión IMAP
  (`FolderClosedException`) y todos los correos restantes fallaron.
- La sync IMAP corre en `sync/ImapSyncService` (servicio en primer plano de tipo `dataSync` con *wake lock* parcial y notificación).
  Publica su estado en `ImapSyncBus` (`StateFlow`), que `MainActivity` observa con `repeatOnLifecycle(STARTED)`.
- `ImapTicketImporter` reconecta ante errores de conexión (hasta 3 seguidos) y vuelve a localizar los mensajes por su id,
  porque los objetos `Message` de una conexión cerrada ya no sirven.
- La sync de Gmail y la importación de PDFs siguen en `lifecycleScope`: si crecen, muévelas al mismo esquema.

### Base de datos
- Usa siempre `TicketDatabaseHelper.get(context)`, que es instancia única compartida por la pantalla y el servicio. No hagas `TicketDatabaseHelper(context)`.
- Si cambias el esquema, sube `DATABASE_VERSION` y migra en `onUpgrade` **sin borrar datos**: son los tickets reales del usuario.
- La columna `gmail_message_id` guarda también el `Message-ID` de IMAP; los PDFs extra de un mismo correo llevan `#1`, `#2`…
- Duplicados: `UNIQUE` en `ticket_number`, `pdf_sha256` y `gmail_message_id`. Guarda los PDFs siempre con `sync/savePdfTicket()`.
- Un correo IMAP se marca en `imap_seen_messages` solo si se procesó (insertado, duplicado o sin PDF). Si falla, se reintenta en la siguiente sync.

### IMAP y proveedores
- Siempre `imaps`, puerto 993, comprobación del certificado y carpeta en **solo lectura**: la app no borra, mueve ni marca correos.
- La contraseña se guarda cifrada con AES-GCM y una clave del Android Keystore (`ImapSettingsStore`).
  `ImapSettings.toString()` la oculta: **no la registres nunca** en logs.
- Los atajos y la ayuda de cada proveedor están en `ImapProvider` (`mail/ImapSettings.kt`). Para añadir uno, rellena `host`,
  `passwordSteps` y `passwordUrl`; el formulario los muestra solo.
- **Outlook / Hotmail / Live no funcionan con contraseña**: desde septiembre de 2024 Microsoft exige OAuth (XOAUTH2) en las
  cuentas personales. Por eso no hay atajo, y el formulario avisa (`ImapProvider.isMicrosoftPersonal`).
  Soportarlas requeriría OAuth con Microsoft (MSAL o un flujo propio) y autenticación XOAUTH2 en Jakarta Mail.

### Privacidad y secretos
- No subas `local.properties` (contiene `sdk.dir` y `GMAIL_CLIENT_ID`), `samples/*.pdf` (tickets personales), `*.keystore`/`*.jks` ni APKs.
- No envíes datos del usuario a servicios externos: la app funciona sin servidores propios.

## Git y publicación

- Rama principal `main`. Trabaja en ramas de funcionalidad (por ejemplo, `imap`) y abre PR a `main`.
- Haz commit o push solo cuando el usuario lo pida.
- Versiones: `versionCode` y `versionName` en `app/build.gradle.kts`. Las APK se publican en GitHub Releases como `MercaStats-vX.Y.Z.apk`.
- Al cambiar comportamiento visible, actualiza el README (usuario) y `docs/ARCHITECTURE.md` (técnico). Si cambias una regla o trampa, actualiza este archivo.
