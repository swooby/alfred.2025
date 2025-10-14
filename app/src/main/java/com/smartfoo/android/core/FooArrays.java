package com.smartfoo.android.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Vector;

@SuppressWarnings("unused")
public class FooArrays
{
    private FooArrays()
    {
    }

    public static boolean isNullOrEmpty(@Nullable Object[] array)
    {
        return array == null || array.length == 0;
    }

    public static boolean equals(@Nullable byte[] a, @Nullable byte[] b)
    {
        return Arrays.equals(a, b);
    }

    public static void copy(@NonNull byte[] source, int sourceOffset, @NonNull byte[] destination, int destinationOffset, int count)
    {
        System.arraycopy(source, sourceOffset, destination, destinationOffset, count);
    }

    public static @NonNull byte[] copy(@NonNull byte[] source, int offset, int count)
    {
        byte[] destination = new byte[count];
        System.arraycopy(source, offset, destination, 0, count);
        return destination;
    }

    public static void fill(@NonNull byte[] array, byte element, int offset, int length)
    {
        //Arrays.fill(array, element, offset, length);
        for (int i = offset; i < length; i++)
        {
            array[i] = element;
        }
    }

    public static <T> void sort(@NonNull T[] values, @NonNull FooComparator<T> comparator)
    {
        Arrays.sort(values, comparator);
    }

    public static <T> void sort(@NonNull Vector<T> vector, @NonNull FooComparator<T> comparator)
    {
        @SuppressWarnings("unchecked")
        T[] temp = (T[]) new Object[vector.size()];
        vector.toArray(temp);
        sort(temp, comparator);

        int i = 0;
        Enumeration<T> enumeration = vector.elements();
        while (enumeration.hasMoreElements())
        {
            enumeration.nextElement();
            vector.setElementAt(temp[i], i++);
        }
    }
}
