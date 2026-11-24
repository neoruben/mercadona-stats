package io.github.neoruben.mercadonastats.mail

import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import jakarta.activation.DataHandler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Properties

class ImapMessagePartsTest {

    private val session: Session = Session.getInstance(Properties())
    private val fakePdf = "%PDF-1.4 ticket de prueba".toByteArray()

    private fun pdfPart(name: String, bytes: ByteArray = fakePdf, mimeType: String = "application/pdf") =
        MimeBodyPart().apply {
            dataHandler = DataHandler(ByteArrayDataSource(bytes, mimeType))
            fileName = name
        }

    private fun textPart(text: String) = MimeBodyPart().apply { setText(text, "UTF-8") }

    /** Escribe y vuelve a leer el correo, como si llegase del servidor. */
    private fun roundTrip(message: MimeMessage): MimeMessage {
        message.saveChanges()
        val out = ByteArrayOutputStream()
        message.writeTo(out)
        return MimeMessage(session, out.toByteArray().inputStream())
    }

    private fun mercadonaMail(vararg parts: MimeBodyPart, messageId: String? = "<ticket-1@example.com>"): MimeMessage {
        val message = object : MimeMessage(session) {
            override fun updateMessageID() {
                if (messageId != null) setHeader("Message-ID", messageId) else removeHeader("Message-ID")
            }
        }
        message.setFrom(InternetAddress(ImapMessageParts.MERCADONA_SENDER))
        message.subject = "Ticket de compra"
        message.setContent(MimeMultipart().apply { parts.forEach { addBodyPart(it) } })
        return roundTrip(message)
    }

    @Test
    fun findsPdfAttachment() {
        val mail = mercadonaMail(textPart("Gracias por tu compra"), pdfPart("ticket.pdf"))

        val pdfs = ImapMessageParts.collectPdfAttachments(mail)

        assertEquals(1, pdfs.size)
        assertEquals("ticket.pdf", pdfs[0].first)
        assertArrayEquals(fakePdf, pdfs[0].second)
    }

    @Test
    fun findsPdfByFileNameEvenWithGenericMimeType() {
        val mail = mercadonaMail(pdfPart("factura.PDF", mimeType = "application/octet-stream"))

        val pdfs = ImapMessageParts.collectPdfAttachments(mail)

        assertEquals(1, pdfs.size)
        assertEquals("factura.PDF", pdfs[0].first)
    }

    @Test
    fun findsPdfInsideForwardedMessage() {
        val original = mercadonaMail(pdfPart("ticket.pdf"))
        val forwardedPart = MimeBodyPart().apply { setContent(original, "message/rfc822") }
        val forward = mercadonaMail(textPart("Te reenvío el ticket"), forwardedPart, messageId = "<fwd@example.com>")

        val pdfs = ImapMessageParts.collectPdfAttachments(forward)

        assertEquals(1, pdfs.size)
        assertArrayEquals(fakePdf, pdfs[0].second)
    }

    @Test
    fun returnsEveryPdfAndIgnoresOtherAttachments() {
        val mail = mercadonaMail(
            pdfPart("ticket-1.pdf"),
            pdfPart("logo.png", byteArrayOf(1, 2, 3), "image/png"),
            pdfPart("ticket-2.pdf", "%PDF-1.4 otro".toByteArray())
        )

        val names = ImapMessageParts.collectPdfAttachments(mail).map { it.first }

        assertEquals(listOf("ticket-1.pdf", "ticket-2.pdf"), names)
    }

    @Test
    fun noPdfMeansEmptyList() {
        val mail = mercadonaMail(textPart("Sin adjuntos"))

        assertTrue(ImapMessageParts.collectPdfAttachments(mail).isEmpty())
    }

    @Test
    fun stableIdUsesMessageIdHeader() {
        val mail = mercadonaMail(pdfPart("ticket.pdf"), messageId = "<abc-123@example.com>")

        assertEquals("<abc-123@example.com>", ImapMessageParts.stableMessageId(mail, "INBOX", 42L))
    }

    @Test
    fun stableIdFallsBackToUidWithoutMessageId() {
        val mail = mercadonaMail(pdfPart("ticket.pdf"), messageId = null)

        assertEquals("imap:Tickets/Mercadona:uid-42", ImapMessageParts.stableMessageId(mail, "Tickets/Mercadona", 42L))
    }
}
