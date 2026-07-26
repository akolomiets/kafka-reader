package com.npk.kfv.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CipherServiceTest {

    @Test
    fun `encrypt and decrypt`() {
        val encrypted = CipherService.encrypt("text to encrypt")
        assertTrue(encrypted.startsWith("cipher:"))

        val decrypted = CipherService.decrypt(encrypted)
        assertEquals("text to encrypt", decrypted)
    }

    @Test
    fun `decrypt plain text`() {
        val decrypted = CipherService.decrypt("plain text")
        assertEquals("plain text", decrypted)
    }

}