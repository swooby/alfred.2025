package com.smartfoo.android.core;

import androidx.annotation.Nullable;

import java.util.Objects;

public class FooObjects
{
    private FooObjects()
    {
    }

    public static boolean equals(@Nullable Object a, @Nullable Object b)
    {
        return Objects.equals(a, b);
    }
}
