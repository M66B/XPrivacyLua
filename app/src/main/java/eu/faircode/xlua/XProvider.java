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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import de.robv.android.xposed.XposedBridge;

class XProvider {
    private final static String TAG = "XLua.Provider";

    private final static Object lock = new Object();

    private static SQLiteDatabase db = null;
    private static ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock(true);

    private static Map<String, XHook> hooks = null;
    private static Map<String, XHook> builtins = null;

    final static String cChannelName = "xlua";

    static Uri getURI() {
        if (Util.isVirtualXposed())
            return Uri.parse("content://eu.faircode.xlua.vxp/");
        else
            return Settings.System.CONTENT_URI;
    }

    static void loadData(Context context) throws RemoteException {
        try {
            synchronized (lock) {
                if (db == null)
                    db = getDatabase(context);
                if (hooks == null)
                    loadHooks(context);
            }
        } catch (RemoteException ex) {
            throw ex;
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.getMessage());
        }
    }

    static Bundle call(Context context, String method, Bundle extras) throws RemoteException, IllegalArgumentException {
        loadData(context);

        Bundle result = null;
        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.allowThreadDiskReads();
            StrictMode.allowThreadDiskWrites();
            switch (method) {
                case "getVersion":
                    result = getVersion(context, extras);
                    break;
                case "putHook":
                    result = putHook(context, extras);
                    break;
                case "getGroups":
                    result = getGroups(context, extras);
                    break;
                case "assignHooks":
                    result = assignHooks(context, extras);
                    break;
                case "report":
                    result = report(context, extras);
                    break;
                case "getSetting":
                    result = getSetting(context, extras);
                    break;
                case "putSetting":
                    result = putSetting(context, extras);
                    break;
                case "initApp":
                    result = initApp(context, extras);
                    break;
                case "clearApp":
                    result = clearApp(context, extras);
                    break;
                case "clearData":
                    result = clearData(context, extras);
                    break;
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RemoteException ex) {
            throw ex;
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.getMessage());
        } finally {
            StrictMode.setThreadPolicy(originalPolicy);
        }

        //Log.i(TAG, "Call " + method +
        //        " uid=" + Process.myUid() +
        //        " cuid=" + Binder.getCallingUid() +
        //        " results=" + (result == null ? "-1" : result.keySet().size()));

        return result;
    }

    static Cursor query(Context context, String method, String[] selection) throws RemoteException {
        loadData(context);

        Cursor result = null;
        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.allowThreadDiskReads();
            StrictMode.allowThreadDiskWrites();
            switch (method) {
                case "getHooks":
                    result = getHooks(context, selection, false);
                    break;
                case "getHooks2":
                    result = getHooks(context, selection, true);
                    break;
                case "getApps":
                    result = getApps(context, selection, false);
                    break;
                case "getApps2":
                    result = getApps(context, selection, true);
                    break;
                case "getAssignedHooks":
                    result = getAssignedHooks(context, selection, false);
                    break;
                case "getAssignedHooks2":
                    result = getAssignedHooks(context, selection, true);
                    break;
                case "getSettings":
                    result = getSettings(context, selection);
                    break;
                case "getLog":
                    result = getLog(context, selection);
                    break;
            }
        } catch (RemoteException ex) {
            throw ex;
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.getMessage());
        } finally {
            StrictMode.setThreadPolicy(originalPolicy);
        }

        //Log.i(TAG, "Query " + method +
        //        " uid=" + Process.myUid() +
        //        " cuid=" + Binder.getCallingUid() +
        //        " rows=" + (result == null ? "-1" : result.getCount()));

        if (result != null)
            result.moveToPosition(-1);
        return result;
    }

    private static Bundle getVersion(Context context, Bundle extras) throws Throwable {
        if (Util.isVirtualXposed()) {
            PackageInfo pi = context.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            Bundle result = new Bundle();
            result.putInt("version", pi.versionCode);
            return result;
        } else
            return null;
    }

    private static Bundle putHook(Context context, Bundle extras) throws Throwable {
        enforcePermission(context);

        // Get arguments
        String id = extras.getString("id");
        String definition = extras.getString("definition");
        if (id == null)
            throw new IllegalArgumentException("id missing");

        // Get hook
        XHook hook = (definition == null ? null : XHook.fromJSON(definition));
        if (hook != null) {
            hook.validate();
            if (!id.equals(hook.getId()))
                throw new IllegalArgumentException("id mismatch");
        }

        // Cache hook
        synchronized (lock) {
            if (hook == null) {
                if (hooks.containsKey(id) && hooks.get(id).isBuiltin())
                    throw new IllegalArgumentException("builtin");
                Log.i(TAG, "Deleting hook id=" + id);
                hooks.remove(id);
                if (builtins.containsKey(id)) {
                    Log.i(TAG, "Restoring builtin id=" + id);
                    XHook builtin = builtins.get(id);
                    // class name is already resolved
                    hooks.put(id, builtin);
                } else
                    Log.w(TAG, "Builtin not found id=" + id);
            } else {
                if (!hook.isBuiltin())
                    Log.i(TAG, "Storing hook id=" + id);
                hook.resolveClassName(context);
                hooks.put(id, hook);
            }
        }

        // Persist define hook
        if (hook == null || !hook.isBuiltin()) {
            dbLock.writeLock().lock();
            try {
                db.beginTransaction();
                try {
                    if (hook == null) {
                        long rows = db.delete("hook", "id = ?", new String[]{id});
                        if (rows < 0)
                            throw new Throwable("Error deleting hook");
                    } else {
                        ContentValues cv = new ContentValues();
                        cv.put("id", id);
                        cv.put("definition", hook.toJSON());
                        long rows = db.insertWithOnConflict("hook", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                        if (rows < 0)
                            throw new Throwable("Error inserting hook");
                    }

                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } finally {
                dbLock.writeLock().unlock();
            }
        }

        return new Bundle();
    }

    private static Bundle getGroups(Context context, Bundle extras) throws Throwable {
        List<String> groups = new ArrayList<>();

        List<String> collection = getCollection(context, Util.getUserId(Binder.getCallingUid()));

        synchronized (lock) {
            for (XHook hook : hooks.values())
                if (hook.isAvailable(null, collection) && !groups.contains(hook.getGroup()))
                    groups.add(hook.getGroup());
        }

        Bundle result = new Bundle();
        result.putStringArray("groups", groups.toArray(new String[0]));
        return result;
    }

    @SuppressLint("WrongConstant")
    private static Cursor getHooks(Context context, String[] selection, boolean marshall) throws Throwable {
        boolean all = (selection != null && selection.length == 1 && "all".equals(selection[0]));
        List<String> collection = getCollection(context, Util.getUserId(Binder.getCallingUid()));

        List<XHook> hv = new ArrayList();
        synchronized (lock) {
            for (XHook hook : hooks.values())
                if (all || hook.isAvailable(null, collection))
                    hv.add(hook);
        }

        Collections.sort(hv, new Comparator<XHook>() {
            @Override
            public int compare(XHook h1, XHook h2) {
                return h1.getId().compareTo(h2.getId());
            }
        });

        MatrixCursor result = new MatrixCursor(new String[]{marshall ? "blob" : "json"});
        for (XHook hook : hv)
            if (marshall) {
                Parcel parcel = Parcel.obtain();
                hook.writeToParcel(parcel, XHook.FLAG_WITH_LUA);
                result.newRow().add(parcel.marshall());
                parcel.recycle();
            } else
                result.addRow(new Object[]{hook.toJSON()});
        return result;
    }

    private static Cursor getApps(Context context, String[] selection, boolean marshall) throws Throwable {
        Map<String, XApp> apps = new HashMap<>();

        int cuid = Binder.getCallingUid();
        int userid = Util.getUserId(cuid);

        // Access package manager as system user
        long ident = Binder.clearCallingIdentity();
        try {
            // Get installed apps for current user
            PackageManager pm = Util.createContextForUser(context, userid).getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(0))
                if (!ai.packageName.startsWith(BuildConfig.APPLICATION_ID)) {
                    int esetting = pm.getApplicationEnabledSetting(ai.packageName);
                    boolean enabled = (ai.enabled &&
                            (esetting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                                    esetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED));
                    boolean persistent = ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0 ||
                            "android".equals(ai.packageName));
                    boolean system = ((ai.flags &
                            (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);

                    XApp app = new XApp();
                    app.uid = ai.uid;
                    app.packageName = ai.packageName;
                    app.icon = ai.icon;
                    app.label = (String) pm.getApplicationLabel(ai);
                    app.enabled = enabled;
                    app.persistent = persistent;
                    app.system = system;
                    app.forceStop = (!persistent && !system);
                    app.assignments = new ArrayList<>();
                    apps.put(app.packageName, app);
                }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        Log.i(TAG, "Installed apps=" + apps.size() + " cuid=" + cuid);

        // Get settings
        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = null;
                try {
                    cursor = db.query(
                            "setting",
                            new String[]{"category", "value"},
                            "user = ? AND name = 'forcestop'",
                            new String[]{Integer.toString(userid)},
                            null, null, null);
                    while (cursor.moveToNext()) {
                        String pkg = cursor.getString(0);
                        if (apps.containsKey(pkg)) {
                            XApp app = apps.get(pkg);
                            app.forceStop = Boolean.parseBoolean(cursor.getString(1));
                        } else
                            Log.i(TAG, "Package " + pkg + " not found (force stop)");
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

        // Get assigned hooks
        List<String> collection = getCollection(context, userid);
        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = null;
                try {
                    int start = Util.getUserUid(userid, 0);
                    int end = Util.getUserUid(userid, Process.LAST_APPLICATION_UID);
                    cursor = db.query(
                            "assignment",
                            new String[]{"package", "uid", "hook", "installed", "used", "restricted", "exception"},
                            "uid >= ? AND uid <= ?",
                            new String[]{Integer.toString(start), Integer.toString(end)},
                            null, null, null);
                    int colPkg = cursor.getColumnIndex("package");
                    int colUid = cursor.getColumnIndex("uid");
                    int colHook = cursor.getColumnIndex("hook");
                    int colInstalled = cursor.getColumnIndex("installed");
                    int colUsed = cursor.getColumnIndex("used");
                    int colRestricted = cursor.getColumnIndex("restricted");
                    int colException = cursor.getColumnIndex("exception");
                    while (cursor.moveToNext()) {
                        String pkg = cursor.getString(colPkg);
                        int uid = cursor.getInt(colUid);
                        String hookid = cursor.getString(colHook);
                        if (apps.containsKey(pkg)) {
                            XApp app = apps.get(pkg);
                            if (app.uid != uid)
                                continue;
                            synchronized (lock) {
                                if (hooks.containsKey(hookid)) {
                                    XHook hook = hooks.get(hookid);
                                    if (hook.isAvailable(pkg, collection)) {
                                        XAssignment assignment = new XAssignment(hook);
                                        assignment.installed = cursor.getLong(colInstalled);
                                        assignment.used = cursor.getLong(colUsed);
                                        assignment.restricted = (cursor.getInt(colRestricted) == 1);
                                        assignment.exception = cursor.getString(colException);
                                        app.assignments.add(assignment);
                                    }
                                } else if (BuildConfig.DEBUG)
                                    Log.w(TAG, "Hook " + hookid + " not found (assignment)");
                            }
                        } else
                            Log.i(TAG, "Package " + pkg + " not found");
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

        MatrixCursor result = new MatrixCursor(new String[]{marshall ? "blob" : "json"});
        for (XApp app : apps.values())
            if (marshall) {
                Parcel parcel = Parcel.obtain();
                app.writeToParcel(parcel, 0);
                result.newRow().add(parcel.marshall());
                parcel.recycle();
            } else
                result.newRow().add(app.toJSON());
        return result;
    }

    @SuppressLint("MissingPermission")
    private static Bundle assignHooks(Context context, Bundle extras) throws Throwable {
        enforcePermission(context);

        List<String> hookids = extras.getStringArrayList("hooks");
        String packageName = extras.getString("packageName");
        int uid = extras.getInt("uid");
        boolean delete = extras.getBoolean("delete");
        boolean kill = extras.getBoolean("kill");

        dbLock.writeLock().lock();
        try {
            db.beginTransaction();
            try {
                List<String> groups = new ArrayList<>();
                for (String hookid : hookids) {
                    XHook hook = null;
                    synchronized (lock) {
                        if (hooks.containsKey(hookid))
                            hook = hooks.get(hookid);
                    }
                    if (hook != null && !groups.contains(hook.getGroup()))
                        groups.add(hook.getGroup());

                    if (delete) {
                        Log.i(TAG, packageName + ":" + uid + "/" + hookid + " deleted");
                        long rows = db.delete("assignment",
                                "hook = ? AND package = ? AND uid = ?",
                                new String[]{hookid, packageName, Integer.toString(uid)});
                        if (rows < 0)
                            throw new Throwable("Error deleting assignment");
                    } else {
                        Log.i(TAG, packageName + ":" + uid + "/" + hookid + " added");
                        ContentValues cv = new ContentValues();
                        cv.put("package", packageName);
                        cv.put("uid", uid);
                        cv.put("hook", hookid);
                        cv.put("installed", -1);
                        cv.put("used", -1);
                        cv.put("restricted", 0);
                        cv.putNull("exception");
                        long rows = db.insertWithOnConflict("assignment", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                        if (rows < 0)
                            throw new Throwable("Error inserting assignment");
                    }
                }

                if (!delete)
                    for (String group : groups) {
                        long rows = db.delete("`group`",
                                "package = ? AND uid = ? AND name = ?",
                                new String[]{packageName, Integer.toString(uid), group});
                        if (rows < 0)
                            throw new Throwable("Error deleting group");
                    }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.writeLock().unlock();
        }

        if (kill)
            forceStop(context, packageName, Util.getUserId(uid));

        return new Bundle();
    }

    @SuppressLint("WrongConstant")
    private static Cursor getAssignedHooks(Context context, String[] selection, boolean marshall) throws Throwable {
        if (selection == null || selection.length != 2)
            throw new IllegalArgumentException("selection invalid");

        String packageName = selection[0];
        int uid = Integer.parseInt(selection[1]);
        MatrixCursor result = new MatrixCursor(new String[]{marshall ? "blob" : "json", "used"});

        List<String> collection = getCollection(context, Util.getUserId(uid));

        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = null;
                try {
                    cursor = db.query(
                            "assignment",
                            new String[]{"hook", "used"},
                            "package = ? AND uid = ?",
                            new String[]{packageName, Integer.toString(uid)},
                            null, null, "hook");
                    int colHook = cursor.getColumnIndex("hook");
                    int colUsed = cursor.getColumnIndex("used");
                    while (cursor.moveToNext()) {
                        String hookid = cursor.getString(colHook);
                        synchronized (lock) {
                            if (hooks.containsKey(hookid)) {
                                XHook hook = hooks.get(hookid);
                                if (hook.isAvailable(packageName, collection))
                                    if (marshall) {
                                        Parcel parcel = Parcel.obtain();
                                        hook.writeToParcel(parcel, XHook.FLAG_WITH_LUA);
                                        result.newRow().add(parcel.marshall()).add(cursor.getString(colUsed));
                                        parcel.recycle();
                                    } else
                                        result.newRow().add(hook.toJSON()).add(cursor.getString(colUsed));
                            } else if (BuildConfig.DEBUG)
                                Log.w(TAG, "Hook " + hookid + " not found");
                        }
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

        return result;
    }

    private static Cursor getSettings(Context context, String[] selection) throws Throwable {
        if (selection == null || selection.length != 2)
            throw new IllegalArgumentException("selection invalid");

        String packageName = selection[0];
        int uid = Integer.parseInt(selection[1]);
        int userid = Util.getUserId(uid);
        MatrixCursor result = new MatrixCursor(new String[]{"name", "value"});

        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = null;
                try {
                    cursor = db.query(
                            "setting",
                            new String[]{"name", "value"},
                            "user = ? AND category = ?",
                            new String[]{Integer.toString(userid), packageName},
                            null, null, null);
                    while (cursor.moveToNext())
                        result.addRow(new String[]{cursor.getString(0), cursor.getString(1)});
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

        return result;
    }

    @SuppressLint("MissingPermission")
    private static Bundle report(Context context, Bundle extras) throws Throwable {
        String hookid = extras.getString("hook");
        String packageName = extras.getString("packageName");
        int uid = extras.getInt("uid");
        int userid = Util.getUserId(uid);
        String event = extras.getString("event");
        long time = extras.getLong("time");
        Bundle data = extras.getBundle("data");
        int restricted = data.getInt("restricted", 0);

        if (uid != Binder.getCallingUid())
            throw new SecurityException();

        StringBuilder sb = new StringBuilder();
        for (String key : data.keySet()) {
            sb.append(' ');
            sb.append(key);
            sb.append('=');
            Object value = data.get(key);
            sb.append(value == null ? "null" : value.toString());
        }
        Log.i(TAG, "Hook " + hookid + " pkg=" + packageName + ":" + uid + " event=" + event + sb.toString());

        // Get hook
        XHook hook = null;
        synchronized (lock) {
            if (hooks.containsKey(hookid))
                hook = hooks.get(hookid);
        }

        // Get notify setting
        Bundle args = new Bundle();
        args.putInt("user", userid);
        args.putString("category", packageName);
        args.putString("name", "notify");
        boolean notify = Boolean.parseBoolean(getSetting(context, args).getString("value"));

        long used = -1;
        dbLock.writeLock().lock();
        try {
            db.beginTransaction();
            try {
                // Store event
                ContentValues cv = new ContentValues();
                if ("install".equals(event))
                    cv.put("installed", time);
                else if ("use".equals(event)) {
                    cv.put("used", time);
                    cv.put("restricted", restricted);
                }
                if (data.containsKey("exception"))
                    cv.put("exception", data.getString("exception"));
                if (data.containsKey("old"))
                    cv.put("old", data.getString("old"));
                if (data.containsKey("new"))
                    cv.put("new", data.getString("new"));

                long rows = db.update("assignment", cv,
                        "package = ? AND uid = ? AND hook = ?",
                        new String[]{packageName, Integer.toString(uid), hookid});
                if (rows != 1)
                    Log.w(TAG, "Error updating assignment");

                // Update group
                if (hook != null && "use".equals(event) && restricted == 1 && notify) {
                    Cursor cursor = null;
                    try {
                        cursor = db.query("`group`", new String[]{"used"},
                                "package = ? AND uid = ? AND name = ?",
                                new String[]{packageName, Integer.toString(uid), hook.getGroup()},
                                null, null, null);
                        if (cursor.moveToNext())
                            used = cursor.getLong(0);
                    } finally {
                        if (cursor != null)
                            cursor.close();
                    }

                    cv.clear();
                    cv.put("package", packageName);
                    cv.put("uid", uid);
                    cv.put("name", hook.getGroup());
                    cv.put("used", time);
                    rows = db.insertWithOnConflict("`group`", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    if (rows < 0)
                        throw new Throwable("Error inserting group");
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.writeLock().unlock();
        }

        long ident = Binder.clearCallingIdentity();
        try {
            Context ctx = Util.createContextForUser(context, userid);
            PackageManager pm = ctx.getPackageManager();
            Resources resources = pm.getResourcesForApplication(BuildConfig.APPLICATION_ID);

            // Notify usage
            if (hook != null && "use".equals(event) && restricted == 1 &&
                    (hook.doNotify() || (notify && used < 0))) {
                // Get group name
                String name = hook.getGroup().toLowerCase().replaceAll("[^a-z]", "_");
                int resId = resources.getIdentifier("group_" + name, "string", BuildConfig.APPLICATION_ID);
                String group = (resId == 0 ? hookid : resources.getString(resId));

                // Build notification
                Notification.Builder builder = new Notification.Builder(ctx);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    builder.setChannelId(cChannelName);
                builder.setSmallIcon(android.R.drawable.ic_dialog_info);
                builder.setContentTitle(resources.getString(R.string.msg_usage, group));
                builder.setContentText(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));
                if (BuildConfig.DEBUG)
                    builder.setSubText(hookid);

                builder.setPriority(Notification.PRIORITY_DEFAULT);
                builder.setCategory(Notification.CATEGORY_STATUS);
                builder.setVisibility(Notification.VISIBILITY_SECRET);

                // Main
                Intent main = ctx.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
                if (main != null) {
                    main.putExtra(ActivityMain.EXTRA_SEARCH_PACKAGE, packageName);
                    PendingIntent pi = PendingIntent.getActivity(ctx, uid, main, 0);
                    builder.setContentIntent(pi);
                }

                builder.setAutoCancel(true);

                Util.notifyAsUser(ctx, "xlua_use_" + hook.getGroup(), uid, builder.build(), userid);
            }

            // Notify exception
            if (data.containsKey("exception")) {
                Notification.Builder builder = new Notification.Builder(ctx);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    builder.setChannelId(cChannelName);
                builder.setSmallIcon(android.R.drawable.ic_dialog_alert);
                builder.setContentTitle(resources.getString(R.string.msg_exception, hookid));
                builder.setContentText(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));

                builder.setPriority(Notification.PRIORITY_HIGH);
                builder.setCategory(Notification.CATEGORY_STATUS);
                builder.setVisibility(Notification.VISIBILITY_SECRET);

                // Main
                Intent main = ctx.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
                if (main != null) {
                    main.putExtra(ActivityMain.EXTRA_SEARCH_PACKAGE, packageName);
                    PendingIntent pi = PendingIntent.getActivity(ctx, uid, main, 0);
                    builder.setContentIntent(pi);
                }

                builder.setAutoCancel(true);

                Util.notifyAsUser(ctx, "xlua_exception", uid, builder.build(), userid);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return new Bundle();
    }

    private static Cursor getLog(Context context, String[] selection) throws Throwable {
        enforcePermission(context);

        if (selection != null)
            throw new IllegalArgumentException("selection invalid");

        int cuid = Binder.getCallingUid();
        int userid = Util.getUserId(cuid);
        int start = Util.getUserUid(userid, 0);
        int end = Util.getUserUid(userid, Process.LAST_APPLICATION_UID);

        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = db.query(
                        "assignment",
                        new String[]{"package", "uid", "hook", "used", "old", "new"},
                        "restricted = 1 AND uid >= ? AND uid <= ?",
                        new String[]{Integer.toString(start), Integer.toString(end)},
                        null, null, "used DESC");

                db.setTransactionSuccessful();

                return cursor;
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }
    }

    private static List<String> getCollection(Context context, int userid) throws Throwable {
        Bundle args = new Bundle();
        args.putInt("user", userid);
        args.putString("category", "global");
        args.putString("name", "collection");
        String collection = getSetting(context, args).getString("value", "Privacy");
        List<String> result = new ArrayList<>();
        Collections.addAll(result, collection.split(","));
        return result;
    }

    private static Bundle getSetting(Context context, Bundle extras) throws Throwable {
        int userid = extras.getInt("user");
        String category = extras.getString("category");
        String name = extras.getString("name");

        String value = null;
        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = null;
                try {
                    cursor = db.query("setting", new String[]{"value"},
                            "user = ? AND category = ? AND name = ?",
                            new String[]{Integer.toString(userid), category, name},
                            null, null, null);
                    if (cursor.moveToNext())
                        value = (cursor.isNull(0) ? null : cursor.getString(0));
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

        if (BuildConfig.DEBUG)
            Log.d(TAG, "Get setting " + userid + ":" + category + ":" + name + "=" + value);
        Bundle result = new Bundle();
        result.putString("value", value);
        return result;
    }

    private static Bundle putSetting(Context context, Bundle extras) throws Throwable {
        enforcePermission(context);

        int userid = extras.getInt("user");
        String category = extras.getString("category");
        String name = extras.getString("name");
        String value = extras.getString("value");
        boolean kill = extras.getBoolean("kill", false);
        Log.i(TAG, "Put setting " + userid + ":" + category + " " + name + "=" + value);

        dbLock.writeLock().lock();
        try {
            db.beginTransaction();
            try {
                if (value == null) {
                    long rows = db.delete(
                            "setting",
                            "user = ? AND category = ? AND name = ?",
                            new String[]{Integer.toString(userid), category, name});
                    if (rows < 0)
                        throw new Throwable("Error deleting setting");
                } else {
                    ContentValues cv = new ContentValues();
                    cv.put("user", userid);
                    cv.put("category", category);
                    cv.put("name", name);
                    cv.put("value", value);
                    long rows = db.insertWithOnConflict("setting", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    if (rows < 0)
                        throw new Throwable("Error inserting setting");
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.writeLock().unlock();
        }

        if (kill)
            forceStop(context, category, userid);

        return new Bundle();
    }

    private static Bundle initApp(Context context, Bundle extras) throws Throwable {
        enforcePermission(context);

        String packageName = extras.getString("packageName");
        int uid = extras.getInt("uid");
        boolean kill = extras.getBoolean("kill", false);

        int userid = Util.getUserId(uid);
        List<String> collection = getCollection(context, Util.getUserId(uid));

        List<String> hookids = new ArrayList<>();
        synchronized (lock) {
            for (XHook hook : hooks.values())
                if (hook.isAvailable(packageName, collection))
                    hookids.add(hook.getId());
        }

        dbLock.writeLock().lock();
        try {
            db.beginTransaction();
            try {
                for (String hookid : hookids) {
                    ContentValues cv = new ContentValues();
                    cv.put("package", packageName);
                    cv.put("uid", uid);
                    cv.put("hook", hookid);
                    cv.put("installed", -1);
                    cv.put("used", -1);
                    cv.put("restricted", 0);
                    cv.putNull("exception");
                    long rows = db.insertWithOnConflict("assignment", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                    if (rows < 0)
                        throw new Throwable("Error inserting assignment");
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.writeLock().unlock();
        }

        if (kill)
            forceStop(context, packageName, userid);

        Log.i(TAG, "Init app pkg=" + packageName + " uid=" + uid + " assignments=" + hookids.size());

        return new Bundle();
    }

    private static Bundle clearApp(Context context, Bundle extras) throws Throwable {
        enforcePermission(context);

        String packageName = extras.getString("packageName");
        int uid = extras.getInt("uid");
        boolean kill = extras.getBoolean("kill", false);
        boolean full = extras.getBoolean("settings", false);

        long assignments;
        long settings = 0;
        int userid = Util.getUserId(uid);

        dbLock.writeLock().lock();
        try {
            db.beginTransaction();
            try {
                assignments = db.delete(
                        "assignment",
                        "package = ? AND uid = ?",
                        new String[]{packageName, Integer.toString(uid)});
                if (full)
                    settings = db.delete(
                            "setting",
                            "user = ? AND category = ?",
                            new String[]{Integer.toString(userid), packageName});

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.writeLock().unlock();
        }

        if (kill)
            forceStop(context, packageName, userid);

        Log.i(TAG, "Cleared app pkg=" + packageName + " uid=" + uid +
                " assignments=" + assignments + " settings=" + settings);

        return new Bundle();
    }

    private static Bundle clearData(Context context, Bundle extras) throws Throwable {
        enforcePermission(context);

        int userid = extras.getInt("user");
        Log.i(TAG, "Clearing data user=" + userid);

        dbLock.writeLock().lock();
        try {
            db.beginTransaction();
            try {
                if (userid == 0) {
                    db.delete("assignment", null, null);
                    db.delete("setting", null, null);
                } else {
                    int start = Util.getUserUid(userid, 0);
                    int end = Util.getUserUid(userid, Process.LAST_APPLICATION_UID);
                    db.delete(
                            "assignment",
                            "uid >= ? AND uid <= ?",
                            new String[]{Integer.toString(start), Integer.toString(end)});
                    db.delete(
                            "setting",
                            "user = ?",
                            new String[]{Integer.toString(userid)});
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.writeLock().unlock();
        }

        return new Bundle();
    }

    private static void enforcePermission(Context context) throws SecurityException {
        int cuid = Util.getAppId(Binder.getCallingUid());

        // Access package manager as system user
        long ident = Binder.clearCallingIdentity();
        try {
            // Allow system
            if (cuid == Process.SYSTEM_UID)
                return;

            // Allow same signature
            PackageManager pm = context.getPackageManager();
            int uid = pm.getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid;
            if (pm.checkSignatures(cuid, uid) == PackageManager.SIGNATURE_MATCH)
                return;

            // Allow specific signature
            String[] cpkg = pm.getPackagesForUid(cuid);
            if (cpkg.length > 0) {
                byte[] bytes = Util.getSha1Fingerprint(context, cpkg[0]);
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes)
                    sb.append(Integer.toString(b & 0xff, 16).toLowerCase());

                Resources resources = pm.getResourcesForApplication(BuildConfig.APPLICATION_ID);
                if (sb.toString().equals(resources.getString(R.string.pro_fingerprint)))
                    return;
            }
            throw new SecurityException("Signature error cuid=" + cuid);
        } catch (Throwable ex) {
            throw new SecurityException(ex);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static void loadHooks(Context context) throws Throwable {
        hooks = new HashMap<>();
        builtins = new HashMap<>();

        // Read built-in definition
        PackageManager pm = context.getPackageManager();
        ApplicationInfo ai = pm.getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
        for (XHook builtin : XHook.readHooks(context, ai.publicSourceDir)) {
            builtin.resolveClassName(context);
            builtins.put(builtin.getId(), builtin);
            hooks.put(builtin.getId(), builtin);
        }

        // Read external definitions
        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = null;
                try {
                    cursor = db.query("hook", null,
                            null, null,
                            null, null, null);
                    int colDefinition = cursor.getColumnIndex("definition");
                    while (cursor.moveToNext()) {
                        String definition = cursor.getString(colDefinition);
                        XHook hook = XHook.fromJSON(definition);
                        hook.resolveClassName(context);
                        hooks.put(hook.getId(), hook);
                    }
                } finally {
                    if (cursor != null)
                        cursor.close();
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.readLock().unlock();
        }

        Log.i(TAG, "Loaded hook definitions hooks=" + hooks.size() + " builtins=" + builtins.size());
    }

    private static SQLiteDatabase getDatabase(Context context) throws Throwable {
        // Build database file
        File dbFile;
        if (Util.isVirtualXposed())
            dbFile = new File(context.getFilesDir(), "xlua.db");
        else {
            dbFile = new File(
                    Environment.getDataDirectory() + File.separator +
                            "system" + File.separator +
                            "xlua" + File.separator +
                            "xlua.db");
            dbFile.getParentFile().mkdirs();
        }

        // Open database
        SQLiteDatabase _db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        Log.i(TAG, "Database file=" + dbFile);

        if (!Util.isVirtualXposed()) {
            // Set database file permissions
            // Owner: rwx (system)
            // Group: rwx (system)
            // World: ---
            Util.setPermissions(dbFile.getParentFile().getAbsolutePath(), 0770, Process.SYSTEM_UID, Process.SYSTEM_UID);
            File[] files = dbFile.getParentFile().listFiles();
            if (files != null)
                for (File file : files)
                    Util.setPermissions(file.getAbsolutePath(), 0770, Process.SYSTEM_UID, Process.SYSTEM_UID);
        }

        dbLock.writeLock().lock();
        try {
            // Upgrade database if needed
            if (_db.needUpgrade(1)) {
                Log.i(TAG, "Database upgrade version 1");
                _db.beginTransaction();
                try {
                    _db.execSQL("CREATE TABLE assignment (package TEXT NOT NULL, uid INTEGER NOT NULL, hook TEXT NOT NULL, installed INTEGER, used INTEGER, restricted INTEGER, exception TEXT)");
                    _db.execSQL("CREATE UNIQUE INDEX idx_assignment ON assignment(package, uid, hook)");

                    _db.execSQL("CREATE TABLE setting (user INTEGER, category TEXT NOT NULL, name TEXT NOT NULL, value TEXT)");
                    _db.execSQL("CREATE UNIQUE INDEX idx_setting ON setting(user, category, name)");

                    _db.setVersion(1);
                    _db.setTransactionSuccessful();
                } finally {
                    _db.endTransaction();
                }
            }

            if (_db.needUpgrade(2)) {
                Log.i(TAG, "Database upgrade version 2");
                _db.beginTransaction();
                try {
                    _db.execSQL("CREATE TABLE hook (id TEXT NOT NULL, definition TEXT NOT NULL)");
                    _db.execSQL("CREATE UNIQUE INDEX idx_hook ON hook(id, definition)");

                    _db.setVersion(2);
                    _db.setTransactionSuccessful();
                } finally {
                    _db.endTransaction();
                }
            }

            if (_db.needUpgrade(3)) {
                Log.i(TAG, "Database upgrade version 3");
                _db.beginTransaction();
                try {
                    _db.execSQL("ALTER TABLE assignment ADD COLUMN old TEXT");
                    _db.execSQL("ALTER TABLE assignment ADD COLUMN new TEXT");
                    _db.execSQL("CREATE INDEX idx_assignment_used ON assignment(used)");

                    _db.setVersion(3);
                    _db.setTransactionSuccessful();
                } finally {
                    _db.endTransaction();
                }
            }

            if (_db.needUpgrade(4)) {
                Log.i(TAG, "Database upgrade version 4");
                _db.beginTransaction();
                try {
                    Map<String, XHook> tmp = new HashMap<>();
                    Cursor cursor = null;
                    try {
                        cursor = _db.query("hook", null,
                                null, null,
                                null, null, null);
                        int colDefinition = cursor.getColumnIndex("definition");
                        while (cursor.moveToNext()) {
                            String definition = cursor.getString(colDefinition);
                            XHook hook = XHook.fromJSON(definition);
                            tmp.put(hook.getId(), hook);
                        }
                    } finally {
                        if (cursor != null)
                            cursor.close();
                    }
                    Log.i(TAG, "Converting definitions=" + tmp.size());

                    _db.execSQL("DROP INDEX idx_hook");
                    _db.execSQL("DELETE FROM hook");
                    _db.execSQL("CREATE UNIQUE INDEX idx_hook ON hook(id)");

                    for (String id : tmp.keySet()) {
                        ContentValues cv = new ContentValues();
                        cv.put("id", id);
                        cv.put("definition", tmp.get(id).toJSON());
                        long rows = _db.insertWithOnConflict("hook", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                        if (rows < 0)
                            throw new Throwable("Error inserting hook");
                    }


                    _db.setVersion(4);
                    _db.setTransactionSuccessful();
                } finally {
                    _db.endTransaction();
                }
            }

            if (_db.needUpgrade(5)) {
                Log.i(TAG, "Database upgrade version 5");
                _db.beginTransaction();
                try {
                    _db.execSQL("CREATE TABLE `group` (package TEXT NOT NULL, uid INTEGER NOT NULL, name TEXT NOT NULL, used INTEGER)");
                    _db.execSQL("CREATE UNIQUE INDEX idx_group ON `group`(package, uid, name)");

                    _db.setVersion(5);
                    _db.setTransactionSuccessful();
                } finally {
                    _db.endTransaction();
                }
            }

            //deleteHook(_db, "Privacy.ContentResolver/query1");
            //deleteHook(_db, "Privacy.ContentResolver/query16");
            //deleteHook(_db, "Privacy.ContentResolver/query26");
            renameHook(_db, "TelephonyManager/getDeviceId", "TelephonyManager.getDeviceId");
            renameHook(_db, "TelephonyManager/getDeviceId/slot", "TelephonyManager.getDeviceId/slot");
            renameHook(_db, "TelephonyManager/getGroupIdLevel1", "TelephonyManager.getGroupIdLevel1");
            renameHook(_db, "TelephonyManager/getImei", "TelephonyManager.getImei");
            renameHook(_db, "TelephonyManager/getImei/slot", "TelephonyManager.getImei/slot");
            renameHook(_db, "TelephonyManager/getLine1Number", "TelephonyManager.getLine1Number");
            renameHook(_db, "TelephonyManager/getMeid", "TelephonyManager.getMeid");
            renameHook(_db, "TelephonyManager/getMeid/slot", "TelephonyManager.getMeid/slot");
            renameHook(_db, "TelephonyManager/getNetworkSpecifier", "TelephonyManager.getNetworkSpecifier");
            renameHook(_db, "TelephonyManager/getSimSerialNumber", "TelephonyManager.getSimSerialNumber");
            renameHook(_db, "TelephonyManager/getSubscriberId", "TelephonyManager.getSubscriberId");
            renameHook(_db, "TelephonyManager/getVoiceMailAlphaTag", "TelephonyManager.getVoiceMailAlphaTag");
            renameHook(_db, "TelephonyManager/getVoiceMailNumber", "TelephonyManager.getVoiceMailNumber");
            renameHook(_db, "Settings.Secure.getString", "Settings.Secure.getString/android_id");
            renameHook(_db, "SystemProperties.get", "SystemProperties.get/serial");
            renameHook(_db, "SystemProperties.get/default", "SystemProperties.get.default/serial");

            Log.i(TAG, "Database version=" + _db.getVersion());

            // Reset usage data
            ContentValues cv = new ContentValues();
            cv.put("installed", -1);
            cv.putNull("exception");
            long rows = _db.update("assignment", cv, null, null);
            Log.i(TAG, "Reset assigned hook data count=" + rows);

            return _db;
        } catch (Throwable ex) {
            _db.close();
            throw ex;
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    private static void renameHook(SQLiteDatabase _db, String oldId, String newId) {
        try {
            ContentValues cv = new ContentValues();
            cv.put("hook", newId);
            long rows = _db.update("assignment", cv, "hook = ?", new String[]{oldId});
            Log.i(TAG, "Renamed hook " + oldId + " into " + newId + " rows=" + rows);
        } catch (Throwable ex) {
            Log.i(TAG, "Renamed hook " + oldId + " into " + newId + " ex=" + ex.getMessage());
        }
    }

    private static void deleteHook(SQLiteDatabase _db, String id) {
        try {
            long rows = _db.delete("assignment", "hook = ?", new String[]{id});
            Log.i(TAG, "Deleted hook " + id + " rows=" + rows);
        } catch (Throwable ex) {
            Log.i(TAG, "Deleted hook " + id + " ex=" + ex.getMessage());
        }
    }

    static boolean isAvailable(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
            Bundle result = context.getContentResolver()
                    .call(XProvider.getURI(), "xlua", "getVersion", new Bundle());
            return (result != null && pi.versionCode == result.getInt("version"));
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            XposedBridge.log(ex);
            return false;
        }
    }

    private static void forceStop(Context context, String packageName, int userid) throws Throwable {
        // Access activity manager as system user
        long ident = Binder.clearCallingIdentity();
        try {
            // public void forceStopPackageAsUser(String packageName, int userId)
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            Method mForceStop = am.getClass().getMethod("forceStopPackageAsUser", String.class, int.class);
            mForceStop.invoke(am, packageName, userid);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    static boolean getSettingBoolean(Context context, String category, String name) {
        return getSettingBoolean(context, Util.getUserId(Process.myUid()), category, name);
    }

    static boolean getSettingBoolean(Context context, int user, String category, String name) {
        return Boolean.parseBoolean(getSetting(context, user, category, name));
    }

    static String getSetting(Context context, String category, String name) {
        return getSetting(context, Util.getUserId(Process.myUid()), category, name);
    }

    static String getSetting(Context context, int user, String category, String name) {
        Bundle args = new Bundle();
        args.putInt("user", user);
        args.putString("category", category);
        args.putString("name", name);
        Bundle result = context.getContentResolver()
                .call(XProvider.getURI(), "xlua", "getSetting", args);
        return (result == null ? null : result.getString("value"));
    }

    static void putSetting(Context context, String category, String name, String value) {
        Bundle args = new Bundle();
        args.putInt("user", Util.getUserId(Process.myUid()));
        args.putString("category", category);
        args.putString("name", name);
        args.putString("value", value);
        context.getContentResolver().call(XProvider.getURI(), "xlua", "putSetting", args);
    }

    static void putSettingBoolean(Context context, String category, String name, boolean value) {
        putSetting(context, category, name, Boolean.toString(value));
    }
}
