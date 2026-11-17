package io.github.neoruben.mercadonastats.gmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

data class PdfAttachmentInfo(
    val filename: String,
    val attachmentId: String,
    val sizeBytes: Int
)

data class MercadonaEmailItem(
    val messageId: String,
    val subject: String,
    val dateHeader: String,
    val fromHeader: String,
    val snippet: String,
    val pdfAttachments: List<PdfAttachmentInfo>,
    val pdfLinksInBody: List<String>
)

data class MercadonaSearchResult(
    val queryUsed: String,
    val totalMessagesMatched: Int,
    val messagesWithPdfAttachment: Int,
    val messagesWithPdfLink: Int,
    val emails: List<MercadonaEmailItem>
)

/**
 * Servicio que consulta la API REST de Gmail para buscar correos de Mercadona
 * (`from:ticket_digital@mail.mercadona.com`) y localizar sus tickets PDF adjuntos (o enlaces).
 */
class GmailMercadonaService {

    companion object {
        const val DEFAULT_MERCADONA_QUERY = "from:ticket_digital@mail.mercadona.com"
        private const val GMAIL_API_BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    }

    suspend fun listMercadonaMessageIds(
        accessToken: String,
        query: String = DEFAULT_MERCADONA_QUERY
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val allMessageIds = mutableListOf<String>()
            var pageToken: String? = null

            do {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val urlBuilder = StringBuilder("$GMAIL_API_BASE/messages?q=$encodedQuery&maxResults=100")
                if (!pageToken.isNullOrBlank()) {
                    urlBuilder.append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
                }

                val listJson = getJson(urlBuilder.toString(), accessToken)
                val messagesArray = listJson.optJSONArray("messages")
                if (messagesArray != null) {
                    for (i in 0 until messagesArray.length()) {
                        val id = messagesArray.getJSONObject(i).optString("id")
                        if (id.isNotBlank()) {
                            allMessageIds.add(id)
                        }
                    }
                }
                pageToken = listJson.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (pageToken != null)

            Result.success(allMessageIds)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessageDetails(
        accessToken: String,
        msgId: String
    ): MercadonaEmailItem = withContext(Dispatchers.IO) {
        val msgJson = getJson("$GMAIL_API_BASE/messages/$msgId?format=full", accessToken)
        val snippet = msgJson.optString("snippet", "")
        val payload = msgJson.optJSONObject("payload")

        var subject = "(Sin asunto)"
        var dateHeader = ""
        var fromHeader = ""

        val headers = payload?.optJSONArray("headers")
        if (headers != null) {
            for (i in 0 until headers.length()) {
                val h = headers.getJSONObject(i)
                when (h.optString("name").lowercase()) {
                    "subject" -> subject = h.optString("value", subject)
                    "date" -> dateHeader = h.optString("value", "")
                    "from" -> fromHeader = h.optString("value", "")
                }
            }
        }

        val pdfAttachments = mutableListOf<PdfAttachmentInfo>()
        val pdfLinks = mutableSetOf<String>()

        if (payload != null) {
            extractAttachmentsAndLinks(payload, pdfAttachments, pdfLinks)
        }

        MercadonaEmailItem(
            messageId = msgId,
            subject = subject,
            dateHeader = dateHeader,
            fromHeader = fromHeader,
            snippet = snippet,
            pdfAttachments = pdfAttachments,
            pdfLinksInBody = pdfLinks.toList()
        )
    }

    suspend fun downloadPdfAttachment(
        accessToken: String,
        messageId: String,
        attachmentId: String
    ): ByteArray = withContext(Dispatchers.IO) {
        val json = getJson(
            "$GMAIL_API_BASE/messages/$messageId/attachments/$attachmentId",
            accessToken
        )
        val dataBase64Url = json.optString("data", "")
        if (dataBase64Url.isBlank()) {
            throw IllegalStateException("El adjunto PDF está vacío.")
        }
        Base64.getUrlDecoder().decode(dataBase64Url)
    }

    suspend fun searchMercadonaEmails(
        accessToken: String,
        query: String = DEFAULT_MERCADONA_QUERY,
        maxDetailsToInspect: Int = 100
    ): Result<MercadonaSearchResult> = withContext(Dispatchers.IO) {
        try {
            val allMessageIds = listMercadonaMessageIds(accessToken, query).getOrThrow()
            val emails = allMessageIds.take(maxDetailsToInspect).map { msgId ->
                getMessageDetails(accessToken, msgId)
            }

            val withAttachmentCount = emails.count { it.pdfAttachments.isNotEmpty() }
            val withLinkCount = emails.count { it.pdfLinksInBody.isNotEmpty() }

            Result.success(
                MercadonaSearchResult(
                    queryUsed = query,
                    totalMessagesMatched = allMessageIds.size,
                    messagesWithPdfAttachment = withAttachmentCount,
                    messagesWithPdfLink = withLinkCount,
                    emails = emails
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractAttachmentsAndLinks(
        part: JSONObject,
        attachmentsOut: MutableList<PdfAttachmentInfo>,
        linksOut: MutableSet<String>
    ) {
        val filename = part.optString("filename", "")
        val mimeType = part.optString("mimeType", "").lowercase()
        val body = part.optJSONObject("body")
        val attachmentId = body?.optString("attachmentId", "").orEmpty()
        val size = body?.optInt("size", 0) ?: 0

        val isPdf = mimeType == "application/pdf" || filename.endsWith(".pdf", ignoreCase = true)
        if (isPdf && attachmentId.isNotBlank()) {
            attachmentsOut.add(
                PdfAttachmentInfo(
                    filename = filename.ifBlank { "ticket.pdf" },
                    attachmentId = attachmentId,
                    sizeBytes = size
                )
            )
        }

        // Si el cuerpo tiene texto o HTML inline, buscamos posibles enlaces a PDFs por si algún correo no trae adjunto
        val dataBase64Url = body?.optString("data", "").orEmpty()
        if (dataBase64Url.isNotBlank() && (mimeType.startsWith("text/plain") || mimeType.startsWith("text/html"))) {
            try {
                val decoded = String(Base64.getUrlDecoder().decode(dataBase64Url), Charsets.UTF_8)
                val urlRegex = Regex("""https?://[^\s"'<>]+\.pdf(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
                urlRegex.findAll(decoded).forEach { match ->
                    linksOut.add(match.value)
                }
            } catch (_: Exception) {
                // Ignorar errores de decodificación en partes inline
            }
        }

        val subParts: JSONArray? = part.optJSONArray("parts")
        if (subParts != null) {
            for (i in 0 until subParts.length()) {
                extractAttachmentsAndLinks(subParts.getJSONObject(i), attachmentsOut, linksOut)
            }
        }
    }

    private fun getJson(urlString: String, accessToken: String): JSONObject {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        val json = JSONObject(text)
        if (conn.responseCode !in 200..299) {
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message") ?: text
            throw IllegalStateException("Error Gmail API (${conn.responseCode}): $message")
        }
        return json
    }
}
