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
  - `parser/PdfTextExtractor.kt`: Inicializa `pdfbox-android` (`PDFBoxResourceLoader`) y extrae el texto seleccionable del PDF ordenado por posición (`sortByPosition = true`), sin OCR ni servicios externos.
  - `parser/MercadonaTicketParser.kt`: Parser Kotlin puro (sin dependencias Android) que soporta tickets en **castellano** y **catalán**.
    - Extrae: tienda, dirección, fecha/hora (`DD/MM/YYYY HH:mm` -> ISO `YYYY-MM-DD HH:mm`), `FACTURA SIMPLIFICADA`, `TOTAL (€)`, desglose de IVA (`4%`, `10%`, `21%`) y líneas de producto.
    - Soporta líneas normales (con o sin precio unitario), **productos al peso en 2 líneas** (ej. nombre en una línea y `0,248 kg 8,90 €/kg 2,21` en la siguiente) y cabeceras de tabla partidas en varias líneas.
  - `data/TicketDatabaseHelper.kt`: Base de datos SQLite local (`mercadona_tickets.db`) con `SQLiteOpenHelper`.
    - Tablas: `tickets`, `ticket_items` (`ON DELETE CASCADE`) y `ticket_vat` (`ON DELETE CASCADE`).
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

---

## 4. Compilación, tests y publicación

- **Compilar APK debug:**
  ```powershell
  gradle assembleDebug
  ```
- **Tests unitarios:**
  - `MercadonaTicketParserTest` (formatos catalán, castellano, producto al peso y cabecera partida).
  - `StatsCalculatorTest` (agregación semanal ISO y mensual).
  - Nota en Windows: si la ruta de `%USERPROFILE%\.gradle` contiene caracteres no ASCII y el worker de Gradle falla al lanzar `GradleWorkerMain`, se pueden compilar con `gradle compileDebugUnitTestKotlin` y ejecutar directamente con `java -cp ... org.junit.runner.JUnitCore`.
- **Privacidad en Git:**
  - `.gitignore` excluye `local.properties`, `samples/*.pdf`, `*.apk`, `*.keystore`, `*.jks`, `.gradle/` y `build/`.
- **Publicación de APKs:**
  - Subir los binarios compilados a **GitHub Releases** siguiendo la convención `MercaStats-v.x.x.x.apk` (incrementando `versionCode` y `versionName` en `app/build.gradle.kts`).
