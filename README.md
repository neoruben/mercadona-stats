# MercaStats

[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Apoyar-orange?logo=kofi&logoColor=white)](https://ko-fi.com/neoruben)

Aplicación Android (Kotlin + Jetpack Compose) que lee los tickets digitales PDF de Mercadona desde Gmail (solo lectura) o mediante importación directa de archivos PDF, los guarda en una base de datos SQLite local evitando duplicados y muestra estadísticas de gasto semanal, mensual y por producto.

---

## Características

- **Sincronización con Gmail (OAuth 2.0 solo lectura):** Busca correos de `ticket_digital@mail.mercadona.com` mediante `AuthorizationClient` nativo de Google Play Services y descarga únicamente los correos nuevos.
  - *Nota:* Requiere que la cuenta de correo donde se reciben los tickets digitales de Mercadona sea de **Gmail** o Google Workspace.
- **Importación manual de PDFs (100% offline y sin cuenta de Google):** Si tus tickets llegan a otro proveedor (Outlook, Hotmail, Yahoo, iCloud...) o prefieres no vincular Gmail, puedes descargar los PDFs y cargarlos directamente desde el almacenamiento del móvil.
- **Sincronización automática silenciosa:** Tras la primera conexión, actualiza los tickets nuevos de forma silenciosa al abrir la app.
- **Parser PDF offline:** Extrae tienda, dirección, fecha/hora, número de factura simplificada, líneas de producto (unidades y peso) y desglose de IVA usando `pdfbox-android`, sin OCR ni servicios externos.
- **Base de datos SQLite local:** Almacena tickets, productos e IVA evitando duplicados por número de ticket, hash SHA-256 del PDF e ID de mensaje de Gmail.
- **Estadísticas y visualización:**
  - Tickets agrupados por mes desplegable con total mensual y diálogo de detalle individual.
  - Gráfica de barras con desglose de gasto por mes y por semana.
  - Ranking de productos por frecuencia de compra y gasto acumulado con buscador y medallas Top 3.

---

## Sincronización y Configuración de OAuth

La aplicación utiliza la API de Gmail en modo de solo lectura (`gmail.readonly`).

### Si utilizas el APK precompilado (Releases)
Al tratarse de una aplicación privada/en fase de pruebas que no está publicada en la Play Store:
- Google requiere que tu dirección de Gmail esté autorizada en la lista de **Usuarios de prueba** del proyecto en Google Cloud.
- Si vas a probar la app, ponte en contacto con el autor para dar de alta tu correo como tester.
- Si no deseas vincular tu cuenta de Google, puedes usar libremente la función **«Importar PDFs»**.

### Para desarrolladores que compilen su propio APK
1. Crea un proyecto en [Google Cloud Console](https://console.cloud.google.com/) y habilita la **Gmail API**.
2. Configura la pantalla de consentimiento OAuth en modo **Testing** añadiendo tu cuenta en *Usuarios de prueba* y el alcance `https://www.googleapis.com/auth/gmail.readonly`.
3. Crea un **ID de cliente OAuth de tipo Android** con el paquete `io.github.neoruben.mercadonastats` y la huella SHA-1 de tu keystore de firma.
4. Opcionalmente, añade `GMAIL_CLIENT_ID=...` en `local.properties` (archivo excluido de Git).

---

## Privacidad

Todos los tickets, productos y estadísticas se procesan y almacenan exclusivamente en el dispositivo del usuario (base de datos SQLite local). La aplicación no cuenta con servidores intermediarios ni envía tus datos o tickets a terceros.

---

## Arquitectura y detalles técnicos

- **Base de datos (`data/TicketDatabaseHelper.kt`):** SQLite (`mercadona_tickets.db`, versión 1) con tablas `tickets` (cabecera, `total_amount`, `total_vat_amount`, `ticket_number`, `pdf_sha256`, `gmail_message_id`), `ticket_items` (`quantity`, `unit`, `description`, `unit_price`, `total_price`) y `ticket_vat`.
- **Parser (`parser/MercadonaTicketParser.kt`):** Extrae la cabecera y las líneas entre `Cnt./Cant.` y `TOTAL (€)` soportando castellano y catalán, unidades (`ITEM_LINE_WITH_UNIT_PRICE`, `ITEM_LINE_SINGLE`) y productos al peso (`WEIGHT_LINE_REGEX`, asociando la descripción de la línea anterior).
- **Cálculo de estadísticas (`stats/StatsCalculator.kt`):**
  - El **Gasto Total** y las gráficas mensuales/semanales suman el `total_amount` oficial de cada ticket (`TOTAL (€)`).
  - **Ranking de productos (`productStats`):** Agrupa `ticket_items` normalizando `description` (`trim`, espacios colapsados, `uppercase(Locale.ROOT)`) y acumula `totalQuantity` (unidades o `kg`), `purchaseCount` (nº de tickets distintos en los que aparece) y `totalAmount` (€).
- **Interfaz (`ui/MercadonaAppUi.kt`):** Tres pestañas inferiores:
  1. `Tickets`: sincronización Gmail / importación PDF y listado.
  2. `Estadísticas`: KPIs globales y gráficas por mes / semana.
  3. `Productos`: alterna entre **Más comprados** (`purchaseCount` → `totalQuantity` → `totalAmount`) y **Mayor importe** (`totalAmount`), con buscador por nombre y barra proporcional.

Consulta [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) para más detalles sobre la arquitectura interna y la guía de compilación.

---

## Licencia

Copyright © 2026 [neoruben](https://github.com/neoruben). Todos los derechos reservados.

El código fuente de este proyecto se publica exclusivamente con fines educativos y de consulta personal. Queda terminantemente prohibida la redistribución, modificación, venta, uso comercial o publicación de este software (en formato fuente o binario compilado/APK) en cualquier plataforma o tienda de aplicaciones (incluyendo Google Play Store u otras tiendas de terceros) sin la autorización expresa y por escrito del autor.

---

Si te gusta el proyecto o te resulta de utilidad, puedes apoyar su desarrollo invitándome a un café en [Ko-fi](https://ko-fi.com/neoruben).
