package com.smartfoo.android.core

import java.util.BitSet

class FooBitSet {
    companion object {
        const val BITS_PER_BYTE: Byte = 8
    }

    val length: Int
    private val bitset: BitSet

    constructor(value: Byte) {
        length = BITS_PER_BYTE.toInt()
        bitset = BitSet(length)

        // Walk through bytes and set the bits
        for (j in 0..<BITS_PER_BYTE) {
            if ((value.toInt() and (1 shl j)) != 0) {
                bitset.set(BITS_PER_BYTE + j)
            }
        }
    }

    constructor(bytes: ByteArray) {
        length = bytes.size * BITS_PER_BYTE
        bitset = BitSet(length)

        // Walk through bytes and set the bits
        for (i in bytes.indices) {
            for (j in 0..<BITS_PER_BYTE) {
                if ((bytes[i].toInt() and (1 shl j)) != 0) {
                    bitset.set(i * BITS_PER_BYTE + j)
                }
            }
        }
    }

    fun get(index: Int): Boolean = bitset.get(index)
}
