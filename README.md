# MercaStats

Aplicación Android (Kotlin + Jetpack Compose) que lee los tickets digitales PDF de Mercadona desde Gmail (solo lectura) o mediante importación directa de archivos PDF, los guarda en una base de datos SQLite local evitando duplicados y muestra estadísticas de gasto semanal, mensual y por producto.

## Características
- **Sincronización con Gmail (OAuth 2.0 solo lectura):** Busca correos de `ticket_digital@mail.mercadona.com` mediante `AuthorizationClient` nativo de Android y descarga únicamente los correos nuevos.
- **Sincronización automática al iniciar:** Tras la primera conexión, actualiza los tickets nuevos de forma silenciosa al abrir la app.
- **Importación manual de PDFs:** Permite importar archivos PDF locales directamente desde el dispositivo.
- **Parser PDF offline:** Extrae tienda, dirección, fecha/hora, número de factura simplificada, líneas de producto (unidades y peso) y desglose de IVA usando `pdfbox-android`, sin OCR ni servicios externos.
- **Base de datos SQLite local:** Almacena tickets, productos e IVA evitando duplicados por número de ticket, hash SHA-256 del PDF e ID de mensaje de Gmail.
- **Estadísticas y visualización:**
  - Tickets agrupados por mes desplegable con total mensual y detalle individual.
  - Gráfica y desglose de gasto por mes y por semana.
  - Ranking de productos por frecuencia de compra y gasto acumulado con buscador.

## Configuración local (OAuth)
1. Crea un proyecto en Google Cloud Console y habilita **Gmail API**.
2. Configura la pantalla de consentimiento OAuth en modo **Testing** añadiendo tu cuenta como usuario de prueba y el alcance `https://www.googleapis.com/auth/gmail.readonly`.
3. Crea un **ID de cliente OAuth de tipo Android** con el paquete `io.github.neoruben.mercadonastats` y la huella SHA-1 de tu keystore de firma.
4. Opcionalmente, añade `GMAIL_CLIENT_ID=...` en `local.properties` (archivo excluido de Git).

## Privacidad
Todos los tickets y estadísticas se procesan y almacenan exclusivamente en el dispositivo del usuario (SQLite local). No se envían datos a servidores de terceros.

## Arquitectura y detalles técnicos
- **Base de datos (`data/TicketDatabaseHelper.kt`):** SQLite (`mercadona_tickets.db`, versión 1) con tablas `tickets` (cabecera, `total_amount`, `total_vat_amount`, `ticket_number`, `pdf_sha256`, `gmail_message_id`), `ticket_items` (`quantity`, `unit`, `description`, `unit_price`, `total_price`) y `ticket_vat`.
- **Parser (`parser/MercadonaTicketParser.kt`):** Extrae la cabecera y las líneas entre `Cnt./Cant.` y `TOTAL (€)` soportando castellano y catalán, unidades (`ITEM_LINE_WITH_UNIT_PRICE`, `ITEM_LINE_SINGLE`) y productos al peso (`WEIGHT_LINE_REGEX`, asociando la descripción de la línea anterior).
- **Cálculo de estadísticas (`stats/StatsCalculator.kt`):**
  - El **Gasto Total** y las gráficas mensuales/semanales suman el `total_amount` oficial de cada ticket (`TOTAL (€)`).
  - **Ranking de productos (`productStats`):** Agrupa `ticket_items` normalizando `description` (`trim`, espacios colapsados, `uppercase(Locale.ROOT)`) y acumula `totalQuantity` (unidades o `kg`), `purchaseCount` (nº de tickets distintos en los que aparece) y `totalAmount` (€).
- **Interfaz (`ui/MercadonaAppUi.kt`):** Tres pestañas inferiores:
  1. `Tickets`: sincronización Gmail / importación PDF y listado.
  2. `Estadísticas`: KPIs globales y gráficas por mes / semana.
  3. `Productos` (desde `v1.1`, `versionCode = 2`): alterna entre **Más comprados** (`purchaseCount` → `totalQuantity` → `totalAmount`) y **Mayor importe** (`totalAmount`), con buscador por nombre y barra proporcional.

## Documentación técnica
Consulta [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) para ver el detalle de la arquitectura, el parser PDF, el esquema SQLite, la configuración OAuth y los comandos de compilación y pruebas.
