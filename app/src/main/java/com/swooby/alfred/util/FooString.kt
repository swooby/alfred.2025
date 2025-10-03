package com.swooby.alfred.util

object FooString {
    /**
     * Tests if a String value is null or empty.
     *
     * @param value the String value to test
     * @return true if the String is null, zero length, or ""
     */
    @JvmStatic
    fun isNullOrEmpty(value: String?): Boolean {
        return value.isNullOrEmpty()
    }

    @JvmStatic
    fun isNullOrEmpty(value: CharSequence?): Boolean {
        return value.isNullOrEmpty()
    }

    @JvmStatic
    fun toString(value: Any?): String? {
        return value?.toString()
    }

    /**
     * @param str1 str1
     * @param str2 str2
     * @return str1 != null ? str1.equals(str2) : str2 == null
     */
    fun equals(str1: String?, str2: String?): Boolean {
        return if (str1 != null) (str1 == str2) else str2 == null
    }

    /**
     * Identical to [.repr], but grammatically intended for Strings.
     *
     * @param value value
     * @return "null", or '\"' + value.toString + '\"', or value.toString()
     */
    fun quote(value: Any?): String {
        return repr(value, false)
    }

    /**
     * Identical to [.quote], but grammatically intended for Objects.
     *
     * @param value value
     * @return "null", or '\"' + value.toString + '\"', or value.toString()
     */
    fun repr(value: Any?): String {
        return repr(value, false)
    }

    /**
     * @param value    value
     * @param typeOnly typeOnly
     * @return "null", or '\"' + value.toString + '\"', or value.toString(), or getShortClassName(value)
     */
    fun repr(value: Any?, typeOnly: Boolean): String {
        if (value == null) {
            return "null"
        }

        if (typeOnly) {
            return FooReflectionUtils.getShortClassName(value)
        }

        if (value is String) {
            return "\"" + value.toString() + '\"'
        }

        if (value is CharSequence) {
            return "\"" + value.toString() + '\"'
        }

        return value.toString()
    }
}