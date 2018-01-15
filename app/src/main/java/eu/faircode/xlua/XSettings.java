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
import android.os.Process;
import android.os.StrictMode;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import de.robv.android.xposed.XposedBridge;

class XSettings {
    private final static String TAG = "XLua.Settings";

    private final static Object lock = new Object();

    private static Map<String, XHook> hooks = null;
    private static SQLiteDatabase db = null;
    private static ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock(true);

    final static String cChannelName = "xlua";

    static Uri URI = Settings.System.CONTENT_URI;
    static String ACTION_DATA_CHANGED = XSettings.class.getPackage().getName() + ".DATA_CHANGED";

    static void loadData(Context context) throws Throwable {
        synchronized (lock) {
            if (hooks == null)
                hooks = loadHooks(context);
            if (db == null)
                db = getDatabase();
        }
    }

    static Bundle call(Context context, String method, Bundle extras) throws Throwable {
        loadData(context);

        Bundle result = null;
        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.allowThreadDiskReads();
            StrictMode.allowThreadDiskWrites();
            switch (method) {
                case "putHook":
                    result = putHook(context, extras);
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
                case "clearData":
                    result = clearData(context, extras);
                    break;
            }
        } finally {
            StrictMode.setThreadPolicy(originalPolicy);
        }

        Log.i(TAG, "Call " + method +
                " uid=" + Process.myUid() +
                " cuid=" + Binder.getCallingUid() +
                " results=" + (result == null ? "-1" : result.keySet().size()));

