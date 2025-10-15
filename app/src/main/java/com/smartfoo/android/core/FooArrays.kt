package com.smartfoo.android.core

import java.util.Arrays
import java.util.Vector

@Suppress("unused")
object FooArrays {
    @JvmStatic
    fun isNullOrEmpty(array: Array<Any>?): Boolean = array == null || array.isEmpty()

    @JvmStatic
    fun equals(
        a: ByteArray?,
        b: ByteArray?,
    ): Boolean = a.contentEquals(b)

    @JvmStatic
    fun copy(
        source: ByteArray,
        sourceOffset: Int,
        destination: ByteArray,
        destinationOffset: Int,
        count: Int,
    ): ByteArray {
        System.arraycopy(source, sourceOffset, destination, destinationOffset, count)
        return destination
    }

    @JvmStatic
    fun copy(
        source: ByteArray,
        offset: Int,
        count: Int,
    ): ByteArray {
        val destination = ByteArray(count)
        System.arraycopy(source, offset, destination, 0, count)
        return destination
    }

    @JvmStatic
    fun fill(
        array: ByteArray,
        element: Byte,
        offset: Int,
        length: Int,
    ): ByteArray {
        Arrays.fill(array, offset, length, element)
        return array
    }

    @JvmStatic
    fun <T> sort(
        values: Array<T>,
        comparator: FooComparator<T>,
    ): Array<T> {
        Arrays.sort<T>(values, comparator)
        return values
    }

    @JvmStatic
    fun <T> sort(
        vector: Vector<T>,
        comparator: FooComparator<T>,
    ): Vector<T> {
        vector.sortWith(comparator)
        return vector
    }
}
