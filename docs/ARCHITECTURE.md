# Arquitectura y Guía Técnica — MercaStats

Este documento resume cómo está construida **MercaStats**, cómo funcionan sus piezas internas y cómo compilar, probar y publicar nuevas versiones.

---

## 1. Visión general de módulos y archivos

- **`app/src/main/java/io/github/neoruben/mercadonastats/`**
  - `MainActivity.kt`: Orquesta el ciclo de vida, la autorización nativa con Google Play Services (`AuthorizationClient`), la sincronización automática silenciosa al iniciar (`auto_sync_enabled` en `SharedPreferences`), la sincronización incremental con Gmail, la importación manual de PDFs locales y la recarga de estadísticas.
  - `gmail/GmailMercadonaService.kt`: Cliente REST (sin SDK pesado) contra `https://gmail.googleapis.com/gmail/v1/users/me`.
    - `listMercadonaMessageIds`: Pagina todos los correos que coinciden con `from:ticket_digital@mail.mercadona.com`.
    - `getMessageDetails`: Inspecciona las partes MIME del correo para localizar adjuntos `.pdf` (`application/pdf`) o enlaces PDF en el cuerpo.
    - `downloadPdfAttachment`: Descarga el adjunto y lo decodifica desde Base64URL.
  - `gmail/GmailOAuthManager.kt`: Implementación PKCE de respaldo para OAuth 2.0. En la app principal se usa `Identity.getAuthorizationClient(activity)` de `play-services-auth` porque valida directamente paquete + SHA-1 sin depender de `redirect_uri` en navegador.
  - `mail/ImapMercadonaService.kt`: Lectura por IMAP (Jakarta Mail, `org.eclipse.angus:jakarta.mail`), siempre `imaps` en el puerto 993 con `ssl.checkserveridentity=true` y la carpeta abierta en `READ_ONLY`.
    - `open(settings)`: conecta y abre la carpeta. Si no existe, el error incluye la lista de carpetas disponibles.
    - `ImapMailbox.findMercadonaMails()`: busca con `FromStringTerm("ticket_digital@mail.mercadona.com")` y obtiene el identificador estable de cada correo.
    - `ImapMailbox.readPdfs(mail)`: descarga los PDFs del correo.
    - `friendly(e, host)`: traduce los errores (contraseña rechazada, servidor desconocido, sin conexión) a mensajes en español.
  - `mail/ImapMessageParts.kt`: Funciones puras (probadas con tests JVM): `collectPdfAttachments` (recorre `multipart/*` y correos reenviados `message/rfc822`; detecta PDFs por tipo MIME o nombre) y `stableMessageId` (cabecera `Message-ID`, o `imap:<carpeta>:uid-N` si no la hay).
  - `mail/ImapSettingsStore.kt`: Guarda servidor, correo y carpeta en `SharedPreferences` (`imap_prefs`) y la contraseña cifrada con AES-GCM y una clave del **Android Keystore** (`mercastats_imap_password`). `clear()` borra también la clave.
  - `ui/ImapSettingsDialog.kt`: Formulario de la cuenta IMAP (correo, contraseña de aplicación, servidor con atajos Gmail/iCloud, carpeta).
  - `parser/PdfTextExtractor.kt`: Inicializa `pdfbox-android` (`PDFBoxResourceLoader`) y extrae el texto seleccionable del PDF ordenado por posición (`sortByPosition = true`), sin OCR ni servicios externos.
  - `parser/MercadonaTicketParser.kt`: Parser Kotlin puro (sin dependencias Android) que soporta tickets en **castellano** y **catalán**.
    - Extrae: tienda, dirección, fecha/hora (`DD/MM/YYYY HH:mm` -> ISO `YYYY-MM-DD HH:mm`), `FACTURA SIMPLIFICADA`, `TOTAL (€)`, desglose de IVA (`4%`, `10%`, `21%`) y líneas de producto.
    - Soporta líneas normales (con o sin precio unitario), **productos al peso en 2 líneas** (ej. nombre en una línea y `0,248 kg 8,90 €/kg 2,21` en la siguiente) y cabeceras de tabla partidas en varias líneas.
  - `data/TicketDatabaseHelper.kt`: Base de datos SQLite local (`mercadona_tickets.db`) con `SQLiteOpenHelper`.
    - Tablas: `tickets`, `ticket_items` (`ON DELETE CASCADE`), `ticket_vat` (`ON DELETE CASCADE`) e `imap_seen_messages` (versión 2 de la base de datos; `onUpgrade` la crea al actualizar desde la versión 1).
    - Prevención de duplicados mediante restricciones `UNIQUE` en `ticket_number`, `pdf_sha256` y `gmail_message_id`.
    - `deleteTicketsWithoutItems()`: limpia tickets que pudieran haberse guardado sin líneas para re-parsearlos al sincronizar.
  - `stats/StatsCalculator.kt`: Calcula el resumen global (total, ticket medio, IVA soportado, nº de compras), agrupación por **mes** (`YYYY-MM`), agrupación por **semana ISO** (`YYYY-Www`) y ranking de **productos** (frecuencia, unidades/kg y gasto acumulado).
  - `ui/MercadonaAppUi.kt`: Interfaz Jetpack Compose con 3 pestañas:
    1. **Tickets:** Estado de conexión Gmail, botones de sincronización e importación PDF, y listado agrupado por **meses desplegables** (cabecera verde con total mensual y tarjetas blancas por compra con diálogo de detalle completo).
    2. **Estadísticas:** 4 KPIs, selector *Mes / Semana*, gráfica de barras con degradado y listado histórico.
    3. **Productos:** Buscador, selector *Frecuencia / Gasto*, medallas Top 3 y barra proporcional.

