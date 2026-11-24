package io.github.neoruben.mercadonastats.mail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la cuenta IMAP en el propio teléfono.
 *
 * Servidor, correo y carpeta van en SharedPreferences. La contraseña de aplicación se guarda
 * cifrada con AES-GCM usando una clave del Android Keystore, que nunca sale del dispositivo.
 * La app tiene `allowBackup="false"`, así que tampoco se copia a la nube.
 */
class ImapSettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Devuelve la cuenta guardada, o `null` si no hay ninguna o no se puede descifrar. */
    fun load(): ImapSettings? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val folder = prefs.getString(KEY_FOLDER, null) ?: ImapSettings.DEFAULT_FOLDER
        val encrypted = prefs.getString(KEY_PASSWORD, null) ?: return null
        val password = runCatching { decrypt(encrypted) }
            .onFailure { Log.w(TAG, "No se pudo descifrar la contraseña IMAP: ${it.message}") }
            .getOrNull() ?: return null
        return ImapSettings(host = host, email = email, password = password, folder = folder)
    }

    fun save(settings: ImapSettings) {
        prefs.edit()
            .putString(KEY_HOST, settings.host)
            .putString(KEY_EMAIL, settings.email)
            .putString(KEY_FOLDER, settings.folder)
            .putString(KEY_PASSWORD, encrypt(settings.password))
            .apply()
    }

    /** Borra la cuenta y la clave de cifrado. */
    fun clear() {
        prefs.edit().clear().apply()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(stored: String): String {
        val iv = Base64.decode(stored.substringBefore(':'), Base64.NO_WRAP)
        val data = Base64.decode(stored.substringAfter(':'), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "ImapSettingsStore"
        const val PREFS_NAME = "imap_prefs"
        const val KEY_HOST = "host"
        const val KEY_EMAIL = "email"
        const val KEY_FOLDER = "folder"
        const val KEY_PASSWORD = "password_enc"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mercastats_imap_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
