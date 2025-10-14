package com.smartfoo.android.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object FooCrypto {
    /**
     * Needs to match https://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html#KeyGenerator
     */
    const val HMACSHA256 = "HmacSHA256"

    /**
     * Need to match https://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html#MessageDigest
     */
    const val SHA256 = "SHA-256"

    fun HMACSHA256(
        key: ByteArray,
        buffer: ByteArray,
    ): ByteArray = HMACSHA256(key, buffer, 0, buffer.size)

    fun HMACSHA256(
        key: ByteArray,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): ByteArray {
        val signingKey = SecretKeySpec(key, HMACSHA256)
        val mac = Mac.getInstance(signingKey.algorithm)
        mac.init(signingKey)
        mac.update(buffer, offset, length)
        return mac.doFinal()
    }

    fun SHA256(buffer: ByteArray): ByteArray {
        if (buffer.isEmpty()) return byteArrayOf()
        return MessageDigest.getInstance(SHA256).digest(buffer)
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    fun SHA256(input: String): String {
        if (input.isEmpty()) return ""
        val bytes = SHA256(input.toByteArray(Charsets.UTF_8))
        val chars = CharArray(bytes.size * 2)
        var index = 0
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            chars[index++] = HEX_DIGITS[value ushr 4]
            chars[index++] = HEX_DIGITS[value and 0x0F]
        }
        return chars.concatToString()
    }

    val randomInt32: Int
        get() {
            val random = SecureRandom()
            return random.nextInt()
        }

    val randomInt64: Long
        get() {
            val random = SecureRandom()
            return random.nextLong()
        }

    fun getRandomBytes(count: Int): ByteArray {
        val bytes = ByteArray(count)
        val random = SecureRandom()
        random.nextBytes(bytes)
        return bytes
    }
}
