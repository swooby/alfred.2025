package com.smartfoo.android.core

import java.util.Objects

object FooObjects {
    fun equals(
        a: Any?,
        b: Any?,
    ): Boolean = Objects.equals(a, b)
}
