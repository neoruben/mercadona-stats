# Mercadona Stats

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
