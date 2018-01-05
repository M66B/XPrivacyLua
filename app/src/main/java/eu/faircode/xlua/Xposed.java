/*
    This file is part of XPrivacy/Lua.

    XPrivacy/Lua is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    XPrivacy/Lua is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with XPrivacy/Lua.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2017-2018 Marcel Bokhorst (M66B)
 */

package eu.faircode.xlua;

import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONArray;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Xposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static final String TAG = "XLua.Xposed";

    private XService service = null;

    public void initZygote(final IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        // Read hook definitions from asset file
        final List<XHook> hooks = readHooks(startupParam.modulePath);

        // Hook activity manager constructor
        Class<?> at = Class.forName("android.app.ActivityThread");
        XposedBridge.hookAllMethods(at, "systemMain", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    final ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    Class<?> clAM = Class.forName("com.android.server.am.ActivityManagerService", false, loader);

                    XposedBridge.hookAllConstructors(clAM, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // Create service, hook android
                            try {
                                service = new XService(param.thisObject, hooks, loader);
                                //hookPackage("android", loader);
                            } catch (Throwable ex) {
                                Log.e(TAG, Log.getStackTraceString(ex));
                                XposedBridge.log(ex);
                            }
                        }
                    });

                    XposedBridge.hookAllMethods(clAM, "systemReady", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // Initialize service
                            try {
                                if (service != null)
                                    service.systemReady();
                            } catch (Throwable ex) {
                                Log.e(TAG, Log.getStackTraceString(ex));
                                XposedBridge.log(ex);
                            }
                        }
                    });

                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    XposedBridge.log(ex);
                }
            }
        });
    }

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (Process.myUid() == Process.SYSTEM_UID)
            return;
        hookPackage(lpparam.packageName, lpparam.classLoader);
    }

    private void hookPackage(final String pkg, ClassLoader loader) {
        try {
            final int uid = Process.myUid();
            final IService client = XService.getClient();
            if (client == null) {
                int userid = Util.getUserId(uid);
                int start = Util.getUserUid(userid, 99000);
                int end = Util.getUserUid(userid, 99999);
                if (uid >= start && uid <= end) {
                    Log.w(TAG, "Isolated process pkg=" + pkg + ":" + uid);
                    return;
                } else
                    throw new Throwable("Service not running pkg=" + pkg + ":" + uid);
            }

            for (final XHook hook : client.getAssignedHooks(pkg, uid))
                try {
                    // Compile script
                    InputStream is = new ByteArrayInputStream(hook.getLuaScript().getBytes());
                    final Prototype script = LuaC.instance.compile(is, "script");

                    // Get class
                    Class<?> cls = Class.forName(hook.getClassName(), false, loader);
                    String[] m = hook.getMethodName().split(":");
                    if (m.length > 1) {
                        Field field = cls.getField(m[0]);
                        Object obj = field.get(null);
                        cls = obj.getClass();
                    }

                    // Get parameter types
                    String[] p = hook.getParameterTypes();
                    Class<?>[] params = new Class[p.length];
                    for (int i = 0; i < p.length; i++)
                        params[i] = resolveClass(p[i], loader);

                    // Get return type
                    Class<?> ret = resolveClass(hook.getReturnType(), loader);

                    // Get method
                    Method method = cls.getDeclaredMethod(m[m.length - 1], params);

                    // Check return type
                    if (!method.getReturnType().equals(ret))
                        throw new Throwable("Invalid return type got " + method.getReturnType() + " expected " + ret);

                    // Hook method
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            execute(param, "before");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            execute(param, "after");
                        }

                        // Execute hook
                        private void execute(MethodHookParam param, String function) {
                            try {
                                // Initialize LUA runtime
                                Globals globals = JsePlatform.standardGlobals();
                                LuaClosure closure = new LuaClosure(script, globals);
                                closure.call();

                                // Check if function exists
                                LuaValue func = globals.get(function);
                                if (!func.isnil()) {
                                    // Setup globals
                                    globals.set("log", new OneArgFunction() {
                                        @Override
                                        public LuaValue call(LuaValue arg) {
                                            Log.i(TAG, arg.checkjstring());
                                            return LuaValue.NIL;
                                        }
                                    });

                                    // Run function
                                    Varargs result = func.invoke(
                                            CoerceJavaToLua.coerce(hook),
                                            CoerceJavaToLua.coerce(new XParam(pkg, uid, param))
                                    );

                                    // Report use
                                    Bundle data = new Bundle();
                                    data.putString("function", function);
                                    data.putInt("restricted", result.arg1().checkboolean() ? 1 : 0);
                                    client.report(hook.getId(), pkg, uid, "use", data);
                                }
                            } catch (Throwable ex) {
                                Log.e(TAG, Log.getStackTraceString(ex));

                                // Report use error
                                try {
                                    Bundle data = new Bundle();
                                    data.putString("function", function);
                                    data.putString("exception", ex.toString());
                                    data.putString("stacktrace", Log.getStackTraceString(ex));
                                    client.report(hook.getId(), pkg, uid, "use", data);
                                } catch (RemoteException ignored) {
                                }
                            }
                        }
                    });

                    // Report install
                    client.report(hook.getId(), pkg, uid, "install", new Bundle());
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));

                    // Report install error
                    try {
                        Bundle data = new Bundle();
                        data.putString("exception", ex.toString());
                        data.putString("stacktrace", Log.getStackTraceString(ex));
                        client.report(hook.getId(), pkg, uid, "install", data);
                    } catch (RemoteException ignored) {
                    }
                }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            XposedBridge.log(ex);
        }
    }

    private static Class<?> resolveClass(String name, ClassLoader loader) throws ClassNotFoundException {
        if ("int".equals(name))
            return int.class;
        else if ("long".equals(name))
            return long.class;
        else if ("void".equals(name))
            return Void.TYPE;
        else
            return Class.forName(name, false, loader);
    }

    private static List<XHook> readHooks(String apk) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(apk);
            ZipEntry zipEntry = zipFile.getEntry("assets/hooks.json");
            if (zipEntry == null)
                throw new Throwable("assets/hooks.json not found in " + apk);

            InputStream is = null;
            try {
                is = zipFile.getInputStream(zipEntry);
                String json = new Scanner(is).useDelimiter("\\A").next();
                List<XHook> hooks = new ArrayList<>();
                JSONArray jarray = new JSONArray(json);
                for (int i = 0; i < jarray.length(); i++) {
                    XHook hook = XHook.fromJSONObject(jarray.getJSONObject(i));
                    if (Build.VERSION.SDK_INT < hook.getMinSdk() || Build.VERSION.SDK_INT > hook.getMaxSdk())
                        continue;

                    // Link script
                    String script = hook.getLuaScript();
                    if (script.startsWith("@")) {
                        ZipEntry luaEntry = zipFile.getEntry("assets/" + script.substring(1) + ".lua");
                        if (luaEntry == null)
                            Log.e(TAG, script + " not found for " + hook.getId());
                        else {
                            InputStream lis = null;
                            try {
                                lis = zipFile.getInputStream(luaEntry);
                                script = new Scanner(lis).useDelimiter("\\A").next();
                                hook.setLuaScript(script);
                            } finally {
                                if (lis != null)
                                    try {
                                        lis.close();
                                    } catch (IOException ignored) {
                                    }
                            }
                        }
                    }

                    if (hook.isEnabled())
                        hooks.add(hook);
                }
                return hooks;
            } finally {
                if (is != null)
                    try {
                        is.close();
                    } catch (IOException ignored) {
                    }
            }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
        } finally {
            if (zipFile != null)
                try {
                    zipFile.close();
                } catch (IOException ignored) {
                }
        }
        return null;
    }
}
