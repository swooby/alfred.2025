package com.swooby.alfred.util;

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
