package com.privateai.camera.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackupKeyDerivationTest {

    @Test
    fun `deriveKey is deterministic - same password and salt produce same key`() {
        val password = "testPassword123!"
        val salt = ByteArray(BackupKeyDerivation.SALT_SIZE) { it.toByte() }

        val key1 = BackupKeyDerivation.deriveKey(password, salt)
        val key2 = BackupKeyDerivation.deriveKey(password, salt)

        assertArrayEquals(key1.encoded, key2.encoded)
    }

    @Test
    fun `deriveKey produces different keys for different passwords`() {
        val salt = ByteArray(BackupKeyDerivation.SALT_SIZE) { it.toByte() }

        val key1 = BackupKeyDerivation.deriveKey("password1", salt)
        val key2 = BackupKeyDerivation.deriveKey("password2", salt)

        assertFalse(
            "Different passwords must produce different keys",
            key1.encoded.contentEquals(key2.encoded)
        )
    }

    @Test
    fun `deriveKey produces different keys for different salts`() {
        val password = "samePassword"
        val salt1 = ByteArray(BackupKeyDerivation.SALT_SIZE) { 0x01 }
        val salt2 = ByteArray(BackupKeyDerivation.SALT_SIZE) { 0x02 }

        val key1 = BackupKeyDerivation.deriveKey(password, salt1)
        val key2 = BackupKeyDerivation.deriveKey(password, salt2)

        assertFalse(
            "Different salts must produce different keys",
            key1.encoded.contentEquals(key2.encoded)
        )
    }

    @Test
    fun `deriveKey with empty password does not throw`() {
        val salt = ByteArray(BackupKeyDerivation.SALT_SIZE) { it.toByte() }

        val key = BackupKeyDerivation.deriveKey("", salt)

        assertNotNull(key)
        assertEquals(32, key.encoded.size)
    }

    @Test
    fun `deriveKey produces 32-byte key for AES-256`() {
        val password = "somePassword"
        val salt = ByteArray(BackupKeyDerivation.SALT_SIZE) { 0xAB.toByte() }

        val key = BackupKeyDerivation.deriveKey(password, salt)

        assertEquals("AES-256 key must be 32 bytes", 32, key.encoded.size)
    }

    @Test
    fun `deriveKey algorithm is AES`() {
        val password = "test"
        val salt = ByteArray(BackupKeyDerivation.SALT_SIZE) { 0 }

        val key = BackupKeyDerivation.deriveKey(password, salt)

        assertEquals("AES", key.algorithm)
    }

    @Test
    fun `generateSalt returns correct size`() {
        val salt = BackupKeyDerivation.generateSalt()

        assertEquals(BackupKeyDerivation.SALT_SIZE, salt.size)
    }

    @Test
    fun `generateSalt produces unique salts`() {
        val salt1 = BackupKeyDerivation.generateSalt()
        val salt2 = BackupKeyDerivation.generateSalt()

        assertFalse(
            "Two generated salts should not be identical",
            salt1.contentEquals(salt2)
        )
    }

    @Test
    fun `SALT_SIZE constant is 16`() {
        assertEquals(16, BackupKeyDerivation.SALT_SIZE)
    }
}
