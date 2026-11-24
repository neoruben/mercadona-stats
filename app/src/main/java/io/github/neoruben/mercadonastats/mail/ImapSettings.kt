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

/** Servidores habituales para rellenar el campo «Servidor» con un toque, con su ayuda. */
enum class ImapProvider(
    val label: String,
    val host: String,
    /** Pasos para crear la contraseña de aplicación. */
    val passwordSteps: List<String>,
    /** Página donde se crea. */
    val passwordUrl: String
) {
    GMAIL(
        label = "Gmail",
        host = "imap.gmail.com",
        passwordSteps = listOf(
            "Activa la verificación en dos pasos en tu cuenta de Google (es obligatoria).",
            "Abre la página de contraseñas de aplicación con el botón de abajo.",
            "Escribe un nombre, por ejemplo MercaStats, y pulsa «Crear».",
            "Copia las 16 letras que aparecen y pégalas aquí (los espacios dan igual)."
        ),
        passwordUrl = "https://myaccount.google.com/apppasswords"
    ),
    ICLOUD(
        label = "iCloud",
        host = "imap.mail.me.com",
        passwordSteps = listOf(
            "Abre tu cuenta de Apple con el botón de abajo e inicia sesión.",
            "Entra en «Inicio de sesión y seguridad» → «Contraseñas específicas de app».",
            "Crea una con el nombre MercaStats.",
            "Pégala aquí tal cual, con los guiones. En «Correo» pon tu dirección @icloud.com."
        ),
        passwordUrl = "https://account.apple.com/account/manage"
    );

    companion object {
        private val MICROSOFT_DOMAINS = setOf(
            "outlook.com", "outlook.es", "hotmail.com", "hotmail.es", "live.com", "live.es", "msn.com"
        )

        /** Sugiere el servidor a partir del dominio del correo. */
        fun guessFromEmail(email: String): ImapProvider? {
            val domain = email.substringAfter('@', "").lowercase()
            return when (domain) {
                "gmail.com", "googlemail.com" -> GMAIL
                "icloud.com", "me.com", "mac.com" -> ICLOUD
                else -> null
            }
        }

        fun fromHost(host: String): ImapProvider? =
            entries.firstOrNull { it.host.equals(host, ignoreCase = true) }

        /**
         * Las cuentas personales de Microsoft (Outlook, Hotmail, Live) ya no admiten IMAP con
         * contraseña, ni siquiera con contraseña de aplicación: solo con inicio de sesión OAuth.
         */
        fun isMicrosoftPersonal(email: String): Boolean =
            email.substringAfter('@', "").lowercase() in MICROSOFT_DOMAINS
    }
}
