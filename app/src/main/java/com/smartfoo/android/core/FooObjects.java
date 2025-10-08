package com.smartfoo.android.core;

import java.util.Objects;

public class FooObjects
{
    private FooObjects()
    {
    }

    public static boolean equals(Object a, Object b)
    {
        return Objects.equals(a, b);
    }
}
