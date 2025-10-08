package com.smartfoo.android.core.reflection;

import com.smartfoo.android.core.logging.FooLog;
import com.smartfoo.android.core.FooString;

import java.lang.reflect.Field;

public class FooReflectionUtils
{
    private static final String TAG = FooLog.TAG(FooReflectionUtils.class);

    private FooReflectionUtils()
    {
    }

    public static Class<?> getClass(Object o)
    {
        return (o instanceof Class<?>) ? (Class<?>) o : (o != null ? o.getClass() : null);
    }

    public static String getClassName(Object o)
    {
        return getClassName(getClass(o));
    }

    public static String getClassName(Class c)
    {
        return getClassName(c == null ? null : c.getName(), true);
    }

    public static String getClassName(String className, boolean shortClassName)
    {
        if (FooString.isNullOrEmpty(className))
        {
            className = "null";
        }
        return shortClassName ? className.substring(className.lastIndexOf('.') + 1) : className;
    }

    public static String getShortClassName(String className)
    {
        return getClassName(className, true);
    }

    public static String getShortClassName(Object o)
    {
        Class c = (o == null) ? null : o.getClass();
        return getShortClassName(c);
    }

    public static String getShortClassName(Class c)
    {
        String className = (c == null) ? null : c.getName();
        return getShortClassName(className);
    }

    public static String getMethodName(String methodName)
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

    public static String getShortClassAndMethodName(Object o, String methodName)
    {
        return getShortClassName(o) + getMethodName(methodName);
    }

    public static <T> String getInstanceSignature(T instance)
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

    public static boolean isAssignableFrom(Object instanceExpected, Object instanceActual)
    {
        //FooRun.throwIllegalArgumentExceptionIfNull(instanceExpected, "instanceExpected");

        if (instanceActual == null)
        {
            return false;
        }

        Class<?> expectedInstanceClass = getClass(instanceExpected);

        Class<?> actualInstanceClass = getClass(instanceActual);

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

    public static Object getFieldValue(Object o, String fieldName)
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

    public static String getFieldValueString(Object o, String fieldName)
    {
        return (String) getFieldValue(o, fieldName);
    }
}
