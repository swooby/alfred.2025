package com.smartfoo.android.core.reflection

import com.smartfoo.android.core.FooString
import com.smartfoo.android.core.logging.FooLog

@Suppress("unused")
object FooReflectionUtils {
    private val TAG = FooLog.TAG(FooReflectionUtils::class.java)

    fun getClass(o: Any?): Class<*>? = o as? Class<*> ?: o?.javaClass

    fun getClassName(o: Any?): String? = getClassName(getClass(o))

    fun <T> getClassName(c: Class<T?>?): String? = getClassName(c?.getName(), true)

    fun getClassName(
        className: String?,
        shortClassName: Boolean,
    ): String? {
        var className = className
        if (FooString.isNullOrEmpty(className)) {
            className = "null"
        }
        return if (shortClassName) className!!.substring(className.lastIndexOf('.') + 1) else className
    }

    fun getShortClassName(className: String?): String? = getClassName(className, true)

    fun getShortClassName(o: Any?): String? = getShortClassName(o?.javaClass)

    fun <T> getShortClassName(c: Class<T?>?): String? = FooReflectionUtils.getShortClassName(c?.getName())

    fun getMethodName(methodName: String?): String {
        var methodName = methodName
        if (methodName == null) {
            methodName = "()"
        }
        if (methodName.compareTo("()") != 0) {
            methodName = ".$methodName"
        }
        return methodName
    }

    fun getShortClassAndMethodName(
        o: Any?,
        methodName: String?,
    ): String = getShortClassName(o) + getMethodName(methodName)

    fun <T> getInstanceSignature(instance: T): String {
        val sb = StringBuilder()
        val instanceClass = instance!!.javaClass
        getClassSignature(instanceClass, sb)
        getInterfaceSignature(instanceClass, sb)
        return sb
            .toString()
            // Remove any unspeakable/unprintable characters
            //noinspection TrimLambda
            .trim { it <= ' ' }
    }

    fun getClassSignature(
        instanceClass: Class<*>,
        sb: StringBuilder,
    ) {
        val instanceSubclasses = instanceClass.getClasses()
        if (instanceSubclasses.size > 0) {
            sb.append(" extends")
            for (i in instanceSubclasses.indices) {
                if (i > 0) {
                    sb.append(", ")
                }
                sb.append(' ').append(instanceSubclasses[i])
            }
        }
    }

    fun getInterfaceSignature(
        instanceClass: Class<*>,
        sb: StringBuilder,
    ) {
        val instanceInterfaces = instanceClass.getInterfaces()
        if (instanceInterfaces.size > 0) {
            sb.append(" implements")
            for (i in instanceInterfaces.indices) {
                if (i > 0) {
                    sb.append(", ")
                }
                sb.append(' ').append(instanceInterfaces[i])
            }
        }
    }

    fun isAssignableFrom(
        instanceExpected: Any?,
        instanceActual: Any?,
    ): Boolean {
        val expectedInstanceClass = getClass(instanceExpected)
        if (expectedInstanceClass == null) {
            return false
        }

        val actualInstanceClass = getClass(instanceActual)
        if (actualInstanceClass == null) {
            return false
        }

        //
        // Verify that actualInstanceClass is an instance of all subclasses and interfaces of expectedClass…
        //
        if (!expectedInstanceClass.isInterface) {
            val expectedSubclasses = expectedInstanceClass.getClasses()
            for (expectedSubclass in expectedSubclasses) {
                if (!expectedSubclass.isAssignableFrom(actualInstanceClass)) {
                    return false
                }
            }
        }

        val expectedInterfaces = expectedInstanceClass.getInterfaces()
        for (expectedInterface in expectedInterfaces) {
            if (!expectedInterface.isAssignableFrom(actualInstanceClass)) {
                return false
            }
        }

        return true
    }

    fun getFieldValue(
        o: Any?,
        fieldName: String,
    ): Any? {
        var fieldValue: Any? = null
        val c = getClass(o)
        if (c != null) {
            try {
                val field = c.getField(fieldName)
                try {
                    fieldValue = field.get(c)
                    //FooLog.v(TAG, "getFieldValue: fieldValue == " + fieldValue);
                } catch (e: IllegalAccessException) {
                    FooLog.w(TAG, "getFieldValue: get", e)
                }
            } catch (e: NoSuchFieldException) {
                FooLog.w(TAG, "getFieldValue: getField", e)
            }
        }
        return fieldValue
    }

    fun getFieldValueString(
        o: Any?,
        fieldName: String,
    ): String? = getFieldValue(o, fieldName) as String?
}
