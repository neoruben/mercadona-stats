package io.github.neoruben.mercadonastats.sync

import android.content.Context
import android.util.Log
import io.github.neoruben.mercadonastats.data.InsertTicketStatus
import io.github.neoruben.mercadonastats.data.TicketDatabaseHelper
import io.github.neoruben.mercadonastats.mail.ImapException
import io.github.neoruben.mercadonastats.mail.ImapMailbox
import io.github.neoruben.mercadonastats.mail.ImapMercadonaService
import io.github.neoruben.mercadonastats.mail.ImapSettings
import io.github.neoruben.mercadonastats.mail.MercadonaMail
import jakarta.mail.FolderClosedException
import jakarta.mail.StoreClosedException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Importa los tickets nuevos de una cuenta IMAP. Si el servidor o la red cortan la conexión
 * (por ejemplo, al apagarse la pantalla), reconecta y sigue por el mismo correo.
 * Todo es bloqueante: llámalo desde un hilo de fondo.
 */
class ImapTicketImporter(
    private val context: Context,
    private val db: TicketDatabaseHelper,
    private val imapService: ImapMercadonaService = ImapMercadonaService()
) {

    fun interface Progress {
        fun update(current: Int, total: Int, message: String)
    }

    @Volatile
    private var cancelled = false

    /** Pide parar: se detiene antes del siguiente correo. */
    fun cancel() {
        cancelled = true
    }

    /** Devuelve el mensaje final. Lanza [ImapException] si no puede conectar o se pierde la conexión. */
    fun run(settings: ImapSettings, progress: Progress): String {
        progress.update(0, 0, "Conectando con ${settings.host}...")
        var mailbox = imapService.open(settings)
        try {
            progress.update(0, 0, "Buscando tickets en «${settings.folder}»...")
            val allMails = mailbox.findMercadonaMails()
            if (allMails.isEmpty()) {
                return "No hay correos de Mercadona en «${settings.folder}»."
            }

            db.deleteTicketsWithoutItems()
            val known = db.getKnownGmailMessageIds() + db.getSeenImapMessageIds()
            var pending = allMails.filterNot { it.id in known }
            if (pending.isEmpty()) {
                return "Todo al día: ${allMails.size} correos revisados, sin tickets nuevos."
            }

            var inserted = 0
            var duplicates = 0
            var failed = 0
            var reconnectsInARow = 0
            var index = 0

            while (index < pending.size) {
                if (cancelled) {
                    throw ImapException(
                        "Sincronización interrumpida: $inserted tickets nuevos importados. " +
                            "El resto se importará la próxima vez."
                    )
                }
                val mail = pending[index]
                progress.update(
                    index + 1,
                    pending.size,
                    "Importando ${index + 1} de ${pending.size} correos nuevos..."
                )
                try {
                    val (newOnes, repeated) = importMail(mailbox, mail)
                    if (newOnes == 0 && repeated == 0) failed++
                    inserted += newOnes
                    duplicates += repeated
                    reconnectsInARow = 0
                    index++
                } catch (e: Exception) {
                    if (!isConnectionLost(e)) {
                        // No se marca como revisado: se reintentará en la próxima sincronización.
                        Log.w(TAG, "Error procesando un correo", e)
                        failed++
                        index++
                        continue
                    }
                    if (reconnectsInARow >= MAX_RECONNECTS_IN_A_ROW) {
                        throw ImapException(
                            "Se ha perdido la conexión con ${settings.host}. Se han importado " +
                                "$inserted tickets nuevos; el resto se importará la próxima vez.",
                            e
                        )
                    }
                    reconnectsInARow++
                    Log.i(TAG, "Conexión perdida, reconectando (intento $reconnectsInARow)", e)
                    progress.update(index + 1, pending.size, "Reconectando con ${settings.host}...")
                    mailbox.close()
                    mailbox = imapService.open(settings)
                    // Los mensajes de la conexión anterior ya no sirven: se vuelven a buscar por id.
                    val fresh = mailbox.findMercadonaMails().associateBy { it.id }
                    pending = pending.take(index) + pending.drop(index).mapNotNull { fresh[it.id] }
                }
            }

            return buildString {
                append("Sincronización completada: $inserted nuevos")
                if (duplicates > 0) append(", $duplicates ya estaban guardados")
                if (failed > 0) append(", $failed sin PDF válido")
            }
        } finally {
            mailbox.close()
        }
    }

    /** Importa los PDF de un correo y lo marca como revisado. Devuelve (nuevos, duplicados). */
    private fun importMail(mailbox: ImapMailbox, mail: MercadonaMail): Pair<Int, Int> {
        val pdfs = mailbox.readPdfs(mail)
        var inserted = 0
        var duplicates = 0
        for ((pdfIndex, pdf) in pdfs.withIndex()) {
            val id = if (pdfIndex == 0) mail.id else "${mail.id}#$pdfIndex"
            when (savePdfTicket(context, db, pdf.second, id)) {
                InsertTicketStatus.INSERTED -> inserted++
                else -> duplicates++
            }
        }
        // Un correo sin PDF no va a cambiar: también se marca para no volver a descargarlo.
        db.markImapMessageSeen(mail.id)
        return inserted to duplicates
    }

    private fun isConnectionLost(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any {
            it is FolderClosedException ||
                it is StoreClosedException ||
                it is SocketException ||
                it is SocketTimeoutException ||
                it is SSLException
        }

    private companion object {
        const val TAG = "MercadonaImap"
        const val MAX_RECONNECTS_IN_A_ROW = 3
    }
}
