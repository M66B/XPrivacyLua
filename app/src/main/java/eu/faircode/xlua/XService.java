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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.UserHandle;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import de.robv.android.xposed.XposedBridge;

public class XService extends IService.Stub {
    private final static String TAG = "XLua.Service";

    private Object am;
    private Context context;
    private static final Map<String, XHook> idHooks = new HashMap<>();

    private int version = -1;
    private EventHandler handler = null;
    private HandlerThread handlerThread = new HandlerThread("NotifyHandler");
    private final List<EventListener> listeners = new ArrayList<>();

    private SQLiteDatabase db = null;
    private ReentrantReadWriteLock dbLock = new ReentrantReadWriteLock(true);

    private static IService client = null;

    private final static int cBatchSize = 50;
    private final static String cChannelName = "xlua";
    private final static String cServiceName = "user.xlua";
    private final static int cBatchEvenDuration = 1000; // milliseconds

    XService(Object am, Context context, List<XHook> hooks, ClassLoader loader) throws Throwable {
        Log.i(TAG, "Registering service " + cServiceName);

        this.am = am;
        this.context = context;

        // Register self (adb: service list)
        Class<?> cServiceManager = Class.forName("android.os.ServiceManager", false, loader);
        // public static void addService(String name, IBinder service, boolean allowIsolated)
        Method mAddService = cServiceManager.getDeclaredMethod("addService", String.class, IBinder.class, boolean.class);
        mAddService.invoke(null, cServiceName, this, true);

        // Register built-in hooks
        setHooks(hooks);

        Log.i(TAG, "Registered service " + cServiceName);
    }

    void systemReady() throws Throwable {
        Log.i(TAG, "System ready");

        if (this.context == null)
            return;

        // Get module version
        String self = XService.class.getPackage().getName();
        PackageInfo pi = context.getPackageManager().getPackageInfo(self, 0);
        this.version = pi.versionCode;
        Log.i(TAG, "Loaded module version " + this.version);

        // public static UserManagerService getInstance()
        Class<?> clsUM = Class.forName("com.android.server.pm.UserManagerService", false, am.getClass().getClassLoader());
        Object um = clsUM.getDeclaredMethod("getInstance").invoke(null);

        //  public int[] getUserIds()
        int[] userids = (int[]) um.getClass().getDeclaredMethod("getUserIds").invoke(um);

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    cChannelName, context.getString(R.string.channel_privacy), NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }

        // Listen for package changes
        for (int userid : userids) {
            Log.i(TAG, "Registering package listener user=" + userid);
            IntentFilter ifPackageAdd = new IntentFilter();
            ifPackageAdd.addAction(Intent.ACTION_PACKAGE_ADDED);
            ifPackageAdd.addAction(Intent.ACTION_PACKAGE_CHANGED);
            ifPackageAdd.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            ifPackageAdd.addDataScheme("package");
            createContextForUser(context, userid).registerReceiver(packageChangedReceiver, ifPackageAdd);
        }

