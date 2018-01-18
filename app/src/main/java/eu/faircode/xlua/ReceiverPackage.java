package eu.faircode.xlua;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import de.robv.android.xposed.XposedBridge;

public class ReceiverPackage extends BroadcastReceiver {
    private static final String TAG = "XLua.Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String packageName = intent.getData().getSchemeSpecificPart();
            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            Log.i(TAG, "Received " + intent + " uid=" + uid);

            int userid = Util.getUserId(uid);
            String self = Xposed.class.getPackage().getName();
            Context ctx = Util.createContextForUser(context, userid);

            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                if (!replacing && !self.equals(packageName)) {
                    // Initialize app
                    Bundle args = new Bundle();
                    args.putString("packageName", packageName);
                    args.putInt("uid", uid);
                    context.getContentResolver()
                            .call(XSettings.URI, "xlua", "clearApp", args);
                    if (XSettings.getSettingBoolean(context, userid, "global", "restrict_new_apps"))
                        context.getContentResolver()
                                .call(XSettings.URI, "xlua", "initApp", args);

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
                    args.putString("packageName", packageName);
                    args.putInt("uid", uid);
                    args.putBoolean("settings", true);
                    context.getContentResolver()
                            .call(XSettings.URI, "xlua", "clearApp", args);

                    Util.cancelAsUser(ctx, "xlua_new_app", uid, userid);
                }
            }
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            XposedBridge.log(ex);
        }
    }
}
