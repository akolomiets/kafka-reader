package com.npk.kfv.service

import com.npk.kfv.ApplicationPrefs
import java.security.spec.KeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CipherService {

    private const val CIPHER_PREFIX =           "cipher:"
    private const val CIPHER_TRANSFORMATION =   "AES/GCM/NoPadding"

    private val secretKey: SecretKey by lazy {
        val appData = ApplicationPrefs.appData
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(appData.toCharArray(), appData.substring(12, 28).toByteArray(), 65536, 256)
        SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private val iv: GCMParameterSpec by lazy {
        val appData = ApplicationPrefs.appData
        GCMParameterSpec(128, appData.substring(42, 58).toByteArray())
    }

    fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        return CIPHER_PREFIX + Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray()))
    }

    fun decrypt(text: String): String =
        if (text.isCrypted()) {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
            val plainText = cipher.doFinal(Base64.getDecoder().decode(text.substring(7)))
            String(plainText)
        } else {
            text
        }

    fun shouldDecrypt(text: String) = text.isCrypted()

    private fun String.isCrypted() = startsWith(CIPHER_PREFIX)

}