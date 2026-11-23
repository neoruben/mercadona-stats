package io.github.neoruben.mercadonastats.mail

/**
 * Datos de la cuenta de correo para leer los tickets por IMAP (siempre con TLS, puerto 993).
 *
 * @property host servidor IMAP, por ejemplo `imap.gmail.com`.
 * @property email dirección (usuario) de la cuenta.
 * @property password contraseña de aplicación (no la contraseña principal de la cuenta).
 * @property folder carpeta o etiqueta donde están los tickets (`INBOX` por defecto).
 */
data class ImapSettings(
    val host: String,
    val email: String,
    val password: String,
    val folder: String
) {
    val port: Int get() = IMAPS_PORT

    override fun toString(): String = "ImapSettings(host=$host, email=$email, folder=$folder)"

    companion object {
        const val IMAPS_PORT = 993
        const val DEFAULT_FOLDER = "INBOX"
    }
}

/** Servidores habituales para rellenar el campo «Servidor» con un toque. */
enum class ImapProvider(val label: String, val host: String) {
    GMAIL("Gmail", "imap.gmail.com"),
    ICLOUD("iCloud", "imap.mail.me.com");

    companion object {
        /** Sugiere el servidor a partir del dominio del correo. */
        fun guessFromEmail(email: String): ImapProvider? {
            val domain = email.substringAfter('@', "").lowercase()
            return when (domain) {
                "gmail.com", "googlemail.com" -> GMAIL
                "icloud.com", "me.com", "mac.com" -> ICLOUD
                else -> null
            }
        }
    }
}