---

## 2. Autenticación OAuth 2.0 y Google Cloud

- **Alcance usado:** `https://www.googleapis.com/auth/gmail.readonly` (solo lectura).
- **Credenciales:** No hay claves ni secretos en el código fuente.
- **Configuración en Google Cloud Console:**
  - API habilitada: **Gmail API**.
  - Pantalla de consentimiento: **Externo**, en modo **En pruebas (Testing)** con los correos autorizados añadidos en *Usuarios de prueba* (límite 100 usuarios sin verificación CASA).
  - Cliente OAuth: Tipo **Android** con:
    - Paquete: `io.github.neoruben.mercadonastats`
    - Huella **SHA-1** del keystore con el que se firme el APK (para debug, la del `debug.keystore`; para release, la del keystore de producción o Google Play App Signing).

---

## 3. Flujo de sincronización incremental y anti-duplicados

1. Al abrir la app, si el usuario conectó Gmail previamente (`auto_sync_enabled = true`), `trySilentAutoSync()` solicita un token silencioso a `AuthorizationClient` (sin diálogo) y lanza `syncFromGmail`.
2. `syncFromGmail` obtiene todos los `messageId` de Mercadona, consulta `getKnownGmailMessageIds()` en SQLite y descarga **únicamente** los mensajes nuevos.
3. Por cada PDF descargado:
   - Calcula el hash **SHA-256** (`StatsCalculator.sha256Hex`).
   - Extrae el texto con `PdfTextExtractor` y lo parsea con `MercadonaTicketParser`.
   - Lo inserta en una transacción SQLite si no coinciden `gmail_message_id`, `ticket_number` ni `pdf_sha256`.

### Sincronización por IMAP

1. Si hay una cuenta IMAP guardada, tiene prioridad sobre Gmail: se usa al abrir la app y al pulsar **Sincronizar**.
2. `syncFromImap` abre la carpeta, busca los correos de Mercadona y descarta los ya conocidos: los `gmail_message_id` de `tickets` y los de `imap_seen_messages`.
3. Cada PDF pasa por el mismo `parseAndSavePdfBytes` que Gmail y la importación manual, guardando el `Message-ID` en `gmail_message_id` (si un correo trae varios PDFs, los siguientes llevan `#1`, `#2`…).
4. El correo se marca en `imap_seen_messages` tanto si se guardó como si era un duplicado (por ejemplo, un ticket ya importado por Gmail o PDF) o no tenía PDF, para no volver a descargarlo. Si falla la lectura o el análisis del PDF, no se marca y se reintenta en la siguiente sincronización.
5. Al guardar la cuenta desde el formulario, primero se prueba la conexión; solo se guarda si funciona.

---

## 4. Compilación, tests y publicación

- **Compilar APK debug:**
  ```powershell
  gradle assembleDebug
  ```
- **Tests unitarios:**
  - `MercadonaTicketParserTest` (formatos catalán, castellano, producto al peso y cabecera partida).
  - `StatsCalculatorTest` (agregación semanal ISO y mensual).
  - `ImapMessagePartsTest` (PDF adjunto, PDF con tipo genérico, correo reenviado, varios adjuntos, sin PDF e identificador del correo).
- **GitHub Actions (`.github/workflows/android.yml`):** en cada subida ejecuta los tests y compila la APK de depuración, que queda descargable como artefacto `MercaStats-debug-apk` durante 14 días en la pestaña **Actions**.
  - Nota en Windows: si la ruta de `%USERPROFILE%\.gradle` contiene caracteres no ASCII y el worker de Gradle falla al lanzar `GradleWorkerMain`, se pueden compilar con `gradle compileDebugUnitTestKotlin` y ejecutar directamente con `java -cp ... org.junit.runner.JUnitCore`.
- **Privacidad en Git:**
  - `.gitignore` excluye `local.properties`, `samples/*.pdf`, `*.apk`, `*.keystore`, `*.jks`, `.gradle/` y `build/`.
- **Publicación de APKs:**
  - Subir los binarios compilados a **GitHub Releases** siguiendo la convención `MercaStats-v.x.x.x.apk` (incrementando `versionCode` y `versionName` en `app/build.gradle.kts`).
