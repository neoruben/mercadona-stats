package io.github.neoruben.mercadonastats.gmail

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Gestiona el flujo OAuth 2.0 con PKCE para Gmail usando exclusivamente el alcance
 * de solo lectura (gmail.readonly). No almacena credenciales en el código fuente.
 */
class GmailOAuthManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gmail_oauth_prefs"
        private const val KEY_CLIENT_ID = "oauth_client_id"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at_ms"
        private const val KEY_CODE_VERIFIER = "pkce_code_verifier"
        private const val KEY_REDIRECT_URI = "last_redirect_uri"

        const val GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }

    fun getClientId(defaultFromBuildConfig: String): String {
        val saved = prefs.getString(KEY_CLIENT_ID, null)?.trim().orEmpty()
        return if (saved.isNotEmpty()) saved else defaultFromBuildConfig.trim()
    }

    fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    fun isConnected(): Boolean {
        return !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank() ||
            !prefs.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
    }

    fun disconnect() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_CODE_VERIFIER)
            .apply()
    }

    /**
     * Construye el esquema inverso estándar de Google OAuth para Android:
     * `<ID>.apps.googleusercontent.com` -> `com.googleusercontent.apps.<ID>:/oauth2redirect`
     */
    fun buildRedirectUri(clientId: String): String {
        val clean = clientId.trim()
        return if (clean.endsWith(".apps.googleusercontent.com")) {
            val prefix = clean.removeSuffix(".apps.googleusercontent.com")
            "com.googleusercontent.apps.$prefix:/oauth2redirect"
        } else {
            "io.github.neoruben.mercadonastats://oauth2redirect"
        }
    }

    /**
     * Inicia el flujo OAuth 2.0 con PKCE en el navegador del sistema.
     */
    fun createAuthorizationIntent(clientId: String): Intent {
        saveClientId(clientId)
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val redirectUri = buildRedirectUri(clientId)

        prefs.edit()
            .putString(KEY_CODE_VERIFIER, codeVerifier)
            .putString(KEY_REDIRECT_URI, redirectUri)
            .apply()

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", GMAIL_READONLY_SCOPE)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()

        return Intent(Intent.ACTION_VIEW, authUri)
    }

    /**
     * Procesa el Intent de retorno (`oauth2redirect?code=...`) y canjea el código por tokens.
     */
    suspend fun handleRedirectUri(uri: Uri, defaultClientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Error de autorización OAuth: $error"))
        }

        val code = uri.getQueryParameter("code")
            ?: return@withContext Result.failure(IllegalArgumentException("No se recibió código de autorización en el redirect URI."))

        exchangeAuthorizationCode(code, defaultClientId)
    }

    /**
     * Permite canjear un código de autorización directamente (útil tanto desde el redirect automático
     * como si se introduce manualmente).
     */
    suspend fun exchangeAuthorizationCode(code: String, defaultClientId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val clientId = getClientId(defaultClientId)
            val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null)
                ?: return@withContext Result.failure(IllegalStateException("Falta el code_verifier PKCE. Vuelve a iniciar la conexión."))
            val redirectUri = prefs.getString(KEY_REDIRECT_URI, null) ?: buildRedirectUri(clientId)

            val params = mapOf(
                "client_id" to clientId,
                "code" to code.trim(),
                "code_verifier" to codeVerifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to redirectUri
            )

            val responseJson = postFormUrlEncoded(TOKEN_ENDPOINT, params)
            val accessToken = responseJson.optString("access_token")
            val refreshToken = responseJson.optString("refresh_token")
            val expiresIn = responseJson.optLong("expires_in", 3600L)

            if (accessToken.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Respuesta sin access_token: ${responseJson.optString("error_description", responseJson.toString())}")
                )
            }

            val editor = prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (expiresIn - 60) * 1000L)

            if (refreshToken.isNotBlank()) {
                editor.putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            editor.apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtiene un `access_token` válido, renovándolo automáticamente con el `refresh_token` si ha expirado.
     */
    suspend fun getValidAccessToken(defaultClientId: String): Result<String> = withContext(Dispatchers.IO) {
        val currentToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (!currentToken.isNullOrBlank() && System.currentTimeMillis() < expiresAt) {
            return@withContext Result.success(currentToken)
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Sesión expirada o no iniciada. Conecta tu cuenta de Gmail primero."))
        }

        try {
            val clientId = getClientId(defaultClientId)
            val params = mapOf(
                "client_id" to clientId,
                "refresh_token" to refreshToken,
                "grant_type" to "refresh_token"
            )
            val responseJson = postFormUrlEncoded(TOKEN_ENDPOINT, params)
            val newAccessToken = responseJson.optString("access_token")
            val expiresIn = responseJson.optLong("expires_in", 3600L)

            if (newAccessToken.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("No se pudo refrescar el token: ${responseJson.optString("error_description", responseJson.toString())}")
                )
            }

            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, newAccessToken)
                .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (expiresIn - 60) * 1000L)
                .apply()

            Result.success(newAccessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postFormUrlEncoded(urlString: String, params: Map<String, String>): JSONObject {
        val body = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 15_000
            readTimeout = 15_000
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
            writer.flush()
        }

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        val json = JSONObject(responseText)
        if (conn.responseCode !in 200..299) {
            val err = json.optString("error_description").ifBlank { json.optString("error", "HTTP ${conn.responseCode}") }
            throw IllegalStateException("Error OAuth (${conn.responseCode}): $err")
        }
        return json
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
