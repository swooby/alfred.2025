package com.swooby.alfred.util

import java.math.BigInteger
import java.security.SecureRandom

object Ulids {
    private val rnd = SecureRandom()
    private val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private val THIRTY_TWO = BigInteger.valueOf(32L)

    /** Returns a 26-char ULID string. */
    fun newUlid(nowMs: Long = System.currentTimeMillis()): String {
        // 48-bit time (ms) + 80-bit randomness = 128 bits (16 bytes)
        val bytes = ByteArray(16)

        // Time into first 6 bytes (big-endian)
        var t = nowMs and ((1L shl 48) - 1)
        for (i in 5 downTo 0) {
            bytes[i] = (t and 0xFF).toByte()
            t = t ushr 8
        }

        // Random into last 10 bytes
        val rand = ByteArray(10).also { rnd.nextBytes(it) }
        System.arraycopy(rand, 0, bytes, 6, 10)

        // Encode 128-bit number as 26 Crockford base32 chars
        return encodeBase32(bytes)
    }

    private fun encodeBase32(bytes: ByteArray): String {
        // Interpret as positive BigInteger
        var n = BigInteger(1, bytes)
        val out = CharArray(26)
        // Produce least-significant digit last (right-aligned)
        for (i in 25 downTo 0) {
            val dr = n.divideAndRemainder(THIRTY_TWO)
            out[i] = ALPHABET[dr[1].toInt()]
            n = dr[0]
        }
        return String(out)
    }
}