        return result;
    }

    static Cursor query(Context context, String method, String[] selection) throws Throwable {
        loadData(context);

        Cursor result = null;
        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.allowThreadDiskReads();
            StrictMode.allowThreadDiskWrites();
            switch (method) {
                case "getHooks":
                    result = getHooks(context, selection);
                    break;
                case "getApps":
                    result = getApps(context, selection);
                    break;
                case "getAssignedHooks":
                    result = getAssignedHooks(context, selection);
                    break;
                case "getSettings":
                    result = getSettings(context, selection);
                    break;
            }
        } finally {
            StrictMode.setThreadPolicy(originalPolicy);
        }

        Log.i(TAG, "Query " + method +
                " uid=" + Process.myUid() +
                " cuid=" + Binder.getCallingUid() +
                " rows=" + (result == null ? "-1" : result.getCount()));

        if (result != null)
            result.moveToPosition(-1);
        return result;
    }

    private static Bundle putHook(Context context, Bundle extras) throws Throwable {
        enforcePermission(context);

        XHook hook = XHook.fromJSON(extras.getString("json"));

        synchronized (lock) {
            hooks.put(hook.getId(), hook);
        }

        Log.i(TAG, "Put hook=" + hook.getId());

        return new Bundle();
    }

    private static Cursor getHooks(Context context, String[] selection) throws Throwable {
        MatrixCursor result = new MatrixCursor(new String[]{"json"});
        synchronized (lock) {
            List<XHook> hv = new ArrayList(hooks.values());
            Collections.sort(hv, new Comparator<XHook>() {
                @Override
                public int compare(XHook h1, XHook h2) {
                    return h1.getId().compareTo(h2.getId());
                }
            });
            for (XHook hook : hv)
                if (hook.isEnabled())
                    result.addRow(new String[]{hook.toJSON()});
        }
        return result;
    }

    private static Cursor getApps(Context context, String[] selection) throws Throwable {
        Map<String, XApp> apps = new HashMap<>();

        int cuid = Binder.getCallingUid();
        int userid = Util.getUserId(cuid);

        // Access package manager as system user
        long ident = Binder.clearCallingIdentity();
        try {
            // Get installed apps for current user
            PackageManager pm = Util.createContextForUser(context, userid).getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(0))
                if (!"android".equals(ai.packageName) &&
                        !XSettings.class.getPackage().getName().equals(ai.packageName)) {
                    int esetting = pm.getApplicationEnabledSetting(ai.packageName);
                    boolean enabled = (ai.enabled &&
                            (esetting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                                    esetting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED));
                    boolean persistent = ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) != 0 ||
                            "android".equals(ai.packageName));

                    XApp app = new XApp();
                    app.uid = ai.uid;
                    app.packageName = ai.packageName;
                    app.icon = ai.icon;
                    app.label = (String) pm.getApplicationLabel(ai);
                    app.enabled = enabled;
                    app.persistent = persistent;
                    app.assignments = new ArrayList<>();
                    apps.put(app.packageName + ":" + app.uid, app);
                }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        Log.i(TAG, "Installed apps=" + apps.size() + " cuid=" + cuid);

        // Get assigned hooks
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
                        if (apps.containsKey(pkg + ":" + uid)) {
                            XApp app = apps.get(pkg + ":" + uid);
                            synchronized (lock) {
                                if (hooks.containsKey(hookid)) {
                                    XHook hook = hooks.get(hookid);
                                    if (hook.isEnabled()) {
                                        XAssignment assignment = new XAssignment(hook);
                                        assignment.installed = cursor.getLong(colInstalled);
                                        assignment.used = cursor.getLong(colUsed);
                                        assignment.restricted = (cursor.getInt(colRestricted) == 1);
                                        assignment.exception = cursor.getString(colException);
                                        app.assignments.add(assignment);
                                    }
                                } else
                                    Log.w(TAG, "Hook " + hookid + " not found");
                            }
                        } else
                            Log.i(TAG, "Package " + pkg + ":" + uid + " not found");
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

        MatrixCursor result = new MatrixCursor(new String[]{"json"});
        for (XApp app : apps.values())
            result.addRow(new String[]{app.toJSON()});
        return result;
    }

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
                for (String hookid : hookids)
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

    private static Cursor getAssignedHooks(Context context, String[] selection) throws Throwable {
        if (selection == null || selection.length != 2)
            throw new IllegalArgumentException();

        String packageName = selection[0];
        int uid = Integer.parseInt(selection[1]);
        MatrixCursor result = new MatrixCursor(new String[]{"json"});

        dbLock.readLock().lock();
        try {
            db.beginTransaction();
            try {
                Cursor cursor = null;
                try {
                    cursor = db.query(
                            "assignment",
                            new String[]{"hook"},
                            "package = ? AND uid = ?",
                            new String[]{packageName, Integer.toString(uid)},
                            null, null, "hook");
                    int colHook = cursor.getColumnIndex("hook");
                    while (cursor.moveToNext()) {
                        String hookid = cursor.getString(colHook);
                        synchronized (lock) {
                            if (hooks.containsKey(hookid)) {
                                XHook hook = hooks.get(hookid);
                                if (hook.isEnabled())
                                    result.addRow(new String[]{hook.toJSON()});
                            } else
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
            throw new IllegalArgumentException();

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
        String event = extras.getString("event");
        Bundle data = extras.getBundle("data");

        if (uid != Binder.getCallingUid())
            throw new SecurityException();

        StringBuilder sb = new StringBuilder();
        for (String key : data.keySet()) {
            sb.append(' ');
            sb.append(key);
            sb.append('=');
            sb.append(data.get(key).toString());
        }
        Log.i(TAG, "Hook " + hookid + " pkg=" + packageName + ":" + uid + " event=" + event + sb.toString());

        // Store event
        dbLock.writeLock().lock();
        try {
            db.beginTransaction();
            try {
                ContentValues cv = new ContentValues();
                if ("install".equals(event))
                    cv.put("installed", new Date().getTime());
                else if ("use".equals(event)) {
                    cv.put("used", new Date().getTime());
                    if (data.containsKey("restricted"))
                        cv.put("restricted", data.getInt("restricted"));
                }
                if (data.containsKey("exception"))
                    cv.put("exception", data.getString("exception"));

                long rows = db.update("assignment", cv,
                        "package = ? AND uid = ? AND hook = ?",
                        new String[]{packageName, Integer.toString(uid), hookid});
                if (rows < 1)
                    Log.i(TAG, packageName + ":" + uid + "/" + hookid + " not updated");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            dbLock.writeLock().unlock();
        }

        long ident = Binder.clearCallingIdentity();
        try {
            // Notify data changed
            Intent intent = new Intent();
            intent.setAction(ACTION_DATA_CHANGED);
            intent.setPackage(XSettings.class.getPackage().getName());
            intent.putExtra("packageName", packageName);
            intent.putExtra("uid", uid);
            context.sendBroadcastAsUser(intent, Util.getUserHandle(uid));

            Context ctx = Util.createContextForUser(context, Util.getUserId(uid));
            PackageManager pm = ctx.getPackageManager();
            String self = XSettings.class.getPackage().getName();
            Resources resources = pm.getResourcesForApplication(self);

            // Notify usage
            if ("use".equals(event) && data.getInt("restricted", 0) == 1) {
                // Get hook
                XHook hook = null;
                synchronized (lock) {
                    if (hooks.containsKey(hookid))
                        hook = hooks.get(hookid);
                }

                if (hook.doNotify()) {
                    // Get group name
                    String group = hookid;
                    if (hook != null) {
                        String name = hook.getGroup().toLowerCase().replaceAll("[^a-z]", "_");
                        int resId = resources.getIdentifier("group_" + name, "string", self);
                        if (resId != 0)
                            group = resources.getString(resId);
                    }

                    // Build notification
                    Notification.Builder builder = new Notification.Builder(ctx);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        builder.setChannelId(cChannelName);
                    builder.setSmallIcon(android.R.drawable.ic_dialog_info);
                    builder.setContentTitle(resources.getString(R.string.msg_usage, group));
                    builder.setContentText(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));

                    builder.setPriority(Notification.PRIORITY_DEFAULT);
                    builder.setCategory(Notification.CATEGORY_STATUS);
                    builder.setVisibility(Notification.VISIBILITY_SECRET);

                    // Main
                    Intent main = ctx.getPackageManager().getLaunchIntentForPackage(self);
                    main.putExtra(ActivityMain.EXTRA_SEARCH_PACKAGE, packageName);
                    PendingIntent pi = PendingIntent.getActivity(ctx, uid, main, 0);
                    builder.setContentIntent(pi);

                    builder.setAutoCancel(true);

                    Util.notifyAsUser(ctx, "xlua_usage", uid, builder.build(), Util.getUserId(uid));
                }
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
                Intent main = ctx.getPackageManager().getLaunchIntentForPackage(self);
                main.putExtra(ActivityMain.EXTRA_SEARCH_PACKAGE, packageName);
                PendingIntent pi = PendingIntent.getActivity(ctx, uid, main, 0);
                builder.setContentIntent(pi);

                builder.setAutoCancel(true);

                Util.notifyAsUser(ctx, "xlua_exception", uid, builder.build(), Util.getUserId(uid));
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        return new Bundle();
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

        Log.i(TAG, "Get setting " + userid + ":" + category + ":" + name + "=" + value);
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
                    db.delete(
                            "setting",
                            "user = ? AND category = ? AND name = ?",
                            new String[]{Integer.toString(userid), category, name});
                } else {
                    ContentValues cv = new ContentValues();
                    cv.put("user", userid);
                    cv.put("category", category);
                    cv.put("name", name);
                    cv.put("value", value);
                    db.insertWithOnConflict("setting", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
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
        // Access package manager as system user
        long ident = Binder.clearCallingIdentity();
        try {
            // Allow system
            int cuid = Util.getAppId(Binder.getCallingUid());
            if (cuid == Process.SYSTEM_UID)
                return;

            // Allow same signature
            PackageManager pm = context.getPackageManager();
            String self = XSettings.class.getPackage().getName();
            int uid = pm.getApplicationInfo(self, 0).uid;
            if (pm.checkSignatures(cuid, uid) != PackageManager.SIGNATURE_MATCH)
                throw new SecurityException("Signature error cuid=" + cuid);
        } catch (PackageManager.NameNotFoundException ex) {
            throw new SecurityException(ex);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static Map<String, XHook> loadHooks(Context context) throws Throwable {
        Map<String, XHook> result = new HashMap<>();
        PackageManager pm = context.getPackageManager();
        String self = XSettings.class.getPackage().getName();
        ApplicationInfo ai = pm.getApplicationInfo(self, 0);
        List<XHook> hooks = XHook.readHooks(context, ai.publicSourceDir);
        for (XHook hook : hooks)
            result.put(hook.getId(), hook);
        Log.i(TAG, "Loaded hooks=" + result.size());
        return result;
    }

    private static SQLiteDatabase getDatabase() {
        // Build database file
        File dbFile = new File(
                Environment.getDataDirectory() + File.separator +
                        "system" + File.separator +
                        "xlua" + File.separator +
                        "xlua.db");
        dbFile.getParentFile().mkdirs();

        // Open database
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        Log.i(TAG, "Database version=" + db.getVersion() + " file=" + dbFile);

        // Set database file permissions
        // Owner: rwx (system)
        // Group: rwx (system)
        // World: ---
        Util.setPermissions(dbFile.getParentFile().getAbsolutePath(), 0770, Process.SYSTEM_UID, Process.SYSTEM_UID);
        File[] files = dbFile.getParentFile().listFiles();
        if (files != null)
            for (File file : files)
                Util.setPermissions(file.getAbsolutePath(), 0770, Process.SYSTEM_UID, Process.SYSTEM_UID);

        dbLock.writeLock().lock();
        try {
            // Upgrade database if needed
            if (db.needUpgrade(1)) {
                db.beginTransaction();
                try {
                    // http://www.sqlite.org/lang_createtable.html
                    db.execSQL("CREATE TABLE assignment (package TEXT NOT NULL, uid INTEGER NOT NULL, hook TEXT NOT NULL, installed INTEGER, used INTEGER, restricted INTEGER, exception TEXT)");
                    db.execSQL("CREATE UNIQUE INDEX idx_assignment ON assignment(package, uid, hook)");

                    db.execSQL("CREATE TABLE setting (user INTEGER, category TEXT NOT NULL, name TEXT NOT NULL, value TEXT)");
                    db.execSQL("CREATE UNIQUE INDEX idx_setting ON setting(user, category, name)");

                    db.setVersion(1);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            deleteHook(db, "Privacy.ContentResolver/query1");
            deleteHook(db, "Privacy.ContentResolver/query16");
            deleteHook(db, "Privacy.ContentResolver/query26");
            renameHook(db, "Privacy.MediaRecorder.start", "Privacy.MediaRecorder.start.Audio");
            renameHook(db, "Privacy.MediaRecorder.stop", "Privacy.MediaRecorder.stop.Audio");

            // Reset usage data
            ContentValues cv = new ContentValues();
            cv.put("installed", -1);
            cv.putNull("exception");
            long rows = db.update("assignment", cv, null, null);
            Log.i(TAG, "Reset assigned hook data count=" + rows);

            return db;
        } catch (Throwable ex) {
            db.close();
            throw ex;
        } finally {
            dbLock.writeLock().unlock();
        }
    }

    private static void renameHook(SQLiteDatabase db, String oldId, String newId) {
        try {
            ContentValues cvMediaStart = new ContentValues();
            cvMediaStart.put("hook", oldId);
            long rows = db.update("assignment", cvMediaStart, "hook = ?", new String[]{newId});
            Log.i(TAG, "Renamed hook " + oldId + " into " + newId + " rows=" + rows);
        } catch (Throwable ex) {
            Log.i(TAG, "Renamed hook " + oldId + " into " + newId + " ex=" + ex.getMessage());
        }
    }

    private static void deleteHook(SQLiteDatabase db, String id) {
        try {
            long rows = db.delete("assignment", "hook = ?", new String[]{id});
            Log.i(TAG, "Deleted hook " + id + " rows=" + rows);
        } catch (Throwable ex) {
            Log.i(TAG, "Deleted hook " + id + " ex=" + ex.getMessage());
        }
    }

    static boolean isAvailable(Context context) {
        try {
            String self = XSettings.class.getPackage().getName();
            PackageInfo pi = context.getPackageManager().getPackageInfo(self, 0);
            Bundle result = context.getContentResolver()
                    .call(XSettings.URI, "xlua", "getVersion", new Bundle());
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
        Bundle args = new Bundle();
        args.putInt("user", user);
        args.putString("category", category);
        args.putString("name", name);
        Bundle result = context.getContentResolver()
                .call(XSettings.URI, "xlua", "getSetting", args);
        return Boolean.parseBoolean(result.getString("value"));
    }

    static void putSetting(Context context, String category, String name, String value) {
        Bundle args = new Bundle();
        args.putInt("user", Util.getUserId(Process.myUid()));
        args.putString("category", category);
        args.putString("name", name);
        args.putString("value", value);
        context.getContentResolver().call(XSettings.URI, "xlua", "putSetting", args);
    }

    static void putSettingBoolean(Context context, String category, String name, boolean value) {
        putSetting(context, category, name, Boolean.toString(value));
    }
}
