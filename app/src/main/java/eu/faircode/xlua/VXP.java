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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.robv.android.xposed.XposedBridge;

public class VXP extends ContentProvider {
    private static final String TAG = "XLua.VXP";

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        try {
            Log.i(TAG, "Call " + arg +
                    " uid=" + android.os.Process.myUid() +
                    " cuid=" + android.os.Binder.getCallingUid());
            return XProvider.call(getContext(), arg, extras);
        } catch (RemoteException ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            XposedBridge.log(ex);
            return null;
        }
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        try {
            Log.i(TAG, "Query " + projection[0].split("\\.")[1] +
                    " uid=" + android.os.Process.myUid() +
                    " cuid=" + android.os.Binder.getCallingUid());
            return XProvider.query(getContext(), projection[0].split("\\.")[1], selectionArgs);
        } catch (RemoteException ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
            XposedBridge.log(ex);
            return null;
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
