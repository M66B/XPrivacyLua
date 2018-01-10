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

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;

public class XParam {
    private static final String TAG = "XLua.XParam";

    private String packageName;
    private int uid;
    private XC_MethodHook.MethodHookParam param;
    private Class<?>[] paramTypes;
    private Class<?> returnType;
    private ClassLoader loader;

    private static final Map<Object, Map<String, Object>> nv = new WeakHashMap<>();

    public XParam(
            String packageName, int uid,
            XC_MethodHook.MethodHookParam param,
            Class<?>[] paramTypes, Class<?> returnType,
            ClassLoader loader) {
        this.packageName = packageName;
        this.uid = uid;
        this.param = param;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
        this.loader = loader;
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
        return this.param.thisObject;
    }

    @SuppressWarnings("unused")
    public Object getArgument(int index) {
        return this.param.args[index];
    }

    @SuppressWarnings("unused")
    public void setArgument(int index, Object value) {
        if (index < 0 || index >= this.paramTypes.length)
            throw new ArrayIndexOutOfBoundsException("Argument #" + index);
        if (value != null && !this.paramTypes[index].isInstance(value))
            throw new IllegalArgumentException(
                    "Expected argument #" + index + " " + this.paramTypes[index] + " got " + value);
        this.param.args[index] = value;
    }

    @SuppressWarnings("unused")
    public boolean hasException() {
        Log.i(TAG, "Throwable=" + this.param.getThrowable());
        return (this.param.getThrowable() != null);
    }

    @SuppressWarnings("unused")
    public Object getResult() throws Throwable {
        return this.param.getResult();
    }

    @SuppressWarnings("unused")
    public void setResult(Object result) {
        if (result instanceof Throwable)
            this.param.setThrowable((Throwable) result);
        else {
            Log.i(TAG, "Set " + this.packageName + ":" + this.uid + " result=" + result);
            if (result != null && !this.returnType.isInstance(result))
                throw new IllegalArgumentException("Expected return " + this.returnType + " got " + result);
            this.param.setResult(result);
        }
    }

    @SuppressWarnings("unused")
    public void putValue(String name, Object value) {
        Log.i(TAG, "Put value " + this.packageName + ":" + this.uid + " " + name + "=" + value);
        synchronized (nv) {
            if (!nv.containsKey(this.param.thisObject))
                nv.put(this.param.thisObject, new HashMap<String, Object>());
            nv.get(this.param.thisObject).put(name, value);
        }
    }

    @SuppressWarnings("unused")
    public Object getValue(String name) {
        Object value = getValueInternal(name);
        Log.i(TAG, "Get value " + this.packageName + ":" + this.uid + " " + name + "=" + value);
        return value;
    }

    private Object getValueInternal(String name) {
        synchronized (nv) {
            if (!nv.containsKey(this.param.thisObject))
                return null;
            if (!nv.get(this.param.thisObject).containsKey(name))
                return null;
            return nv.get(this.param.thisObject).get(name);
        }
    }
}
