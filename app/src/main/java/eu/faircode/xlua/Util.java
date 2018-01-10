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

import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class Util {
    private final static String TAG = "XLua.Util";

    private static final int PER_USER_RANGE = 100000;

    static String getSelfVersionName(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException ex) {
            return ex.toString();
        }
    }

    static void setPermissions(String path, int mode, int uid, int gid) {
        try {
            Class<?> fileUtils = Class.forName("android.os.FileUtils");
            Method setPermissions = fileUtils
                    .getMethod("setPermissions", String.class, int.class, int.class, int.class);
            setPermissions.invoke(null, path, mode, uid, gid);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
        }
    }

    static int getAppId(int uid) {
        try {
            // public static final int getAppId(int uid)
            Method method = UserHandle.class.getDeclaredMethod("getAppId", int.class);
            return (int) method.invoke(null, uid);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            return uid % PER_USER_RANGE;
        }
    }

    static int getUserId(int uid) {
        try {
            // public static final int getUserId(int uid)
            Method method = UserHandle.class.getDeclaredMethod("getUserId", int.class);
            return (int) method.invoke(null, uid);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            return uid / PER_USER_RANGE;
        }
    }

    static int getUserUid(int userid, int appid) {
        try {
            // public static int getUid(@UserIdInt int userId, @AppIdInt int appId)
            Method method = UserHandle.class.getDeclaredMethod("getUid", int.class, int.class);
            return (int) method.invoke(null, userid, appid);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            return userid * PER_USER_RANGE + (appid % PER_USER_RANGE);
        }
    }

    static UserHandle getUserHandle(int userid) {
        try {
            // public UserHandle(int h)
            Constructor ctor = UserHandle.class.getConstructor(int.class);
            return (UserHandle) ctor.newInstance(userid);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            return Process.myUserHandle();
        }
    }

    static Context createContextForUser(Context context, int userid) throws Throwable {
        // public UserHandle(int h)
        Class<?> clsUH = Class.forName("android.os.UserHandle");
        Constructor<?> cUH = clsUH.getDeclaredConstructor(int.class);
        UserHandle uh = (UserHandle) cUH.newInstance(userid);

        // public Context createPackageContextAsUser(String packageName, int flags, UserHandle user)
        Method c = context.getClass().getDeclaredMethod("createPackageContextAsUser", String.class, int.class, UserHandle.class);
        return (Context) c.invoke(context, "android", 0, uh);
    }

    static void notifyAsUser(Context context, String tag, int id, Notification notification, int userid) throws Throwable {
        NotificationManager nm = context.getSystemService(NotificationManager.class);

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PackageManager pm = context.getPackageManager();
            String self = Util.class.getPackage().getName();
            Resources resources = pm.getResourcesForApplication(self);
            NotificationChannel channel = new NotificationChannel(
                    XSettings.cChannelName, resources.getString(R.string.channel_privacy), NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
            nm.createNotificationChannel(channel);
        }

        // public void notifyAsUser(String tag, int id, Notification notification, UserHandle user)
        Method mNotifyAsUser = nm.getClass().getDeclaredMethod(
                "notifyAsUser", String.class, int.class, Notification.class, UserHandle.class);
        mNotifyAsUser.invoke(nm, tag, id, notification, Util.getUserHandle(userid));
        Log.i(TAG, "Notified " + tag + ":" + id + " as " + userid);
    }

    static void cancelAsUser(Context context, String tag, int id, int userid) throws Throwable {
        NotificationManager nm = context.getSystemService(NotificationManager.class);

        // public void cancelAsUser(String tag, int id, UserHandle user)
        Method mCancelAsUser = nm.getClass().getDeclaredMethod(
                "cancelAsUser", String.class, int.class, UserHandle.class);
        mCancelAsUser.invoke(nm, tag, id, Util.getUserHandle(userid));
        Log.i(TAG, "Cancelled " + tag + ":" + id + " as " + userid);
    }

    static boolean isDebuggable(Context context) {
        return ((context.getApplicationContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    }

    static class DialogObserver implements LifecycleObserver {
        private LifecycleOwner owner = null;
        private Dialog dialog = null;

        void startObserving(LifecycleOwner owner, Dialog dialog) {
            this.dialog = dialog;
            this.owner = owner;
            owner.getLifecycle().addObserver(this);
        }

        void stopObserving() {
            if (this.owner != null && this.dialog != null) {
                owner.getLifecycle().removeObserver(this);
                this.dialog = null;
            }
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        public void onDestroy() {
            if (this.owner != null && this.dialog != null)
                this.dialog.dismiss();
        }
    }
}
