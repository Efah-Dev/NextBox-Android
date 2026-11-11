package dev.efah.nextbox.data

import android.util.Base64
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for backup files: AES-256-GCM with a key derived by
 * PBKDF2-HMAC-SHA256. A backup holds node passwords and subscription URLs, so
 * a file that leaves the phone should not be readable by whoever finds it.
 */
object BackupCrypto {
    private const val ITERATIONS = 210_000

    @Serializable
    private data class Envelope(
        val app: String = "NextBox",
        val encrypted: Boolean = true,
        val kdf: String = "PBKDF2-HMAC-SHA256",
        val iterations: Int = ITERATIONS,
        val salt: String,
        val iv: String,
        val data: String,
    )

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plain: String, password: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toByteArray())
        return NbJson.encodeToString(
            Envelope.serializer(),
            Envelope(salt = b64(salt), iv = b64(iv), data = b64(data)),
        )
    }

    /** Whether text is a password protected backup. */
    fun isEncrypted(text: String): Boolean =
        text.contains("\"encrypted\"") && runCatching { NbJson.decodeFromString(Envelope.serializer(), text).encrypted }.getOrDefault(false)

    /** Returns the backup JSON; throws when the password is wrong or the file is damaged. */
    fun decrypt(text: String, password: String): String {
        val e = NbJson.decodeFromString(Envelope.serializer(), text)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, unb64(e.salt), e.iterations.coerceIn(1, 5_000_000)), GCMParameterSpec(128, unb64(e.iv)))
        return String(cipher.doFinal(unb64(e.data)))
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
