package io.github.neoruben.mercadonastats.mail

import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeUtility

/** Funciones puras sobre correos (sin conexión), para poder probarlas con tests JVM. */
object ImapMessageParts {

    /** Remitente de los tickets digitales de Mercadona. */
    const val MERCADONA_SENDER = "ticket_digital@mail.mercadona.com"

    /**
     * Identificador estable de un correo: su cabecera Message-ID o, si no tiene,
     * el UID de IMAP dentro de la carpeta (o, en último caso, su número).
     */
    fun stableMessageId(message: Message, folderName: String, uid: Long?): String {
        val headerId = runCatching { message.getHeader("Message-ID")?.firstOrNull()?.trim() }.getOrNull()
        if (!headerId.isNullOrBlank()) return headerId
        return if (uid != null && uid > 0) {
            "imap:$folderName:uid-$uid"
        } else {
            "imap:$folderName:num-${message.messageNumber}"
        }
    }

    /** Devuelve los PDF adjuntos de un correo (nombre, contenido), incluidos los de correos reenviados. */
    fun collectPdfAttachments(part: Part): List<Pair<String, ByteArray>> {
        val out = mutableListOf<Pair<String, ByteArray>>()
        collectInto(part, out)
        return out
    }

    private fun collectInto(part: Part, out: MutableList<Pair<String, ByteArray>>) {
        when {
            part.isMimeType("multipart/*") -> {
                val multipart = part.content as? Multipart ?: return
                for (i in 0 until multipart.count) {
                    collectInto(multipart.getBodyPart(i), out)
                }
            }

            part.isMimeType("message/rfc822") -> {
                val nested = part.content as? Part ?: return
                collectInto(nested, out)
            }

            else -> {
                val decodedName = part.fileName?.let { raw ->
                    runCatching { MimeUtility.decodeText(raw) }.getOrDefault(raw)
                }
                val isPdf = part.isMimeType("application/pdf") ||
                    (decodedName != null && decodedName.endsWith(".pdf", ignoreCase = true))
                if (isPdf) {
                    val bytes = part.inputStream.use { it.readBytes() }
                    if (bytes.isNotEmpty()) {
                        out.add((decodedName?.ifBlank { null } ?: "ticket.pdf") to bytes)
                    }
                }
            }
        }
    }
}
