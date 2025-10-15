package com.smartfoo.android.core

import kotlin.math.min

@Suppress("unused")
open class FooMemoryStream
    @JvmOverloads
    constructor(
        capacity: Int = BLOCK_SIZE,
    ) {
        companion object {
            val EMPTY_BUFFER = ByteArray(0)
            const val BLOCK_SIZE = 256

            protected fun checkOffset(
                size: Int,
                buffer: ByteArray,
                offset: Int,
                length: Int, //
                checkParameters: Boolean = true,
                throwException: Boolean = true,
            ): Boolean {
                if (checkParameters) {
                    requireNotNull(buffer) { "buffer must not be null" }
                    require(length <= buffer.size) { "length($length) must be <= buffer.length(${buffer.size})" }
                    require(!(offset < 0 || offset >= length)) {
                        "offset($offset) must be >= 0 and < (length($length) or buffer.length(${buffer.size}))"
                    }
                }

                if (offset + size > length) {
                    if (throwException) {
                        throw IndexOutOfBoundsException(
                            "attempted to read $size bytes past offset($offset) would exceed length($length)",
                        )
                    }
                    return false
                }
                return true
            }

            private fun unsignedByteToInt(value: Byte): Int = value.toInt() and 0xff

            private fun unsignedByteToInt(
                value: Byte,
                leftShift: Int,
            ): Int = unsignedByteToInt(value) shl leftShift

            fun newBytes(value: Byte): ByteArray =
                byteArrayOf(
                    value,
                )

            fun newBytes(value: Short): ByteArray =
                byteArrayOf(
                    (value.toInt() and 0xFF).toByte(),
                    ((value.toInt() shr 8) and 0xFF).toByte(),
                )

            fun newBytes(value: Int): ByteArray =
                byteArrayOf(
                    (value and 0xFF).toByte(),
                    ((value shr 8) and 0xFF).toByte(),
                    ((value shr 16) and 0xFF).toByte(),
                    ((value shr 24) and 0xFF).toByte(),
                )

            fun newBytes(value: Long): ByteArray =
                byteArrayOf(
                    (value and 0xFFL).toByte(),
                    ((value shr 8) and 0xFFL).toByte(),
                    ((value shr 16) and 0xFFL).toByte(),
                    ((value shr 24) and 0xFFL).toByte(),
                    ((value shr 32) and 0xFFL).toByte(),
                    ((value shr 40) and 0xFFL).toByte(),
                    ((value shr 48) and 0xFFL).toByte(),
                    ((value shr 56) and 0xFFL).toByte(),
                )
        }

        @get:Synchronized
        var buffer = EMPTY_BUFFER // never null
            protected set
        private var position = 0
        private var length = 0

        init {
            makeSpaceFor(capacity)
        }

        @Synchronized
        fun reset() {
            setLength(0)
            //setPosition(0);
        }

        @Synchronized
        fun clear() {
            buffer = EMPTY_BUFFER
            reset()
        }

        @get:Synchronized
        val capacity: Int
            get() = buffer.size

        @Synchronized
        fun getPosition(): Int = this.position

        @Synchronized
        fun setPosition(position: Int) {
            makeSpaceFor(position)
            this.position = position
        }

        @Synchronized
        fun incPosition(amount: Int): Int {
            setPosition(getPosition() + amount)
            return getPosition()
        }

        @Synchronized
        fun getLength(): Int = this.length

        @Synchronized
        fun setLength(length: Int) {
            require(length >= 0) { "length must be >= 0" }

            makeSpaceFor(length)

            // this.buffer can contain previously used data
            // if length > this.length, re-zero any length added to the end.
            FooArrays.fill(this.buffer, 0.toByte(), this.length, length)

            this.length = length

            if (this.position > this.length) {
                this.position = this.length
            }
        }

        @Synchronized
        fun incLength(amount: Int): Int {
            setLength(getLength() + amount)
            return getPosition()
        }

        @Synchronized
        protected fun makeSpaceFor(size: Int): Boolean {
            var size = size
            if (size <= buffer.size) {
                // already big enough, do nothing
                // this also handles the size <= 0 case
                return false
            }

            val remainder = size % BLOCK_SIZE
            size = size / BLOCK_SIZE * BLOCK_SIZE
            if (remainder > 0) {
                size += BLOCK_SIZE
            }
            if (size == 0) {
                return false
            }

            // only need to copy the bytes in the array that are actually used
            // TODO: Seems a little heavy to do in a method that is called by every write method
            buffer = FooArrays.copy(buffer, 0, ByteArray(size), 0, length)

            // position and length remain unchanged
            return true
        }

        private fun extendLengthToPosition() {
            if (position > length) {
                length = position
            }
        }

        @Synchronized
        fun write(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ) {
            makeSpaceFor(position + length)
            FooArrays.copy(buffer, offset, buffer, position, length)
            position += length
            extendLengthToPosition()
        }

        @Synchronized
        fun writeInt8(value: Byte) {
            makeSpaceFor(position + 1)
            buffer[position++] = value
            extendLengthToPosition()
        }

        @Synchronized
        fun writeUInt8(value: Short) {
            require((value.toInt() shr 8) == 0) {
                "value is not a uint8: 0x${FooString.toHexString(value, 4)}"
            }
            makeSpaceFor(position + 1)
            buffer[position++] = value.toByte()
            extendLengthToPosition()
        }

        @Synchronized
        fun writeInt16(value: Short) {
            makeSpaceFor(position + 2)
            buffer[position++] = (value.toInt() shr 8).toByte()
            buffer[position++] = value.toByte()
            extendLengthToPosition()
        }

        @Synchronized
        fun writeUInt16(value: Int) {
            require((value shr 16) == 0) {
                "value is not a uint16: 0x${FooString.toHexString(value, 8)}"
            }
            makeSpaceFor(position + 2)
            buffer[position++] = (value shr 8).toByte()
            buffer[position++] = value.toByte()
            extendLengthToPosition()
        }

        @Synchronized
        fun writeInt32(value: Long) {
            makeSpaceFor(position + 4)
            buffer[position++] = (value shr 24).toByte()
            buffer[position++] = (value shr 16).toByte()
            buffer[position++] = (value shr 8).toByte()
            buffer[position++] = value.toByte()
            extendLengthToPosition()
        }

        @Synchronized
        fun writeUInt32(value: Long) {
            require((value shr 32) == 0L) {
                "value is not a uint32: 0x${FooString.toHexString(value, 16)}"
            }
            makeSpaceFor(position + 4)
            buffer[position++] = (value shr 24).toByte()
            buffer[position++] = (value shr 16).toByte()
            buffer[position++] = (value shr 8).toByte()
            buffer[position++] = value.toByte()
            extendLengthToPosition()
        }

        @Synchronized
        fun writeString(value: String?) {
            if (value != null && !value.isEmpty()) {
                val b = FooString.getBytes(value)
                makeSpaceFor(position + b.size + 1) // null terminated
                write(b, 0, b.size)
            }
            writeUInt8(0.toShort())
            extendLengthToPosition()
        }

        @Synchronized
        fun read(
            dest: ByteArray,
            offset: Int,
            count: Int,
        ): Int {
            var count = count
            count = min(count, length - position)
            FooArrays.copy(buffer, position, dest, offset, count)
            position += count
            return count
        }

        @Synchronized
        fun readInt8(): Byte {
            checkOffset(1, buffer, position, length)
            return buffer[position++]
        }

        @Synchronized
        fun readUInt8(): Short {
            checkOffset(1, buffer, position, length)
            return unsignedByteToInt(buffer[position++]).toShort()
        }

        @Synchronized
        fun readInt16(): Short {
            checkOffset(2, buffer, position, length)
            var value = unsignedByteToInt(buffer[position++], 8)
            value += unsignedByteToInt(buffer[position++])
            return value.toShort()
        }

        @Synchronized
        fun readUInt16(): Int {
            checkOffset(2, buffer, position, length)
            var value = unsignedByteToInt(buffer[position++], 8)
            value += unsignedByteToInt(buffer[position++])
            return value
        }

        @Synchronized
        fun readInt32(): Int {
            checkOffset(4, buffer, position, length)
            var value = unsignedByteToInt(buffer[position++], 24)
            value += unsignedByteToInt(buffer[position++], 16)
            value += unsignedByteToInt(buffer[position++], 8)
            value += unsignedByteToInt(buffer[position++])
            return value
        }

        @Synchronized
        fun readUInt32(): Long {
            checkOffset(4, buffer, position, length)
            var value = unsignedByteToInt(buffer[position++], 24).toLong()
            value += unsignedByteToInt(buffer[position++], 16).toLong()
            value += unsignedByteToInt(buffer[position++], 8).toLong()
            value += unsignedByteToInt(buffer[position++]).toLong()
            return value
        }

        @Synchronized
        fun readString(): String {
            val index = position
            while (checkOffset(
                    1,
                    buffer,
                    position,
                    length,
                ) &&
                buffer[position].toInt() != 0
            ) {
                position++
            }
            position++ // null terminated
            return FooString.getString(buffer, index, position - index - 1)
        }

        @Synchronized
        fun toDebugString(): String = "($length):${FooString.toHexString(buffer, 0, length)}"
    }
