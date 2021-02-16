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

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Prototype;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XLua implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static final String TAG = "XLua.Xposed";

    private static int version = -1;
    private Timer timer = null;
    private final Map<String, Map<String, Bundle>> queue = new HashMap<>();

    public void initZygote(final IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        Log.i(TAG, "initZygote system=" + startupParam.startsSystemServer + " debug=" + BuildConfig.DEBUG);
    }

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        int uid = Process.myUid();
        Log.i(TAG, "Loaded " + lpparam.packageName + ":" + uid);
        XposedBridge.log(TAG + " Loaded " + lpparam.packageName + ":" + uid);

        if ("android".equals(lpparam.packageName))
            hookAndroid(lpparam);

        if ("com.android.providers.settings".equals(lpparam.packageName))
            hookSettings(lpparam);

        if (!"android".equals(lpparam.packageName) &&
                !lpparam.packageName.startsWith(BuildConfig.APPLICATION_ID))
            hookApplication(lpparam);
    }

    private void hookAndroid(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/ActivityManagerService.java
        Class<?> clsAM = Class.forName("com.android.server.am.ActivityManagerService", false, lpparam.classLoader);

        XposedBridge.hookAllMethods(clsAM, "systemReady", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Log.i(TAG, "Preparing system");
                    XposedBridge.log(TAG + " Preparing system");
                    Context context = getContext(param.thisObject);
                    hookPackage(lpparam, Process.myUid(), context);

                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    XposedBridge.log(ex);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Log.i(TAG, "System ready");
                    XposedBridge.log(TAG + " System ready");
                    Context context = getContext(param.thisObject);

                    // Store current module version
                    PackageInfo pi = context.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
                    version = pi.versionCode;
                    Log.i(TAG, "Module version " + version);

                    // public static UserManagerService getInstance()
                    Class<?> clsUM = Class.forName("com.android.server.pm.UserManagerService", false, param.thisObject.getClass().getClassLoader());
                    Object um = clsUM.getDeclaredMethod("getInstance").invoke(null);

                    //  public int[] getUserIds()
                    int[] userids = (int[]) um.getClass().getDeclaredMethod("getUserIds").invoke(um);

                    // Listen for package changes
                    for (int userid : userids) {
                        Log.i(TAG, "Registering package listener user=" + userid);
                        IntentFilter ifPackageAdd = new IntentFilter();
                        ifPackageAdd.addAction(Intent.ACTION_PACKAGE_ADDED);
                        ifPackageAdd.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
                        ifPackageAdd.addDataScheme("package");
                        Util.createContextForUser(context, userid).registerReceiver(new ReceiverPackage(), ifPackageAdd);
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    XposedBridge.log(ex);
                }
            }

            @NonNull
            private Context getContext(Object am) throws Throwable {
                // Searching for context
                Context context = null;
                Class<?> cAm = am.getClass();
                while (cAm != null && context == null) {
                    for (Field field : cAm.getDeclaredFields())
                        if (field.getType().equals(Context.class)) {
                            field.setAccessible(true);
                            context = (Context) field.get(am);
                            Log.i(TAG, "Context found in " + cAm + " as " + field.getName());
                            break;
                        }
                    cAm = cAm.getSuperclass();
                }
                if (context == null)
                    throw new Throwable("Context not found");

                return context;
            }
        });
    }

    private void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // https://android.googlesource.com/platform/frameworks/base/+/master/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java
        Class<?> clsSet = Class.forName("com.android.providers.settings.SettingsProvider", false, lpparam.classLoader);

        // Bundle call(String method, String arg, Bundle extras)
        Method mCall = clsSet.getMethod("call", String.class, String.class, Bundle.class);
        XposedBridge.hookMethod(mCall, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String method = (String) param.args[0];
                    String arg = (String) param.args[1];
                    Bundle extras = (Bundle) param.args[2];

                    if ("xlua".equals(method))
                        if ("getVersion".equals(arg)) {
                            Bundle result = new Bundle();
                            result.putInt("version", version);
                            param.setResult(result);
                        } else
                            try {
                                Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                                Context context = (Context) mGetContext.invoke(param.thisObject);
                                param.setResult(XProvider.call(context, arg, extras));
                            } catch (IllegalArgumentException ex) {
                                Log.i(TAG, "Error: " + ex.getMessage());
                                param.setThrowable(ex);
                            } catch (Throwable ex) {
                                Log.e(TAG, Log.getStackTraceString(ex));
                                XposedBridge.log(ex);
                                param.setResult(null);
                            }
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    XposedBridge.log(ex);
                }
            }
        });

        // Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
        Method mQuery = clsSet.getMethod("query", Uri.class, String[].class, String.class, String[].class, String.class);
        XposedBridge.hookMethod(mQuery, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    String[] projection = (String[]) param.args[1];
                    String[] selection = (String[]) param.args[3];
                    if (projection != null && projection.length > 0 &&
                            projection[0] != null && projection[0].startsWith("xlua.")) {
                        try {
                            Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) mGetContext.invoke(param.thisObject);
                            param.setResult(XProvider.query(context, projection[0].split("\\.")[1], selection));
                        } catch (Throwable ex) {
                            Log.e(TAG, Log.getStackTraceString(ex));
                            XposedBridge.log(ex);
                            param.setResult(null);
                        }
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    XposedBridge.log(ex);
                }
            }
        });
    }

    private void hookApplication(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        final int uid = Process.myUid();
        Class<?> at = Class.forName("android.app.LoadedApk", false, lpparam.classLoader);
        XposedBridge.hookAllMethods(at, "makeApplication", new XC_MethodHook() {
            private boolean made = false;

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (!made) {
                        made = true;
                        Context context = (Application) param.getResult();

                        // Check for isolate process
                        int userid = Util.getUserId(uid);
                        int start = Util.getUserUid(userid, 99000);
                        int end = Util.getUserUid(userid, 99999);
                        boolean isolated = (uid >= start && uid <= end);
                        if (isolated) {
                            Log.i(TAG, "Skipping isolated " + lpparam.packageName + ":" + uid);
                            return;
                        }

                        hookPackage(lpparam, uid, context);
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    XposedBridge.log(ex);
                }
            }
        });
    }

    private void hookPackage(final XC_LoadPackage.LoadPackageParam lpparam, int uid, final Context context) throws Throwable {
        // Get assigned hooks
        List<XHook> hooks = new ArrayList<>();
        Cursor chooks = null;
        try {
            chooks = context.getContentResolver()
                    .query(XProvider.getURI(), new String[]{"xlua.getAssignedHooks2"},
                            "pkg = ? AND uid = ?", new String[]{lpparam.packageName, Integer.toString(uid)},
                            null);
            while (chooks != null && chooks.moveToNext()) {
                byte[] marshaled = chooks.getBlob(0);
                Parcel parcel = Parcel.obtain();
                parcel.unmarshall(marshaled, 0, marshaled.length);
                parcel.setDataPosition(0);
                XHook hook = XHook.CREATOR.createFromParcel(parcel);
                parcel.recycle();
                hooks.add(hook);
            }
        } finally {
            if (chooks != null)
                chooks.close();
        }

        final Map<String, String> settings = new HashMap<>();

        // Get global settings
        Cursor csettings1 = null;
        try {
            csettings1 = context.getContentResolver()
                    .query(XProvider.getURI(), new String[]{"xlua.getSettings"},
                            "pkg = ? AND uid = ?", new String[]{"global", Integer.toString(uid)},
                            null);
            while (csettings1 != null && csettings1.moveToNext())
                settings.put(csettings1.getString(0), csettings1.getString(1));
        } finally {
            if (csettings1 != null)
                csettings1.close();
        }

        // Get app settings
        Cursor csettings2 = null;
        try {
            csettings2 = context.getContentResolver()
                    .query(XProvider.getURI(), new String[]{"xlua.getSettings"},
                            "pkg = ? AND uid = ?", new String[]{lpparam.packageName, Integer.toString(uid)},
                            null);
            while (csettings2 != null && csettings2.moveToNext())
                settings.put(csettings2.getString(0), csettings2.getString(1));
        } finally {
            if (csettings2 != null)
                csettings2.close();
        }

        Map<ScriptHolder, Prototype> scriptPrototype = new HashMap<>();

        // Apply hooks
        PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        for (final XHook hook : hooks)
            try {
                if (!hook.isAvailable(pi.versionCode))
                    continue;

                long install = SystemClock.elapsedRealtime();

                // Compile script
                final Prototype compiledScript;
                ScriptHolder sh = new ScriptHolder(hook.getLuaScript());
                if (scriptPrototype.containsKey(sh))
                    compiledScript = scriptPrototype.get(sh);
                else {
                    InputStream is = new ByteArrayInputStream(sh.script.getBytes());
                    compiledScript = LuaC.instance.compile(is, "script");
                    scriptPrototype.put(sh, compiledScript);
                }

                // Get class
                Class<?> cls = Class.forName(hook.getResolvedClassName(), false, context.getClassLoader());

                // Handle field method
                String methodName = hook.getMethodName();
                if (methodName != null) {
                    String[] m = methodName.split(":");
                    if (m.length > 1) {
                        Field field = cls.getField(m[0]);
                        Object obj = field.get(null);
                        cls = obj.getClass();
                    }
                    methodName = m[m.length - 1];
                }

                // Get parameter types
                String[] p = hook.getParameterTypes();
                final Class<?>[] paramTypes = new Class[p.length];
                for (int i = 0; i < p.length; i++)
                    paramTypes[i] = resolveClass(p[i], context.getClassLoader());

                // Get return type
                final Class<?> returnType = (hook.getReturnType() == null ? null :
                        resolveClass(hook.getReturnType(), context.getClassLoader()));

                if (methodName != null && methodName.startsWith("#")) {
                    // Get field
                    Field field = resolveField(cls, methodName.substring(1), returnType);
                    field.setAccessible(true);

                    if (paramTypes.length > 0)
                        throw new NoSuchFieldException("Field with parameters");

                    try {
                        long run = SystemClock.elapsedRealtime();

                        // Initialize Lua runtime
                        Globals globals = getGlobals(context, hook, settings);
                        LuaClosure closure = new LuaClosure(compiledScript, globals);
                        closure.call();

                        // Check if function exists
                        LuaValue func = globals.get("after");
                        if (func.isnil())
                            return;

                        LuaValue[] args = new LuaValue[]{
                                CoerceJavaToLua.coerce(hook),
                                CoerceJavaToLua.coerce(new XParam(context, field, settings))
                        };

                        // Run function
                        Varargs result = func.invoke(args);

                        // Report use
                        boolean restricted = result.arg1().checkboolean();
                        if (restricted && hook.doUsage()) {
                            Bundle data = new Bundle();
                            data.putString("function", "after");
                            data.putInt("restricted", restricted ? 1 : 0);
                            data.putLong("duration", SystemClock.elapsedRealtime() - run);
                            if (result.narg() > 1) {
                                data.putString("old", result.isnil(2) ? null : result.checkjstring(2));
                                data.putString("new", result.isnil(3) ? null : result.checkjstring(3));
                            }
                            report(context, hook.getId(), "after", "use", data);
                        }
                    } catch (Throwable ex) {
                        StringBuilder sb = new StringBuilder();

                        sb.append("Exception:\n");
                        sb.append(Log.getStackTraceString(ex));
                        sb.append("\n");

                        sb.append("\nPackage:\n");
                        sb.append(context.getPackageName());
                        sb.append(':');
                        sb.append(Integer.toString(context.getApplicationInfo().uid));
                        sb.append("\n");

                        sb.append("\nField:\n");
                        sb.append(field.toString());
                        sb.append("\n");

                        Log.e(TAG, sb.toString());

                        // Report use error
                        Bundle data = new Bundle();
                        data.putString("function", "after");
                        data.putString("exception", sb.toString());
                        report(context, hook.getId(), "after", "use", data);
                    }
                } else {
                    // Get method
                    final Member member = resolveMember(cls, methodName, paramTypes);

                    // Check return type
                    final Class<?> memberReturnType = (methodName == null ? null : ((Method) member).getReturnType());
                    if (returnType != null && memberReturnType != null && !memberReturnType.isAssignableFrom(returnType))
                        throw new Throwable("Invalid return type " + memberReturnType + " got " + returnType);

                    // Hook method
                    XposedBridge.hookMethod(member, new XC_MethodHook() {
                        private final WeakHashMap<Thread, Globals> threadGlobals = new WeakHashMap<>();

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
                                long run = SystemClock.elapsedRealtime();

                                // Initialize Lua runtime
                                LuaValue func;
                                LuaValue[] args;
                                synchronized (threadGlobals) {
                                    Thread thread = Thread.currentThread();
                                    if (!threadGlobals.containsKey(thread))
                                        threadGlobals.put(thread, getGlobals(context, hook, settings));
                                    Globals globals = threadGlobals.get(thread);

                                    // Define functions
                                    LuaClosure closure = new LuaClosure(compiledScript, globals);
                                    closure.call();

                                    // Check if function exists
                                    func = globals.get(function);
                                    if (func.isnil())
                                        return;

                                    // Build arguments
                                    args = new LuaValue[]{
                                            CoerceJavaToLua.coerce(hook),
                                            CoerceJavaToLua.coerce(new XParam(context, param, settings))
                                    };
                                }

                                // Run function
                                Varargs result = func.invoke(args);

                                // Report use
                                boolean restricted = result.arg1().checkboolean();
                                if (restricted && hook.doUsage()) {
                                    Bundle data = new Bundle();
                                    data.putString("function", function);
                                    data.putInt("restricted", restricted ? 1 : 0);
                                    data.putLong("duration", SystemClock.elapsedRealtime() - run);
                                    if (result.narg() > 1) {
                                        data.putString("old", result.isnil(2) ? null : result.checkjstring(2));
                                        data.putString("new", result.isnil(3) ? null : result.checkjstring(3));
                                    }
                                    report(context, hook.getId(), function, "use", data);
                                }
                            } catch (Throwable ex) {
                                synchronized (threadGlobals) {
                                    threadGlobals.remove(Thread.currentThread());
                                }

                                StringBuilder sb = new StringBuilder();

                                sb.append("Exception:\n");
                                sb.append(Log.getStackTraceString(ex));
                                sb.append("\n");

                                sb.append("\nPackage:\n");
                                sb.append(context.getPackageName());
                                sb.append(':');
                                sb.append(Integer.toString(context.getApplicationInfo().uid));
                                sb.append("\n");

                                sb.append("\nMethod:\n");
                                sb.append(function);
                                sb.append(' ');
                                sb.append(member.toString());
                                sb.append("\n");

                                sb.append("\nArguments:\n");
                                if (param.args == null)
                                    sb.append("null\n");
                                else
                                    for (int i = 0; i < param.args.length; i++) {
                                        sb.append(i);
                                        sb.append(": ");
                                        if (param.args[i] == null)
                                            sb.append("null");
                                        else {
                                            sb.append(param.args[i].toString());
                                            sb.append(" (");
                                            sb.append(param.args[i].getClass().getName());
                                            sb.append(')');
                                        }
                                        sb.append("\n");
                                    }

                                sb.append("\nReturn:\n");
                                if (param.getResult() == null)
                                    sb.append("null");
                                else {
                                    sb.append(param.getResult().toString());
                                    sb.append(" (");
                                    sb.append(param.getResult().getClass().getName());
                                    sb.append(')');
                                }
                                sb.append("\n");

                                Log.e(TAG, sb.toString());

                                // Report use error
                                Bundle data = new Bundle();
                                data.putString("function", function);
                                data.putString("exception", sb.toString());
                                report(context, hook.getId(), function, "use", data);
                            }
                        }
                    });
                }

                // Report install
                if (BuildConfig.DEBUG) {
                    Bundle data = new Bundle();
                    data.putLong("duration", SystemClock.elapsedRealtime() - install);
                    report(context, hook.getId(), null, "install", data);
                }
            } catch (Throwable ex) {
                if (hook.isOptional() &&
                        (ex instanceof NoSuchFieldException ||
                                ex instanceof NoSuchMethodException ||
                                ex instanceof ClassNotFoundException ||
                                ex instanceof NoClassDefFoundError))
                    Log.i(TAG, "Optional hook=" + hook.getId() +
                            ": " + ex.getClass().getName() + ": " + ex.getMessage());
                else {
                    Log.e(TAG, hook.getId() + ": " + Log.getStackTraceString(ex));

                    // Report install error
                    Bundle data = new Bundle();
                    data.putString("exception", ex instanceof LuaError ? ex.getMessage() : Log.getStackTraceString(ex));
                    report(context, hook.getId(), null, "install", data);
                }
            }
    }

    private void report(final Context context, String hook, String function, String event, Bundle data) {
        final String packageName = context.getPackageName();
        final int uid = context.getApplicationInfo().uid;

        Bundle args = new Bundle();
        args.putString("hook", hook);
        args.putString("packageName", packageName);
        args.putInt("uid", uid);
        args.putString("event", event);
        args.putLong("time", new Date().getTime());
        args.putBundle("data", data);

        synchronized (queue) {
            String key = (function == null ? "*" : function) + ":" + event;
            if (!queue.containsKey(key))
                queue.put(key, new HashMap<String, Bundle>());
            queue.get(key).put(hook, args);

            if (timer == null) {
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    public void run() {
                        Log.i(TAG, "Processing event queue package=" + packageName + ":" + uid);

                        List<Bundle> work = new ArrayList<>();
                        synchronized (queue) {
                            for (String key : queue.keySet())
                                for (String hook : queue.get(key).keySet())
                                    work.add(queue.get(key).get(hook));
                            queue.clear();
                            timer = null;
                        }

                        for (Bundle args : work)
                            context.getContentResolver()
                                    .call(XProvider.getURI(), "xlua", "report", args);
                    }
                }, 1000);
            }
        }
    }

    private static Class<?> resolveClass(String name, ClassLoader loader) throws ClassNotFoundException {
        if ("boolean".equals(name))
            return boolean.class;
        else if ("byte".equals(name))
            return byte.class;
        else if ("char".equals(name))
            return char.class;
        else if ("short".equals(name))
            return short.class;
        else if ("int".equals(name))
            return int.class;
        else if ("long".equals(name))
            return long.class;
        else if ("float".equals(name))
            return float.class;
        else if ("double".equals(name))
            return double.class;

        else if ("boolean[]".equals(name))
            return boolean[].class;
        else if ("byte[]".equals(name))
            return byte[].class;
        else if ("char[]".equals(name))
            return char[].class;
        else if ("short[]".equals(name))
            return short[].class;
        else if ("int[]".equals(name))
            return int[].class;
        else if ("long[]".equals(name))
            return long[].class;
        else if ("float[]".equals(name))
            return float[].class;
        else if ("double[]".equals(name))
            return double[].class;

        else if ("void".equals(name))
            return Void.TYPE;

        else
            return Class.forName(name, false, loader);
    }

    private static Field resolveField(Class<?> cls, String name, Class<?> type) throws NoSuchFieldException {
        try {
            Class<?> c = cls;
            while (c != null && !c.equals(Object.class))
                try {
                    Field field = c.getDeclaredField(name);
                    if (!field.getType().equals(type))
                        throw new NoSuchFieldException();
                    return field;
                } catch (NoSuchFieldException ex) {
                    for (Field field : c.getDeclaredFields()) {
                        if (!name.equals(field.getName()))
                            continue;

                        if (!field.getType().equals(type))
                            continue;

                        Log.i(TAG, "Resolved field=" + field);
                        return field;
                    }
                }
            throw new NoSuchFieldException(name);
        } catch (NoSuchFieldException ex) {
            Class<?> c = cls;
            while (c != null && !c.equals(Object.class)) {
                Log.i(TAG, c.toString());
                for (Method method : c.getDeclaredMethods())
                    Log.i(TAG, "- " + method.toString());
                c = c.getSuperclass();
            }
            throw ex;
        }
    }

    private static Member resolveMember(Class<?> cls, String name, Class<?>[] params) throws NoSuchMethodException {
        boolean exists = false;
        try {
            Class<?> c = cls;
            while (c != null && !c.equals(Object.class))
                try {
                    if (name == null)
                        return c.getDeclaredConstructor(params);
                    else
                        return c.getDeclaredMethod(name, params);
                } catch (NoSuchMethodException ex) {
                    for (Member member : name == null ? c.getDeclaredConstructors() : c.getDeclaredMethods()) {
                        if (name != null && !name.equals(member.getName()))
                            continue;

                        exists = true;

                        Class<?>[] mparams = (name == null
                                ? ((Constructor) member).getParameterTypes()
                                : ((Method) member).getParameterTypes());

                        if (mparams.length != params.length)
                            continue;

                        boolean same = true;
                        for (int i = 0; i < mparams.length; i++) {
                            if (!mparams[i].isAssignableFrom(params[i])) {
                                same = false;
                                break;
                            }
                        }
                        if (!same)
                            continue;

                        Log.i(TAG, "Resolved member=" + member);
                        return member;
                    }
                    c = c.getSuperclass();
                    if (c == null)
                        throw ex;
                }
            throw new NoSuchMethodException(name);
        } catch (NoSuchMethodException ex) {
            Class<?> c = cls;
            while (c != null && !c.equals(Object.class)) {
                Log.i(TAG, c.toString());
                for (Member member : name == null ? c.getDeclaredConstructors() : c.getDeclaredMethods())
                    if (!exists || name == null || name.equals(member.getName()))
                        Log.i(TAG, "    " + member.toString());
                c = c.getSuperclass();
            }
            throw ex;
        }
    }

    private static Globals getGlobals(Context context, XHook hook, Map<String, String> settings) {
        Globals globals = JsePlatform.standardGlobals();
        // base, bit32, coroutine, io, math, os, package, string, table, luajava

        if (BuildConfig.DEBUG)
            globals.load(new DebugLib());

        globals.set("log", new LuaLog(context.getPackageName(), context.getApplicationInfo().uid, hook.getId()));
        globals.set("hook", new LuaHook(context, settings));

        return new LuaLocals(globals);
    }

    private static class LuaLocals extends Globals {
        LuaLocals(Globals globals) {
            this.presize(globals.length(), 0);
            Varargs entry = globals.next(LuaValue.NIL);
            while (!entry.arg1().isnil()) {
                LuaValue key = entry.arg1();
                LuaValue value = entry.arg(2);
                super.rawset(key, value);
                entry = globals.next(entry.arg1());
            }
        }

        @Override
        public void set(int key, LuaValue value) {
            if (value.isfunction())
                super.set(key, value);
            else
                error("Globals not allowed: set " + value);
        }

        @Override
        public void rawset(int key, LuaValue value) {
            if (value.isfunction())
                super.rawset(key, value);
            else
                error("Globals not allowed: rawset " + value);
        }

        @Override
        public void rawset(LuaValue key, LuaValue value) {
            if (value.isfunction())
                super.rawset(key, value);
            else
                error("Globals not allowed: " + key + "=" + value);
        }
    }

    private static class LuaHook extends VarArgFunction {
        private Context context;
        private Map<String, String> settings;

        LuaHook(Context context, Map<String, String> settings) {
            this.context = context;
            this.settings = settings;
        }

        @Override
        public Varargs invoke(final Varargs args) {
            Class<?> cls = args.arg(1).checkuserdata().getClass();
            String m = args.arg(2).checkjstring();
            args.arg(3).checkfunction();
            Log.i(TAG, "Dynamic hook " + cls.getName() + "." + m);
            final LuaValue fun = args.arg(3);
            final List<LuaValue> xargs = new ArrayList<>();
            for (int i = 4; i <= args.narg(); i++)
                xargs.add(args.arg(i));

            XposedBridge.hookAllMethods(cls, m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    execute("before", param);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    execute("after", param);
                }

                private void execute(String when, MethodHookParam param) {
                    Log.i(TAG, "Dynamic invoke " + param.method);
                    List<LuaValue> values = new ArrayList<>();
                    values.add(LuaValue.valueOf(when));
                    values.add(CoerceJavaToLua.coerce(new XParam(context, param, settings)));
                    for (int i = 0; i < xargs.size(); i++)
                        values.add(xargs.get(i));
                    fun.invoke(values.toArray(new LuaValue[0]));
                }
            });

            return LuaValue.NIL;
        }
    }

    private static class LuaLog extends OneArgFunction {
        private final String packageName;
        private final int uid;
        private final String hook;

        LuaLog(String packageName, int uid, String hook) {
            this.packageName = packageName;
            this.uid = uid;
            this.hook = hook;
        }

        @Override
        public LuaValue call(LuaValue arg) {
            Log.i(TAG, "Log " + packageName + ":" + uid + " " + hook + " " +
                    arg.toString() + " (" + arg.typename() + ")");
            return LuaValue.NIL;
        }
    }

    private class ScriptHolder {
        String script;

        ScriptHolder(String script) {
            String[] lines = script.split("\\r?\\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (!line.startsWith("--"))
                    sb.append(line.trim());
                sb.append("\n");
            }
            this.script = sb.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ScriptHolder))
                return false;
            ScriptHolder other = (ScriptHolder) obj;
            return this.script.equals(other.script);
        }

        @Override
        public int hashCode() {
            return this.script.hashCode();
        }
    }
}
