package io.github.neoruben.mercadonastats.sync

import android.content.Context
import io.github.neoruben.mercadonastats.data.InsertTicketStatus
import io.github.neoruben.mercadonastats.data.TicketDatabaseHelper
import io.github.neoruben.mercadonastats.parser.MercadonaTicketParser
import io.github.neoruben.mercadonastats.parser.PdfTextExtractor
import io.github.neoruben.mercadonastats.stats.StatsCalculator

/** Lee un PDF de ticket y lo guarda si es nuevo. Bloqueante: llámalo fuera del hilo principal. */
fun savePdfTicket(
    context: Context,
    db: TicketDatabaseHelper,
    pdfBytes: ByteArray,
    messageId: String?
): InsertTicketStatus {
    val sha256 = StatsCalculator.sha256Hex(pdfBytes)
    val rawText = PdfTextExtractor.extractText(context, pdfBytes)
    val parsedTicket = MercadonaTicketParser.parse(rawText)
    return db.insertTicketIfNew(
        ticket = parsedTicket,
        pdfSha256 = sha256,
        gmailMessageId = messageId
    )
}