        // Start event handler
        this.handlerThread.start();
        this.handler = new EventHandler(handlerThread.getLooper());
    }

    static IService getClient() {
        if (client == null)
            try {
                // public static IBinder getService(String name)
                Class<?> cServiceManager = Class.forName("android.os.ServiceManager");
                Method mGetService = cServiceManager.getDeclaredMethod("getService", String.class);
                client = IService.Stub.asInterface((IBinder) mGetService.invoke(null, cServiceName));
            } catch (Throwable ex) {
                Log.e(TAG, Log.getStackTraceString(ex));
            }

        return client;
    }

    static IService getClient(Context context) {
        IService client = getClient();
        if (client != null)
            try {
                String self = XService.class.getPackage().getName();
                PackageInfo pi = context.getPackageManager().getPackageInfo(self, 0);
                if (client.getVersion() != pi.versionCode)
                    throw new Throwable("Module version mismatch");
            } catch (Throwable ex) {
                Log.e(TAG, Log.getStackTraceString(ex));
                client = null;
            }

        return client;
    }

    @Override
    public int getVersion() throws RemoteException {
        return this.version;
    }

    @Override
    public void setHooks(List<XHook> hooks) {
        enforcePermission();

        for (XHook hook : hooks)
            idHooks.put(hook.getId(), hook);
        Log.i(TAG, "Set hooks=" + hooks.size());
    }

    @Override
    public void getHooks(IHookReceiver receiver) throws RemoteException {
        receiver.transfer(new ArrayList<>(idHooks.values()), true);
    }

    @Override
    public void getApps(IAppReceiver receiver) throws RemoteException {
        Map<String, XApp> apps = new HashMap<>();

        int cuid = Binder.getCallingUid();
        int userid = Util.getUserId(cuid);

        // Access package manager as system user
        long ident = Binder.clearCallingIdentity();
        try {
            // Get installed apps for current user
            PackageManager pm = createContextForUser(context, userid).getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
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
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.toString());
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        Log.i(TAG, "Installed apps=" + apps.size() + " cuid=" + cuid);

        try {
            // Get assigned hooks
            SQLiteDatabase db = getDb();
            this.dbLock.readLock().lock();
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
                                if (idHooks.containsKey(hookid)) {
                                    XAssignment assignment = new XAssignment(idHooks.get(hookid));
                                    assignment.installed = cursor.getLong(colInstalled);
                                    assignment.used = cursor.getLong(colUsed);
                                    assignment.restricted = (cursor.getInt(colRestricted) == 1);
                                    assignment.exception = cursor.getString(colException);
                                    app.assignments.add(assignment);
                                } else
                                    Log.w(TAG, "Hook " + hookid + " not found");
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
                this.dbLock.readLock().unlock();
            }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.toString());
        }

        List<XApp> list = new ArrayList<>(apps.values());
        for (int i = 0; i < list.size(); i += cBatchSize) {
            List<XApp> sublist = list.subList(i, Math.min(list.size(), i + cBatchSize));
            Log.i(TAG, "Transferring apps=" + sublist.size() + "@" + i + " of " + list.size());
            receiver.transfer(sublist, i + cBatchSize >= list.size());
        }
    }

    @Override
    public void assignHooks(List<String> hookids, String packageName, int uid, boolean delete, boolean kill)
            throws RemoteException, SecurityException {
        enforcePermission();

        try {
            SQLiteDatabase db = getDb();
            this.dbLock.writeLock().lock();
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
                this.dbLock.writeLock().unlock();
            }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.toString());
        }

        long ident = Binder.clearCallingIdentity();
        try {
            int userid = Util.getUserId(uid);
            Context ctx = createContextForUser(context, userid);
            cancelAsUser(ctx, "xlua_new_app", uid, userid);
            cancelAsUser(ctx, "xlua_exception", uid, userid);

            if (kill)
                killApp(packageName, uid);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.toString());
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public List<XHook> getAssignedHooks(String packageName, int uid) throws RemoteException {
        List<XHook> result = new ArrayList<>();

        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.allowThreadDiskReads();
            StrictMode.allowThreadDiskWrites();

            try {
                SQLiteDatabase db = getDb();
                this.dbLock.readLock().lock();
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
                                    null, null, null);
                            int colHook = cursor.getColumnIndex("hook");
                            while (cursor.moveToNext()) {
                                String hookid = cursor.getString(colHook);
                                if (idHooks.containsKey(hookid)) {
                                    XHook hook = idHooks.get(hookid);
                                    if ("android.content.ContentResolver".equals(hook.getClassName())) {
                                        String className = this.context.getContentResolver().getClass().getName();
                                        hook.setClassName(className);
                                        Log.i(TAG, hook.getId() + " class name=" + className);
                                    }
                                    result.add(hook);
                                } else
                                    Log.w(TAG, "Hook " + hookid + " not found");
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
                    this.dbLock.readLock().unlock();
                }
            } catch (Throwable ex) {
                Log.e(TAG, Log.getStackTraceString(ex));
                throw new RemoteException(ex.toString());
            }
        } finally {
            StrictMode.setThreadPolicy(originalPolicy);
        }

        return result;
    }

    @Override
    public void registerEventListener(final IEventListener listener) throws RemoteException {
        Log.i(TAG, "Registering listener=" + listener);
        synchronized (listeners) {
            listeners.add(new EventListener(listener));
            listener.asBinder().linkToDeath(new DeathRecipient() {
                @Override
                public void binderDied() {
                    Log.i(TAG, "Died listener=" + listener);
                    synchronized (listeners) {
                        listeners.remove(new EventListener(listener));
                    }
                }
            }, 0);
        }
    }

    @Override
    public void unregisterEventListener(IEventListener listener) throws RemoteException {
        Log.i(TAG, "Unregistering listener=" + listener);
        synchronized (listeners) {
            listeners.remove(new EventListener(listener));
        }
    }

    @Override
    public void report(String hookid, String packageName, int uid, String event, Bundle data) throws RemoteException {
        Log.i(TAG, "Hook " + hookid + " pkg=" + packageName + ":" + uid + " event=" + event);
        for (String key : data.keySet())
            Log.i(TAG, key + "=" + data.get(key));

        // Store event
        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.allowThreadDiskReads();
            StrictMode.allowThreadDiskWrites();

            try {
                SQLiteDatabase db = getDb();
                this.dbLock.writeLock().lock();
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
                    this.dbLock.writeLock().unlock();
                }
            } catch (Throwable ex) {
                Log.e(TAG, Log.getStackTraceString(ex));
                throw new RemoteException(ex.toString());
            }

            // Notify data changed
            this.notify(EventHandler.EVENT_DATA_CHANGED, new Bundle());

            // Notify exception
            if (data.containsKey("exception")) {
                long ident = Binder.clearCallingIdentity();
                try {
                    Context ctx = createContextForUser(context, Util.getUserId(uid));
                    PackageManager pm = ctx.getPackageManager();
                    String self = XService.class.getPackage().getName();
                    Resources resources = pm.getResourcesForApplication(self);

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

                    notifyAsUser(ctx, "xlua_exception", uid, builder.build(), Util.getUserId(uid));
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    throw new RemoteException(ex.toString());
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        } finally {
            StrictMode.setThreadPolicy(originalPolicy);
        }
    }

    @Override
    public void notify(int what, Bundle data) {
        enforcePermission();

        // System might not be ready
        if (this.handler != null) {
            Message message = this.handler.obtainMessage();
            message.what = what;
            this.handler.sendMessage(message);
        }
    }

    @Override
    public String getSetting(int userid, String category, String name) throws RemoteException {
        String value = null;
        try {
            SQLiteDatabase db = getDb();
            this.dbLock.readLock().lock();
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
                this.dbLock.readLock().unlock();
            }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.toString());
        }

        Log.i(TAG, "Get setting " + userid + ":" + category + ":" + name + "=" + value);
        return value;
    }

    @Override
    public void putSetting(int userid, String category, String name, String value) throws RemoteException, SecurityException {
        Log.i(TAG, "Put setting  " + userid + ":" + category + ":" + name + "=" + value);

        enforcePermission();

        try {
            SQLiteDatabase db = getDb();
            this.dbLock.writeLock().lock();
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
                this.dbLock.writeLock().unlock();
            }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.toString());
        }
    }

    @Override
    public void clearData(int userid) throws RemoteException {
        enforcePermission();

        Log.i(TAG, "Clearing data user=" + userid);
        try {
            SQLiteDatabase db = getDb();
            this.dbLock.writeLock().lock();
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
                this.dbLock.writeLock().unlock();
            }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            throw new RemoteException(ex.toString());
        }
    }

    private void enforcePermission() throws SecurityException {
        // Access package manager as system user
        long ident = Binder.clearCallingIdentity();
        try {
            int cuid = Util.getAppId(Binder.getCallingUid());
            if (cuid == Process.SYSTEM_UID)
                return;
            String self = XService.class.getPackage().getName();
            int puid = context.getPackageManager().getApplicationInfo(self, 0).uid;
            if (cuid != puid)
                throw new SecurityException("Calling uid " + cuid + " <> package uid " + puid);
        } catch (Throwable ex) {
            throw new SecurityException("Error determining package uid", ex);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private static Context createContextForUser(Context context, int userid) throws Throwable {
        // public UserHandle(int h)
        Class<?> clsUH = Class.forName("android.os.UserHandle");
        Constructor<?> cUH = clsUH.getDeclaredConstructor(int.class);
        UserHandle uh = (UserHandle) cUH.newInstance(userid);

        // public Context createPackageContextAsUser(String packageName, int flags, UserHandle user)
        Method c = context.getClass().getDeclaredMethod("createPackageContextAsUser", String.class, int.class, UserHandle.class);
        return (Context) c.invoke(context, "android", 0, uh);
    }

    private static void notifyAsUser(Context context, String tag, int id, Notification notification, int userid) throws Throwable {
        NotificationManager nm = context.getSystemService(NotificationManager.class);

        // public void notifyAsUser(String tag, int id, Notification notification, UserHandle user)
        Method mNotifyAsUser = nm.getClass().getDeclaredMethod(
                "notifyAsUser", String.class, int.class, Notification.class, UserHandle.class);
        mNotifyAsUser.invoke(nm, tag, id, notification, Util.getUserHandle(userid));
        Log.i(TAG, "Notified " + tag + ":" + id);
    }

    private static void cancelAsUser(Context context, String tag, int id, int userid) throws Throwable {
        NotificationManager nm = context.getSystemService(NotificationManager.class);

        // public void cancelAsUser(String tag, int id, UserHandle user)
        Method mCancelAsUser = nm.getClass().getDeclaredMethod(
                "cancelAsUser", String.class, int.class, UserHandle.class);
        mCancelAsUser.invoke(nm, tag, id, Util.getUserHandle(userid));
        Log.i(TAG, "Cancelled " + tag + ":" + id);
    }

    private void killApp(String pkg, int uid) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        int appid = Util.getAppId(uid);
        int userid = Util.getUserId(uid);
        String reason = "xlua";
        Log.i(TAG, "Killing " + pkg + ":" + appid + ":" + userid);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // public void killApplication(String pkg, int appId, int userId, String reason)
            Method m = am.getClass().getDeclaredMethod("killApplication", String.class, int.class, int.class, String.class);
            m.invoke(am, pkg, appid, userid, reason);
        } else {
            // public void killApplicationWithAppId(String pkg, int appid, String reason)
            Method m = am.getClass().getDeclaredMethod("killApplicationWithAppId", String.class, int.class, String.class);
            m.invoke(am, pkg, uid, reason);
        }
        // public void killUid(int appId, int userId, String reason)
    }

    private SQLiteDatabase getDb() {
        if (this.db == null) {
            this.dbLock.writeLock().lock();
            try {
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

                ContentValues cv = new ContentValues();
                cv.put("installed", -1);
                cv.putNull("exception");
                long rows = db.update("assignment", cv, null, null);
                Log.i(TAG, "Reset assigned hook data count=" + rows);

                this.db = db;
            } catch (Throwable ex) {
                this.db = null;
                throw ex;
            } finally {
                this.dbLock.writeLock().unlock();
            }
        }

        return this.db;
    }

    private static BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);

            try {
                String packageName = intent.getData().getSchemeSpecificPart();
                int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                int userid = Util.getUserId(uid);
                Log.i(TAG, "pkg=" + packageName + ":" + uid);

                List<String> hookids = new ArrayList<>();
                for (XHook hook : idHooks.values())
                    hookids.add(hook.getId());

                String self = XService.class.getPackage().getName();
                IService client = getClient();

                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                    // Check for update
                    if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
                        return;

                    // Check for self
                    if (!self.equals(packageName)) {
                        // Restrict app
                        if (Boolean.parseBoolean(getClient().getSetting(userid, "global", "restrict_new_apps")))
                            client.assignHooks(hookids, packageName, uid, false, false);

                        // Notify new app
                        if (Boolean.parseBoolean(getClient().getSetting(userid, "global", "notify_new_apps"))) {
                            Context ctx = createContextForUser(context, userid);
                            PackageManager pm = ctx.getPackageManager();
                            Resources resources = pm.getResourcesForApplication(self);

                            Notification.Builder builder = new Notification.Builder(ctx);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                builder.setChannelId(cChannelName);
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

                            notifyAsUser(ctx, "xlua_new_app", uid, builder.build(), userid);
                        }
                    }

                } else if (Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction())) {
                    // Do nothing

                } else if (Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())) {
                    if (self.equals(packageName))
                        client.clearData(userid);
                    else
                        client.assignHooks(hookids, packageName, uid, true, false);
                }

                client.notify(EventHandler.EVENT_PACKAGE_CHANGED, new Bundle());
            } catch (Throwable ex) {
                Log.e(TAG, Log.getStackTraceString(ex));
                XposedBridge.log(ex);
            }
        }
    };

    private class EventListener {
        private IEventListener listener;

        EventListener(IEventListener listener) {
            this.listener = listener;
        }

        void dataChanged() throws RemoteException {
            this.listener.dataChanged();
        }

        void packageChanged() throws RemoteException {
            this.listener.packageChanged();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof EventListener))
                return false;
            EventListener other = (EventListener) obj;
            return this.listener.asBinder().equals(other.listener.asBinder());
        }

        @Override
        public String toString() {
            return this.listener.asBinder().toString();
        }
    }

    private class EventHandler extends Handler {
        static final int EVENT_DATA_CHANGED = 1;
        static final int EVENT_PACKAGE_CHANGED = 2;

        EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // Batch changes
            try {
                Thread.sleep(cBatchEvenDuration);
                if (handler.hasMessages(msg.what))
                    handler.removeMessages(msg.what);
            } catch (InterruptedException ignored) {
            }

            // Notify listeners
            synchronized (listeners) {
                List<EventListener> dead = new ArrayList<>();
                for (EventListener listener : listeners)
                    try {
                        Log.i(TAG, "Notify changed what=" + msg.what + " listener=" + listener);
                        switch (msg.what) {
                            case EVENT_DATA_CHANGED:
                                listener.dataChanged();
                                break;
                            case EVENT_PACKAGE_CHANGED:
                                listener.packageChanged();
                                break;
                        }
                    } catch (RemoteException ex) {
                        Log.e(TAG, Log.getStackTraceString(ex));
                        if (ex instanceof DeadObjectException)
                            dead.add(listener);
                    }

                for (EventListener listener : dead) {
                    Log.w(TAG, "Removing listener=" + listener);
                    listeners.remove(listener);
                }
            }
        }
    }
}
