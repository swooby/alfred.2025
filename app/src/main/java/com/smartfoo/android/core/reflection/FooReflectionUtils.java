package com.smartfoo.android.core.reflection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.smartfoo.android.core.FooString;
import com.smartfoo.android.core.logging.FooLog;

import java.lang.reflect.Field;

public class FooReflectionUtils
{
    private static final String TAG = FooLog.TAG(FooReflectionUtils.class);

    private FooReflectionUtils()
    {
    }

    @Nullable
    public static Class<?> getClass(@Nullable Object o)
    {
        return (o instanceof Class<?>) ? (Class<?>) o : (o != null ? o.getClass() : null);
    }

    @Nullable
    public static String getClassName(@Nullable Object o)
    {
        return getClassName(getClass(o));
    }

    @Nullable
    public static <T> String getClassName(@Nullable Class<T> c)
    {
        return getClassName(c == null ? null : c.getName(), true);
    }

    @Nullable
    public static String getClassName(@Nullable String className, boolean shortClassName)
    {
        if (FooString.isNullOrEmpty(className))
        {
            className = "null";
        }
        //noinspection DataFlowIssue
        return shortClassName ? className.substring(className.lastIndexOf('.') + 1) : className;
    }

    @Nullable
    public static String getShortClassName(@Nullable String className)
    {
        return getClassName(className, true);
    }

    @Nullable
    public static String getShortClassName(@Nullable Object o)
    {
        Class<?> c = (o == null) ? null : o.getClass();
        return getShortClassName(c);
    }

    @Nullable
    public static <T> String getShortClassName(@Nullable Class<T> c)
    {
        String className = (c == null) ? null : c.getName();
        return getShortClassName(className);
    }

    @NonNull
    public static String getMethodName(@Nullable String methodName)
    {
        if (methodName == null)
        {
            methodName = "()";
        }
        if (methodName.compareTo("()") != 0)
        {
            methodName = "." + methodName;
        }
        return methodName;
    }

    @NonNull
    public static String getShortClassAndMethodName(@Nullable Object o, @Nullable String methodName)
    {
        return getShortClassName(o) + getMethodName(methodName);
    }

    @NonNull
    public static <T> String getInstanceSignature(@NonNull T instance)
    {
        //FooRun.throwIllegalArgumentExceptionIfNull(instance, "instance");

        StringBuilder sb = new StringBuilder();

        Class<?> instanceClass = instance.getClass();

        Class<?>[] instanceSubclasses = instanceClass.getClasses();
        if (instanceSubclasses.length > 0)
        {
            sb.append(" extends");
            Class<?> instanceSubclass;
            for (int i = 0; i < instanceSubclasses.length; i++)
            {
                if (i > 0)
                {
                    sb.append(", ");
                }
                instanceSubclass = instanceSubclasses[i];
                sb.append(' ').append(instanceSubclass);
            }
        }

        Class<?>[] instanceInterfaces = instanceClass.getInterfaces();
        if (instanceInterfaces.length > 0)
        {
            sb.append(" implements");
            Class<?> instanceInterface;
            for (int i = 0; i < instanceInterfaces.length; i++)
            {
                if (i > 0)
                {
                    sb.append(", ");
                }
                instanceInterface = instanceInterfaces[i];
                sb.append(' ').append(instanceInterface);
            }
        }

        return sb.toString().trim();
    }

    public static boolean isAssignableFrom(@Nullable Object instanceExpected, @Nullable Object instanceActual)
    {
        //FooRun.throwIllegalArgumentExceptionIfNull(instanceExpected, "instanceExpected");

        Class<?> expectedInstanceClass = getClass(instanceExpected);
        if (expectedInstanceClass == null)
        {
            return false;
        }

        Class<?> actualInstanceClass = getClass(instanceActual);
        if (actualInstanceClass == null)
        {
            return false;
        }

        //
        // Verify that actualInstanceClass is an instance of all subclasses and interfaces of expectedClass…
        //

        if (!expectedInstanceClass.isInterface())
        {
            Class<?>[] expectedSubclasses = expectedInstanceClass.getClasses();
            for (Class<?> expectedSubclass : expectedSubclasses)
            {
                if (!expectedSubclass.isAssignableFrom(actualInstanceClass))
                {
                    return false;
                }
            }
        }

        Class<?>[] expectedInterfaces = expectedInstanceClass.getInterfaces();
        for (Class<?> expectedInterface : expectedInterfaces)
        {
            if (!expectedInterface.isAssignableFrom(actualInstanceClass))
            {
                return false;
            }
        }

        return true;
    }

    @Nullable
    public static Object getFieldValue(@Nullable Object o, @NonNull String fieldName)
    {
        Object fieldValue = null;

        Class<?> c = getClass(o);
        if (c != null)
        {
            try
            {
                Field field = c.getField(fieldName);

                try
                {
                    fieldValue = field.get(c);
                    //FooLog.v(TAG, "getFieldValue: fieldValue == " + fieldValue);
                }
                catch (IllegalAccessException e)
                {
                    FooLog.w(TAG, "getFieldValue: get", e);
                }
            }
            catch (NoSuchFieldException e)
            {
                FooLog.w(TAG, "getFieldValue: getField", e);
            }
        }

        return fieldValue;
    }

    @Nullable
    public static String getFieldValueString(@Nullable Object o, @NonNull String fieldName)
    {
        return (String) getFieldValue(o, fieldName);
    }
}
