/*
    This file is part of XPrivacyLua.

    XPrivacyLua is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    XPrivacyLua is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with XPrivacyLua.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2017-2019 Marcel Bokhorst (M66B)
 */

package eu.faircode.xlua;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

public class XParam {
    private static final String TAG = "XLua.XParam";

    private final Context context;
    private final Field field;
    private final XC_MethodHook.MethodHookParam param;
    private final Class<?>[] paramTypes;
    private final Class<?> returnType;
    private final Map<String, String> settings;

    private static final Map<Object, Map<String, Object>> nv = new WeakHashMap<>();

    // Field param
    public XParam(
            Context context,
            Field field,
            Map<String, String> settings) {
        this.context = context;
        this.field = field;
        this.param = null;
        this.paramTypes = null;
        this.returnType = field.getType();
        this.settings = settings;
    }

    // Method param
    public XParam(
            Context context,
            XC_MethodHook.MethodHookParam param,
            Map<String, String> settings) {
        this.context = context;
        this.field = null;
        this.param = param;
        if (param.method instanceof Constructor) {
            this.paramTypes = ((Constructor) param.method).getParameterTypes();
            this.returnType = null;
        } else {
            this.paramTypes = ((Method) param.method).getParameterTypes();
            this.returnType = ((Method) param.method).getReturnType();
        }
        this.settings = settings;
    }

    @SuppressWarnings("unused")
    public Context getApplicationContext() {
        return this.context;
    }

    @SuppressWarnings("unused")
    public String getPackageName() {
        return this.context.getPackageName();
    }

    @SuppressWarnings("unused")
    public int getUid() {
        return this.context.getApplicationInfo().uid;
    }

    @SuppressWarnings("unused")
    public Object getScope() {
        return this.param;
    }

    @SuppressWarnings("unused")
    public Object getThis() {
        if (this.field == null)
            return this.param.thisObject;
        else
            return null;
    }

    @SuppressWarnings("unused")
    public Object getArgument(int index) {
        if (index < 0 || index >= this.paramTypes.length)
            throw new ArrayIndexOutOfBoundsException("Argument #" + index);
        return this.param.args[index];
    }

    @SuppressWarnings("unused")
    public void setArgument(int index, Object value) {
        if (index < 0 || index >= this.paramTypes.length)
            throw new ArrayIndexOutOfBoundsException("Argument #" + index);

        if (value != null) {
            value = coerceValue(this.paramTypes[index], value);
            if (!boxType(this.paramTypes[index]).isInstance(value))
                throw new IllegalArgumentException(
                        "Expected argument #" + index + " " + this.paramTypes[index] + " got " + value.getClass());
        }

        this.param.args[index] = value;
    }

    @SuppressWarnings("unused")
    public Throwable getException() {
        Throwable ex = (this.field == null ? this.param.getThrowable() : null);
        if (BuildConfig.DEBUG)
            Log.i(TAG, "Get " + this.getPackageName() + ":" + this.getUid() + " result=" + ex.getMessage());
        return ex;
    }

    @SuppressWarnings("unused")
    public Object getResult() throws Throwable {
        Object result = (this.field == null ? this.param.getResult() : this.field.get(null));
        if (BuildConfig.DEBUG)
            Log.i(TAG, "Get " + this.getPackageName() + ":" + this.getUid() + " result=" + result);
        return result;
    }

    @SuppressWarnings("unused")
    public void setResult(Object result) throws Throwable {
        if (this.field == null)
            if (result instanceof Throwable)
                this.param.setThrowable((Throwable) result);
            else {
                if (BuildConfig.DEBUG)
                    Log.i(TAG, "Set " + this.getPackageName() + ":" + this.getUid() + " result=" + result);
                if (result != null && this.returnType != null) {
                    result = coerceValue(this.returnType, result);
                    if (!boxType(this.returnType).isInstance(result))
                        throw new IllegalArgumentException(
                                "Expected return " + this.returnType + " got " + result.getClass());
                }
                this.param.setResult(result);
            }
        else
            this.field.set(null, result);
    }

    @SuppressWarnings("unused")
    public String getSetting(String name) {
        synchronized (this.settings) {
            String value = (this.settings.containsKey(name) ? this.settings.get(name) : null);
            Log.i(TAG, "Get setting " + this.getPackageName() + ":" + this.getUid() + " " + name + "=" + value);
            return value;
        }
    }

    @SuppressWarnings("unused")
    public void putValue(String name, Object value, Object scope) {
        Log.i(TAG, "Put value " + this.getPackageName() + ":" + this.getUid() + " " + name + "=" + value + " @" + scope);
        synchronized (nv) {
            if (!nv.containsKey(scope))
                nv.put(scope, new HashMap<String, Object>());
            nv.get(scope).put(name, value);
        }
    }

    @SuppressWarnings("unused")
    public Object getValue(String name, Object scope) {
        Object value = getValueInternal(name, scope);
        Log.i(TAG, "Get value " + this.getPackageName() + ":" + this.getUid() + " " + name + "=" + value + " @" + scope);
        return value;
    }

    private static Object getValueInternal(String name, Object scope) {
        synchronized (nv) {
            if (!nv.containsKey(scope))
                return null;
            if (!nv.get(scope).containsKey(name))
                return null;
            return nv.get(scope).get(name);
        }
    }

    private static Class<?> boxType(Class<?> type) {
        if (type == boolean.class)
            return Boolean.class;
        else if (type == byte.class)
            return Byte.class;
        else if (type == char.class)
            return Character.class;
        else if (type == short.class)
            return Short.class;
        else if (type == int.class)
            return Integer.class;
        else if (type == long.class)
            return Long.class;
        else if (type == float.class)
            return Float.class;
        else if (type == double.class)
            return Double.class;
        return type;
    }

    private static Object coerceValue(Class<?> type, Object value) {
        // TODO: check for null primitives

        // Lua 5.2 auto converts numbers into floating or integer values
        if (Integer.class.equals(value.getClass())) {
            if (long.class.equals(type))
                return (long) (int) value;
            else if (float.class.equals(type))
                return (float) (int) value;
            else if (double.class.equals(type))
                return (double) (int) value;
        } else if (Double.class.equals(value.getClass())) {
            if (float.class.equals(type))
                return (float) (double) value;
        }

        return value;
    }
}
