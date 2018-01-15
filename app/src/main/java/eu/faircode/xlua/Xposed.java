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

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

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
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Xposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static final String TAG = "XLua.Xposed";

    private static int version = -1;

    public void initZygote(final IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        Log.i(TAG, "initZygote system=" + startupParam.startsSystemServer);
    }

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        final int uid = Process.myUid();
        Log.i(TAG, "Loaded " + lpparam.packageName + ":" + uid);

        if ("android".equals(lpparam.packageName)) {
            // https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/am/ActivityManagerService.java
            Class<?> clsAM = Class.forName("com.android.server.am.ActivityManagerService", false, lpparam.classLoader);
            XposedBridge.hookAllMethods(clsAM, "systemReady", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Log.i(TAG, "System ready");

                        // Search for context
                        Context context = null;
                        Class<?> cAm = param.thisObject.getClass();
                        while (cAm != null && context == null) {
                            for (Field field : cAm.getDeclaredFields())
                                if (field.getType().equals(Context.class)) {
                                    field.setAccessible(true);
                                    context = (Context) field.get(param.thisObject);
                                    Log.i(TAG, "Context found in " + cAm + " as " + field.getName());
                                    break;
                                }
                            cAm = cAm.getSuperclass();
                        }
                        if (context == null)
                            throw new Throwable("Context not found");

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
                            Util.createContextForUser(context, userid).registerReceiver(packageChangedReceiver, ifPackageAdd);
                        }
                    } catch (Throwable ex) {
                        Log.e(TAG, Log.getStackTraceString(ex));
                        XposedBridge.log(ex);
                    }
                }
            });
        }

        if ("com.android.providers.settings".equals(lpparam.packageName)) {
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
                                    getVersion(context);
                                    param.setResult(XSettings.call(context, arg, extras));
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
                                getVersion(context);
                                param.setResult(XSettings.query(context, projection[0].split("\\.")[1], selection));
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

        if (!"android".equals(lpparam.packageName) &&
                !Xposed.class.getPackage().getName().equals(lpparam.packageName)) {
            Class<?> at = Class.forName("android.app.LoadedApk", false, lpparam.classLoader);
            XposedBridge.hookAllMethods(at, "makeApplication", new XC_MethodHook() {
                private boolean made = false;

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (!made) {
                            made = true;
                            Application app = (Application) param.getResult();
                            ContentResolver resolver = app.getContentResolver();

                            int userid = Util.getUserId(uid);
                            int start = Util.getUserUid(userid, 99000);
                            int end = Util.getUserUid(userid, 99999);
                            boolean isolated = (uid >= start && uid <= end);

                            if (isolated) {
                                Log.i(TAG, "Skipping isolated " + lpparam.packageName + ":" + uid);
                                return;
                            }

                            // Get hooks
                            List<XHook> hooks = new ArrayList<>();
                            Cursor hcursor = null;
                            try {
                                hcursor = resolver
                                        .query(XSettings.URI, new String[]{"xlua.getAssignedHooks"},
                                                null, new String[]{lpparam.packageName, Integer.toString(uid)},
                                                null);
                                while (hcursor != null && hcursor.moveToNext())
                                    hooks.add(XHook.fromJSON(hcursor.getString(0)));
                            } finally {
                                if (hcursor != null)
                                    hcursor.close();
                            }

                            Map<String, String> settings = new HashMap<>();

                            // Get global settings
                            Cursor scursor1 = null;
                            try {
                                scursor1 = resolver
                                        .query(XSettings.URI, new String[]{"xlua.getSettings"},
                                                null, new String[]{"global", Integer.toString(uid)},
                                                null);
                                while (scursor1 != null && scursor1.moveToNext())
                                    settings.put(scursor1.getString(0), scursor1.getString(1));
                            } finally {
                                if (scursor1 != null)
                                    scursor1.close();
                            }

                            // Get package settings
                            Cursor scursor2 = null;
                            try {
                                scursor2 = resolver
                                        .query(XSettings.URI, new String[]{"xlua.getSettings"},
                                                null, new String[]{lpparam.packageName, Integer.toString(uid)},
                                                null);
                                while (scursor2 != null && scursor2.moveToNext())
                                    settings.put(scursor2.getString(0), scursor2.getString(1));
                            } finally {
                                if (scursor2 != null)
                                    scursor2.close();
                            }

                            hookPackage(app, lpparam, uid, hooks, settings);
                        }
                    } catch (Throwable ex) {
                        Log.e(TAG, Log.getStackTraceString(ex));
                        XposedBridge.log(ex);
                    }
                }
            });
        }
    }

    private void getVersion(Context context) throws PackageManager.NameNotFoundException {
        if (version < 0) {
            String self = Xposed.class.getPackage().getName();
            PackageInfo pi = context.getPackageManager().getPackageInfo(self, 0);
            version = pi.versionCode;
            Log.i(TAG, "Loaded module version " + version);
        }
    }

    private void hookPackage(
            final Context context,
            final XC_LoadPackage.LoadPackageParam lpparam, final int uid,
            List<XHook> hooks, final Map<String, String> settings) {
        for (final XHook hook : hooks)
            try {
                // Compile script
                InputStream is = new ByteArrayInputStream(hook.getLuaScript().getBytes());
                final Prototype script = LuaC.instance.compile(is, "script");

                // Get class
                Class<?> cls;
                try {
                    cls = Class.forName(hook.getClassName(), false, lpparam.classLoader);
                } catch (ClassNotFoundException ex) {
                    if (hook.isOptional()) {
                        Log.i(TAG, "Optional hook=" + hook.getId() + ": " + ex.getMessage());
                        continue;
                    } else
                        throw ex;
                }

                String[] m = hook.getMethodName().split(":");
                if (m.length > 1) {
                    Field field = cls.getField(m[0]);
                    Object obj = field.get(null);
                    cls = obj.getClass();
                }
                String methodName = m[m.length - 1];

                // Get parameter types
                String[] p = hook.getParameterTypes();
                final Class<?>[] paramTypes = new Class[p.length];
                for (int i = 0; i < p.length; i++)
                    paramTypes[i] = resolveClass(p[i], lpparam.classLoader);

                // Get return type
                final Class<?> returnType = resolveClass(hook.getReturnType(), lpparam.classLoader);

                if (methodName.startsWith("#")) {
                    // Get field
                    Field field;
                    try {
                        field = resolveField(cls, methodName.substring(1), returnType);
                        field.setAccessible(true);
                    } catch (NoSuchFieldException ex) {
                        if (hook.isOptional()) {
                            Log.i(TAG, "Optional hook=" + hook.getId() + ": " + ex.getMessage());
                            continue;
                        } else
                            throw ex;
                    }

                    // Initialize Lua runtime
                    Globals globals = JsePlatform.standardGlobals();
                    LuaClosure closure = new LuaClosure(script, globals);
                    closure.call();

                    // Check if function exists
                    LuaValue func = globals.get("after");
                    if (!func.isnil()) {
                        // Setup globals
                        globals.set("log", new LuaLog(lpparam.packageName, uid));

                        // Run function
                        Varargs result = func.invoke(
                                CoerceJavaToLua.coerce(hook),
                                CoerceJavaToLua.coerce(new XParam(
                                        lpparam.packageName, uid,
                                        field,
                                        paramTypes, returnType, lpparam.classLoader,
                                        settings))
                        );

                        // Report use
                        boolean restricted = result.arg1().checkboolean();
                        if (restricted) {
                            Bundle data = new Bundle();
                            data.putString("function", "after");
                            data.putInt("restricted", restricted ? 1 : 0);
                            report(context, hook.getId(), lpparam.packageName, uid, "use", data);
                        }
                    }

                } else {
                    // Get method
                    Method method;
                    try {
                        method = resolveMethod(cls, methodName, paramTypes);
                    } catch (NoSuchMethodException ex) {
                        if (hook.isOptional()) {
                            Log.i(TAG, "Optional hook=" + hook.getId() + ": " + ex.getMessage());
                            continue;
                        } else
                            throw ex;
                    }

                    // Check return type
                    if (!method.getReturnType().equals(returnType))
                        throw new Throwable("Invalid return type got " + method.getReturnType() + " expected " + returnType);

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
                                // Initialize Lua runtime
                                Globals globals = JsePlatform.standardGlobals();
                                LuaClosure closure = new LuaClosure(script, globals);
                                closure.call();

                                // Check if function exists
                                LuaValue func = globals.get(function);
                                if (!func.isnil()) {
                                    // Setup globals
                                    globals.set("log", new LuaLog(lpparam.packageName, uid));

                                    // Run function
                                    Varargs result = func.invoke(
                                            CoerceJavaToLua.coerce(hook),
                                            CoerceJavaToLua.coerce(new XParam(
                                                    lpparam.packageName, uid,
                                                    param,
                                                    paramTypes, returnType, lpparam.classLoader,
                                                    settings))
                                    );

                                    // Report use
                                    boolean restricted = result.arg1().checkboolean();
                                    if (restricted) {
                                        Bundle data = new Bundle();
                                        data.putString("function", function);
                                        data.putInt("restricted", restricted ? 1 : 0);
                                        report(context, hook.getId(), lpparam.packageName, uid, "use", data);
                                    }
                                }
                            } catch (Throwable ex) {
                                Log.e(TAG, Log.getStackTraceString(ex));

                                // Report use error
                                Bundle data = new Bundle();
                                data.putString("function", function);
                                data.putString("exception", Log.getStackTraceString(ex));
                                report(context, hook.getId(), lpparam.packageName, uid, "use", data);
                            }
                        }
                    });
                }

                // Report install
                Bundle data = new Bundle();
                report(context, hook.getId(), lpparam.packageName, uid, "install", data);
            } catch (Throwable ex) {
                Log.e(TAG, Log.getStackTraceString(ex));

                // Report install error
                Bundle data = new Bundle();
                data.putString("exception", ex.toString());
                data.putString("stacktrace", Log.getStackTraceString(ex));
                report(context, hook.getId(), lpparam.packageName, uid, "install", data);
            }
    }

    private static class LuaLog extends OneArgFunction {
        private final String packageName;
        private final int uid;

        LuaLog(String packageName, int uid) {
            this.packageName = packageName;
            this.uid = uid;
        }

        @Override
        public LuaValue call(LuaValue arg) {
            Log.i(TAG, "Log " +
                    packageName + ":" + uid + " " +
                    arg.toString() + " type=" + arg.typename());
            return LuaValue.NIL;
        }
    }

    private static void report(Context context, String hook, String packageName, int uid, String event, Bundle data) {
        Bundle args = new Bundle();
        args.putString("hook", hook);
        args.putString("packageName", packageName);
        args.putInt("uid", uid);
        args.putString("event", event);
        args.putBundle("data", data);
        context.getContentResolver()
                .call(XSettings.URI, "xlua", "report", args);
    }

    private static Class<?> resolveClass(String name, ClassLoader loader) throws ClassNotFoundException {
        if ("boolean".equals(name))
            return boolean.class;
        else if ("int".equals(name))
            return int.class;
        else if ("long".equals(name))
            return long.class;
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

    private static Method resolveMethod(Class<?> cls, String name, Class<?>[] params) throws NoSuchMethodException {
        try {
            Class<?> c = cls;
            while (c != null && !c.equals(Object.class))
                try {
                    return c.getDeclaredMethod(name, params);
                } catch (NoSuchMethodException ex) {
                    for (Method method : c.getDeclaredMethods()) {
                        if (!name.equals(method.getName()))
                            continue;

                        Class<?>[] mparams = method.getParameterTypes();

                        if (mparams.length != params.length)
                            continue;

                        boolean same = true;
                        for (int i = 0; i < mparams.length; i++) {
                            if (!params[i].isAssignableFrom(mparams[i])) {
                                same = false;
                                break;
                            }
                        }
                        if (!same)
                            continue;

                        Log.i(TAG, "Resolved method=" + method);
                        return method;
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
                for (Method method : c.getDeclaredMethods())
                    Log.i(TAG, "- " + method.toString());
                c = c.getSuperclass();
            }
            throw ex;
        }
    }

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String packageName = intent.getData().getSchemeSpecificPart();
                int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                int userid = Util.getUserId(uid);
                boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                Log.i(TAG, "Received " + intent);

                // Get hooks
                ArrayList<String> hooks = new ArrayList<>();
                Cursor cursor = null;
                try {
                    cursor = context.getContentResolver()
                            .query(XSettings.URI, new String[]{"xlua.getHooks"}, null, null, null);
                    while (cursor != null && cursor.moveToNext())
                        hooks.add(XHook.fromJSON(cursor.getString(0)).getId());
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                String self = Xposed.class.getPackage().getName();
                Context ctx = Util.createContextForUser(context, userid);

                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                    if (!replacing && !self.equals(packageName)) {
                        // Restrict app
                        if (XSettings.getSettingBoolean(context, userid, "global", "restrict_new_apps")) {
                            Bundle args = new Bundle();
                            args.putStringArrayList("hooks", hooks);
                            args.putString("packageName", packageName);
                            args.putInt("uid", uid);
                            args.putBoolean("delete", false);
                            args.putBoolean("kill", false);
                            context.getContentResolver()
                                    .call(XSettings.URI, "xlua", "assignHooks", args);
                        }

                        // Notify new app
                        if (XSettings.getSettingBoolean(context, userid, "global", "notify_new_apps")) {
                            PackageManager pm = ctx.getPackageManager();
                            Resources resources = pm.getResourcesForApplication(self);

                            Notification.Builder builder = new Notification.Builder(ctx);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                builder.setChannelId(XSettings.cChannelName);
                            builder.setSmallIcon(android.R.drawable.ic_dialog_alert);
                            builder.setContentTitle(resources.getString(R.string.msg_review_settings));
                            builder.setContentText(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));

                            builder.setPriority(Notification.PRIORITY_HIGH);
                            builder.setCategory(Notification.CATEGORY_STATUS);
                            builder.setVisibility(Notification.VISIBILITY_SECRET);

                            // Main
                            Intent main = ctx.getPackageManager().getLaunchIntentForPackage(self);
                            main.putExtra(ActivityMain.EXTRA_SEARCH_PACKAGE, packageName);
                            PendingIntent pi = PendingIntent.getActivity(ctx, uid, main, 0);
                            builder.setContentIntent(pi);

                            builder.setAutoCancel(true);

                            Util.notifyAsUser(ctx, "xlua_new_app", uid, builder.build(), userid);
                        }
                    }
                } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                    if (self.equals(packageName)) {
                        Bundle args = new Bundle();
                        args.putInt("user", userid);
                        context.getContentResolver()
                                .call(XSettings.URI, "xlua", "clearData", args);
                    } else {
                        Bundle args = new Bundle();
                        args.putStringArrayList("hooks", hooks);
                        args.putString("packageName", packageName);
                        args.putInt("uid", uid);
                        args.putBoolean("delete", true);
                        args.putBoolean("kill", false);
                        context.getContentResolver()
                                .call(XSettings.URI, "xlua", "assignHooks", args);

                        Util.cancelAsUser(ctx, "xlua_new_app", uid, userid);
                    }
                }
            } catch (Throwable ex) {
                Log.e(TAG, Log.getStackTraceString(ex));
                XposedBridge.log(ex);
            }
        }
    };
}
