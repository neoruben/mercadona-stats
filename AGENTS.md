# AGENTS.md — Guía para agentes de código (Claude Code, Codex, Antigravity…)

Este archivo es la referencia común para cualquier agente que trabaje en el repositorio.
**El estado del trabajo en curso y los siguientes pasos están en [PENDIENTE.md](PENDIENTE.md): léelo al empezar y actualízalo al terminar.**
`CLAUDE.md` y `GEMINI.md` solo lo importan: **edita aquí** y no dupliques contenido en ellos.
Para el detalle de cada pieza, consulta [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Lo que ve el usuario está en el [README.md](README.md).

## Qué es

**MercaStats**: app Android (Kotlin + Jetpack Compose, sin backend) que importa los tickets digitales PDF de
Mercadona, los guarda en SQLite en el propio móvil y muestra estadísticas de gasto. Tiene dos vías de importación:

1. **IMAP con contraseña de aplicación** (Jakarta Mail): Gmail, iCloud o cualquier servidor IMAP.
2. **PDF compartido o abierto** desde otra app, o elegido con el selector de archivos.

El inicio de sesión con Google (API de Gmail con OAuth) se eliminó en la 1.2.0: no lo vuelvas a añadir sin que el usuario lo pida.

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
  MainActivity.kt          Estado de la pantalla, formulario IMAP, importación de PDF; lanza la sync IMAP
  data/                    TicketDatabaseHelper (SQLite, instancia única con get())
  mail/                    IMAP: conexión, partes MIME, ajustes y contraseña cifrada (Keystore)
  parser/                  Extracción de texto del PDF (pdfbox-android) y parser del ticket
  stats/                   Agregados por mes, semana y producto, e histórico de precios
  sync/                    Sincronización IMAP en segundo plano (servicio, importador, estado)
  ui/                      Compose: pestañas, formulario IMAP e histórico de precios (ProductPriceDialog)
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
- El wrapper de Gradle (`gradlew`, `gradle/wrapper/`, Gradle 9.9.2) está versionado y lo usan tanto las máquinas
  locales como la CI. Para cambiar de versión: `./gradlew wrapper --gradle-version X --gradle-distribution-sha256-sum <suma>`
  (la suma está en https://gradle.org/release-checksums).
- Gradle se ejecuta con Java 25 (`gradle/gradle-daemon-jvm.properties`, igual que el JBR de Android Studio); la CI instala JDK 25.
- Versiones: AGP 9.5.1, Kotlin (plugin Compose) 2.2.10, compileSdk/targetSdk 35, minSdk 26, bytecode Java 17.
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
- La importación de PDFs sigue en `lifecycleScope`: si crece, muévela al mismo esquema.

### Base de datos
- Usa siempre `TicketDatabaseHelper.get(context)`, que es instancia única compartida por la pantalla y el servicio. No hagas `TicketDatabaseHelper(context)`.
- Si cambias el esquema, sube `DATABASE_VERSION` y migra en `onUpgrade` **sin borrar datos**: son los tickets reales del usuario.
- La columna `gmail_message_id` guarda el `Message-ID` de IMAP (el nombre viene de cuando se usaba la API de Gmail; no la renombres); los PDFs extra de un mismo correo llevan `#1`, `#2`…
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
- No subas `local.properties` (contiene `sdk.dir` y la ruta de la firma), `samples/*.pdf` (tickets personales), `*.keystore`/`*.jks` ni APKs.
- No envíes datos del usuario a servicios externos: la app funciona sin servidores propios.

## Git y publicación

- Rama principal `main`. Trabaja en ramas de funcionalidad (por ejemplo, `imap`) y abre PR a `main`.
- Haz commit o push solo cuando el usuario lo pida.
- Versiones: `versionCode` y `versionName` en `app/build.gradle.kts`. Las APK se publican en GitHub Releases como `MercaStats-v.X.Y.Z.apk` (con punto tras la «v», como las anteriores).
- **APK release firmada:** `./gradlew :app:assembleRelease` genera `app/build/outputs/apk/release/app-release.apk`.
  Se firma sola si `local.properties` tiene `RELEASE_KEYSTORE_PROPERTIES=<ruta a keystore.properties>` (fuera del repo,
  con `storeFile`, `storePassword`, `keyAlias` y `keyPassword`). Si la ruta de `storeFile` no existe, se busca el keystore
  junto a ese archivo. Sin esa línea, la release sale sin firmar.
  **No leas, muestres ni copies las contraseñas** de ese archivo: Gradle las lee directamente.
  Comprueba la firma con `apksigner verify --print-certs` (en `build-tools`): el certificado debe ser el mismo que el de
  la release anterior, o la actualización no se instalará encima.
- Al cambiar comportamiento visible, actualiza el README (usuario) y `docs/ARCHITECTURE.md` (técnico). Si cambias una regla o trampa, actualiza este archivo.
