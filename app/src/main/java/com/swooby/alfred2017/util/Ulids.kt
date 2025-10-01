package com.swooby.alfred2017.util

import java.security.SecureRandom
import java.math.BigInteger

object Ulids {
    private val rnd = SecureRandom()
    fun newUlid(nowMs: Long = System.currentTimeMillis()): String {
        val time = nowMs and ((1L shl 48) - 1)
        val rand = ByteArray(10).also { rnd.nextBytes(it) }
        val buffer = ByteArray(16)
        for (i in 0 until 6) buffer[i] = ((time shr (8 * (5 - i))) and 0xFF).toByte()
        System.arraycopy(rand, 0, buffer, 6, 10)
        return toBase32Crockford(buffer)
    }
    private val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray()
    private fun toBase32Crockford(bytes: ByteArray): String {
        val bits = BigInteger(1, bytes).toString(2).padStart(128, '0')
        val sb = StringBuilder(26)
        for (i in 0 until 26) {
            val chunk = bits.substring(i*5, i*5+5)
            sb.append(ALPHABET[Integer.parseInt(chunk, 2)])
        }
        return sb.toString()
    }
}