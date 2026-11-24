package io.github.neoruben.mercadonastats.mail

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.search.FromStringTerm
import java.io.Closeable
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties

/** Error de correo con un mensaje en español pensado para mostrar al usuario. */
class ImapException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Un correo de Mercadona encontrado en la carpeta, con su identificador estable. */
class MercadonaMail(val message: Message, val id: String)

/**
 * Lee los tickets de Mercadona de cualquier correo por IMAP (TLS, solo lectura).
 * Misma lógica que la versión Docker de MercaStats. Todas las funciones son bloqueantes:
 * llámalas desde `Dispatchers.IO`.
 */
class ImapMercadonaService {

    /** Conecta y abre la carpeta en solo lectura. Lanza [ImapException] con un mensaje claro. */
    fun open(settings: ImapSettings): ImapMailbox {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", settings.host)
            put("mail.imaps.port", settings.port.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "30000")
            put("mail.imaps.writetimeout", "15000")
        }
        val store = Session.getInstance(props).getStore("imaps")
        try {
            store.connect(settings.host, settings.port, settings.email, settings.password)
            val folder = store.getFolder(settings.folder)
            if (!folder.exists()) {
                throw ImapException(folderNotFoundMessage(store, settings.folder))
            }
            folder.open(Folder.READ_ONLY)
            return ImapMailbox(store, folder, settings.folder)
        } catch (e: Exception) {
            runCatching { if (store.isConnected) store.close() }
            throw friendly(e, settings.host)
        }
    }

    private fun folderNotFoundMessage(store: Store, folderName: String): String {
        val available = runCatching {
            store.defaultFolder.list("*")
                .filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }
                .map { it.fullName }
                .sorted()
                .take(15)
        }.getOrDefault(emptyList())
        return buildString {
            append("La carpeta «$folderName» no existe en este correo.")
            if (available.isNotEmpty()) {
                append(" Carpetas disponibles: ")
                append(available.joinToString(", "))
                append(".")
            }
        }
    }

    companion object {
        /** Convierte errores técnicos en mensajes comprensibles. */
        fun friendly(e: Throwable, host: String): ImapException {
            if (e is ImapException) return e
            val chain = generateSequence(e) { it.cause }.toList()
            val message = when {
                chain.any { it is AuthenticationFailedException } ->
                    "El correo ha rechazado el usuario o la contraseña. Usa una contraseña de aplicación, " +
                        "no la contraseña normal de tu cuenta, y comprueba que el acceso IMAP está activado."
                chain.any { it is UnknownHostException } ->
                    "No se encuentra el servidor «$host». Revisa el nombre y la conexión a internet."
                chain.any { it is SocketTimeoutException || it is ConnectException } ->
                    "No se ha podido conectar con «$host». Revisa la conexión a internet."
                else -> "Error de correo: ${e.message ?: e.javaClass.simpleName}"
            }
            return ImapException(message, e)
        }
    }
}

/** Carpeta abierta en solo lectura. Ciérrala con [close] al terminar. */
class ImapMailbox internal constructor(
    private val store: Store,
    private val folder: Folder,
    private val folderName: String
) : Closeable {

    /** Busca los correos del remitente de Mercadona y calcula su identificador estable. */
    fun findMercadonaMails(): List<MercadonaMail> {
        val messages = folder.search(FromStringTerm(ImapMessageParts.MERCADONA_SENDER))
        if (messages.isEmpty()) return emptyList()

        val uidFolder = folder as? UIDFolder
        val profile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add("Message-ID")
            if (uidFolder != null) add(UIDFolder.FetchProfileItem.UID)
        }
        folder.fetch(messages, profile)

        return messages.map { message ->
            val uid = runCatching { uidFolder?.getUID(message) }.getOrNull()
            MercadonaMail(message, ImapMessageParts.stableMessageId(message, folderName, uid))
        }
    }

    /** Descarga los PDF adjuntos de un correo. */
    fun readPdfs(mail: MercadonaMail): List<Pair<String, ByteArray>> =
        ImapMessageParts.collectPdfAttachments(mail.message)

    override fun close() {
        runCatching { if (folder.isOpen) folder.close(false) }
        runCatching { if (store.isConnected) store.close() }
    }
}
