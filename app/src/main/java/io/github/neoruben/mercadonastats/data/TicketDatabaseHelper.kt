package io.github.neoruben.mercadonastats.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.neoruben.mercadonastats.parser.ParsedTicket
import io.github.neoruben.mercadonastats.parser.ParsedTicketItem
import io.github.neoruben.mercadonastats.parser.ParsedVatBreakdown

data class StoredTicket(
    val id: Long,
    val ticketNumber: String,
    val pdfSha256: String,
    val gmailMessageId: String?,
    val storeName: String,
    val storeAddress: String,
    val dateTimeIso: String,
    val dateDisplay: String,
    val totalAmount: Double,
    val totalVatAmount: Double,
    val items: List<ParsedTicketItem>,
    val vatBreakdown: List<ParsedVatBreakdown>
)

enum class InsertTicketStatus {
    INSERTED,
    DUPLICATE_TICKET_NUMBER,
    DUPLICATE_PDF_HASH,
    DUPLICATE_GMAIL_MESSAGE
}

/**
 * Base de datos SQLite local para almacenar tickets de Mercadona evitando duplicados
 * mediante claves UNIQUE en `ticket_number`, `pdf_sha256` y `gmail_message_id`.
 */
class TicketDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "mercadona_tickets.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_TICKETS = "tickets"
        private const val TABLE_ITEMS = "ticket_items"
        private const val TABLE_VAT = "ticket_vat"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TICKETS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_number TEXT NOT NULL UNIQUE,
                pdf_sha256 TEXT NOT NULL UNIQUE,
                gmail_message_id TEXT UNIQUE,
                store_name TEXT NOT NULL,
                store_address TEXT NOT NULL,
                date_time_iso TEXT NOT NULL,
                date_display TEXT NOT NULL,
                total_amount REAL NOT NULL,
                total_vat_amount REAL NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ITEMS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id INTEGER NOT NULL,
                quantity REAL NOT NULL,
                unit TEXT,
                description TEXT NOT NULL,
                unit_price REAL,
                total_price REAL NOT NULL,
                FOREIGN KEY(ticket_id) REFERENCES $TABLE_TICKETS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_VAT (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id INTEGER NOT NULL,
                vat_rate_percent INTEGER NOT NULL,
                base_amount REAL NOT NULL,
                vat_amount REAL NOT NULL,
                total_amount REAL NOT NULL,
                FOREIGN KEY(ticket_id) REFERENCES $TABLE_TICKETS(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_tickets_date ON $TABLE_TICKETS(date_time_iso DESC)")
        db.execSQL("CREATE INDEX idx_items_ticket ON $TABLE_ITEMS(ticket_id)")
        db.execSQL("CREATE INDEX idx_vat_ticket ON $TABLE_VAT(ticket_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getKnownGmailMessageIds(): Set<String> {
        val ids = mutableSetOf<String>()
        readableDatabase.rawQuery(
            "SELECT gmail_message_id FROM $TABLE_TICKETS WHERE gmail_message_id IS NOT NULL",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (!id.isNullOrBlank()) ids.add(id)
            }
        }
        return ids
    }

    /**
     * Elimina tickets que se guardaron sin líneas de producto (p. ej. por un cambio de cabecera)
     * para que puedan volver a sincronizarse y parsearse correctamente.
     */
    fun deleteTicketsWithoutItems(): Int {
        return writableDatabase.delete(
            TABLE_TICKETS,
            "NOT EXISTS (SELECT 1 FROM $TABLE_ITEMS WHERE $TABLE_ITEMS.ticket_id = $TABLE_TICKETS.id)",
            null
        )
    }

    fun insertTicketIfNew(
        ticket: ParsedTicket,
        pdfSha256: String,
        gmailMessageId: String?
    ): InsertTicketStatus {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (!gmailMessageId.isNullOrBlank() && exists(db, "gmail_message_id = ?", arrayOf(gmailMessageId))) {
                return InsertTicketStatus.DUPLICATE_GMAIL_MESSAGE
            }
            if (exists(db, "ticket_number = ?", arrayOf(ticket.ticketNumber))) {
                return InsertTicketStatus.DUPLICATE_TICKET_NUMBER
            }
            if (exists(db, "pdf_sha256 = ?", arrayOf(pdfSha256))) {
                return InsertTicketStatus.DUPLICATE_PDF_HASH
            }

            val ticketValues = ContentValues().apply {
                put("ticket_number", ticket.ticketNumber)
                put("pdf_sha256", pdfSha256)
                if (!gmailMessageId.isNullOrBlank()) {
                    put("gmail_message_id", gmailMessageId)
                }
                put("store_name", ticket.storeName)
                put("store_address", ticket.storeAddress)
                put("date_time_iso", ticket.dateTimeIso)
                put("date_display", ticket.dateDisplay)
                put("total_amount", ticket.totalAmount)
                put("total_vat_amount", ticket.totalVatAmount)
                put("created_at_ms", System.currentTimeMillis())
            }

            val ticketId = db.insertOrThrow(TABLE_TICKETS, null, ticketValues)

            for (item in ticket.items) {
                val itemValues = ContentValues().apply {
                    put("ticket_id", ticketId)
                    put("quantity", item.quantity)
                    put("unit", item.unit)
                    put("description", item.description)
                    if (item.unitPrice != null) {
                        put("unit_price", item.unitPrice)
                    }
                    put("total_price", item.totalPrice)
                }
                db.insertOrThrow(TABLE_ITEMS, null, itemValues)
            }

            for (vat in ticket.vatBreakdown) {
                val vatValues = ContentValues().apply {
                    put("ticket_id", ticketId)
                    put("vat_rate_percent", vat.vatRatePercent)
                    put("base_amount", vat.baseAmount)
                    put("vat_amount", vat.vatAmount)
                    put("total_amount", vat.totalAmount)
                }
                db.insertOrThrow(TABLE_VAT, null, vatValues)
            }

            db.setTransactionSuccessful()
            return InsertTicketStatus.INSERTED
        } finally {
            db.endTransaction()
        }
    }

    fun getAllTickets(): List<StoredTicket> {
        val db = readableDatabase
        val tickets = mutableListOf<StoredTicket>()

        db.rawQuery(
            """
            SELECT id, ticket_number, pdf_sha256, gmail_message_id, store_name, store_address,
                   date_time_iso, date_display, total_amount, total_vat_amount
            FROM $TABLE_TICKETS
            ORDER BY date_time_iso DESC, id DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                tickets.add(
                    StoredTicket(
                        id = id,
                        ticketNumber = cursor.getString(1),
                        pdfSha256 = cursor.getString(2),
                        gmailMessageId = cursor.getString(3),
                        storeName = cursor.getString(4),
                        storeAddress = cursor.getString(5),
                        dateTimeIso = cursor.getString(6),
                        dateDisplay = cursor.getString(7),
                        totalAmount = cursor.getDouble(8),
                        totalVatAmount = cursor.getDouble(9),
                        items = loadItems(db, id),
                        vatBreakdown = loadVat(db, id)
                    )
                )
            }
        }
        return tickets
    }

    private fun loadItems(db: SQLiteDatabase, ticketId: Long): List<ParsedTicketItem> {
        val list = mutableListOf<ParsedTicketItem>()
        db.rawQuery(
            "SELECT quantity, unit, description, unit_price, total_price FROM $TABLE_ITEMS WHERE ticket_id = ? ORDER BY id ASC",
            arrayOf(ticketId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ParsedTicketItem(
                        quantity = c.getDouble(0),
                        unit = c.getString(1),
                        description = c.getString(2),
                        unitPrice = if (c.isNull(3)) null else c.getDouble(3),
                        totalPrice = c.getDouble(4)
                    )
                )
            }
        }
        return list
    }

    private fun loadVat(db: SQLiteDatabase, ticketId: Long): List<ParsedVatBreakdown> {
        val list = mutableListOf<ParsedVatBreakdown>()
        db.rawQuery(
            "SELECT vat_rate_percent, base_amount, vat_amount, total_amount FROM $TABLE_VAT WHERE ticket_id = ? ORDER BY vat_rate_percent ASC",
            arrayOf(ticketId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ParsedVatBreakdown(
                        vatRatePercent = c.getInt(0),
                        baseAmount = c.getDouble(1),
                        vatAmount = c.getDouble(2),
                        totalAmount = c.getDouble(3)
                    )
                )
            }
        }
        return list
    }

    private fun exists(db: SQLiteDatabase, whereClause: String, args: Array<String>): Boolean {
        db.rawQuery("SELECT 1 FROM $TABLE_TICKETS WHERE $whereClause LIMIT 1", args).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
