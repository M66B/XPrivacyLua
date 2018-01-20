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

    Copyright 2017-2018 Marcel Bokhorst (M66B)
 */

package eu.faircode.xlua;

import android.util.Log;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

public class XParam {
    private static final String TAG = "XLua.XParam";

    private final String packageName;
    private final int uid;
    private final Field field;
    private final XC_MethodHook.MethodHookParam param;
    private final Class<?>[] paramTypes;
    private final Class<?> returnType;
    private final ClassLoader loader;
    private final Map<String, String> settings;

    private static final Map<Object, Map<String, Object>> nv = new WeakHashMap<>();

    // Field param
    public XParam(
            String packageName, int uid,
            Field field,
            Class<?>[] paramTypes, Class<?> returnType, ClassLoader loader,
            Map<String, String> settings) {
        this.packageName = packageName;
        this.uid = uid;
        this.field = field;
        this.param = null;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
        this.loader = loader;
        this.settings = settings;
    }

    // Method param
    public XParam(
            String packageName, int uid,
            XC_MethodHook.MethodHookParam param,
            Class<?>[] paramTypes, Class<?> returnType, ClassLoader loader,
            Map<String, String> settings) {
        this.packageName = packageName;
        this.uid = uid;
        this.field = null;
        this.param = param;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
        this.loader = loader;
        this.settings = settings;
    }

    @SuppressWarnings("unused")
    public String getPackageName() {
        return this.packageName;
    }

    @SuppressWarnings("unused")
    public int getUid() {
        return this.uid;
    }

    @SuppressWarnings("unused")
    public ClassLoader getClassLoader() {
        return this.loader;
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

        try {
            value = coerceValue(this.paramTypes[index], value);
        } catch (Throwable IllegalArgumentException) {
            throw new IllegalArgumentException(
                    "Expected argument #" + index + " " + this.paramTypes[index] + " got " + value.getClass());
        }

        this.param.args[index] = value;
    }

    @SuppressWarnings("unused")
    public boolean hasException() {
        if (this.field == null)
            return false;

        boolean has = (this.param.getThrowable() != null);
        if (has)
            Log.i(TAG, this.packageName + ":" + this.uid + " " + param.method.getName() +
                    " throwable=" + this.param.getThrowable());
        return has;
    }

    @SuppressWarnings("unused")
    public Object getResult() throws Throwable {
        Object result = (this.field == null ? this.param.getResult() : this.field.get(null));
        if (BuildConfig.DEBUG)
            Log.i(TAG, "Get " + this.packageName + ":" + this.uid + " result=" + result);
        return result;
    }

    @SuppressWarnings("unused")
    public void setResult(Object result) throws Throwable {
        if (this.field == null)
            if (result instanceof Throwable)
                this.param.setThrowable((Throwable) result);
            else {
                if (BuildConfig.DEBUG)
                    Log.i(TAG, "Set " + this.packageName + ":" + this.uid + " result=" + result);
                if (result != null && !boxType(this.returnType).isInstance(result))
                    throw new IllegalArgumentException(
                            "Expected return " + this.returnType + " got " + result.getClass());
                this.param.setResult(result);
            }
        else
            this.field.set(null, result);
    }

    @SuppressWarnings("unused")
    public String getSetting(String name) {
        synchronized (this.settings) {
            String value = (this.settings.containsKey(name) ? this.settings.get(name) : null);
            Log.i(TAG, "Get setting " + this.packageName + ":" + this.uid + " " + name + "=" + value);
            return value;
        }
    }

    @SuppressWarnings("unused")
    public void putValue(String name, Object value, Object scope) {
        Log.i(TAG, "Put value " + this.packageName + ":" + this.uid + " " + name + "=" + value + " @" + scope);
        synchronized (nv) {
            if (!nv.containsKey(scope))
                nv.put(scope, new HashMap<String, Object>());
            nv.get(scope).put(name, value);
        }
    }

    @SuppressWarnings("unused")
    public Object getValue(String name, Object scope) {
        Object value = getValueInternal(name, scope);
        Log.i(TAG, "Get value " + this.packageName + ":" + this.uid + " " + name + "=" + value + " @" + scope);
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
        if (value == null)
            return null;

        // Lua 5.2 auto converts numbers into floating or integer values
        if (Integer.class.equals(value.getClass())) {
            if (float.class.equals(type))
                return (float) (int) value;
            else if (double.class.equals(type))
                return (double) (int) value;
        } else if (Double.class.equals(value.getClass())) {
            if (float.class.equals(type))
                return (float) (double) value;
        }

        if (!boxType(type).isInstance(value))
            throw new IllegalArgumentException();
        return value;
    }
}
