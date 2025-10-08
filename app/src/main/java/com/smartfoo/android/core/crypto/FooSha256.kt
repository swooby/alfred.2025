package com.smartfoo.android.core.crypto

import java.security.MessageDigest

object FooSha256 {
    private val HEX_DIGITS: CharArray = "0123456789abcdef".toCharArray()

    fun sha256(input: String): String {
        return sha256(input.toByteArray(Charsets.UTF_8))
    }

    fun sha256(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input)
        val chars = CharArray(bytes.size * 2)
        var index = 0
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            chars[index++] = HEX_DIGITS[value ushr 4]
            chars[index++] = HEX_DIGITS[value and 0x0F]
        }
        return chars.concatToString()
    }
}