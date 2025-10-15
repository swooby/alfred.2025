package com.smartfoo.android.core.collections

import com.smartfoo.android.core.FooObjects

object FooCollections {
    @JvmStatic
    fun <T> hashCode(items: Collection<T>): Int {
        var hashCode = 0
        for (item in items) {
            hashCode = 31 * hashCode + item.hashCode()
        }
        return hashCode
    }

    @JvmStatic
    fun <T> identical(
        a: Collection<T>?,
        b: Collection<T>?,
    ): Boolean {
        if (a === b) {
            return true
        }

        if (a == null || b == null) {
            return false
        }

        val length = a.size
        if (b.size != length) {
            return false
        }

        val itA = a.iterator()
        val itB = b.iterator()
        var identical = itA.hasNext() == itB.hasNext()
        while (identical && itA.hasNext() && itB.hasNext()) {
            identical = FooObjects.equals(itA.next(), itB.next())
            identical = identical and (itA.hasNext() == itB.hasNext())
        }

        return identical
    }

    /**
     * Order and duplicate independent comparison of two collections
     *
     * @param a
     * @param b
     * @param <T>
     * @return
     </T> */
    @JvmStatic
    fun <T> equivalent(
        a: Collection<T>,
        b: Collection<T>,
    ): Boolean {
        // TODO:(pv) Make this more efficient...

        if (false) {
            val t1 = a.containsAll(b)
            val t2 = b.containsAll(a)
            val result = t1 && t2
            return result
            //return a.containsAll(b) && b.containsAll(a);
            //return new TreeSet<>(a).equals(new TreeSet<>(b));
        } else {
            val a2 = LinkedHashSet(a)
            val b2 = LinkedHashSet(b)
            val result = a2 == b2
            return result
        }
    }
}
