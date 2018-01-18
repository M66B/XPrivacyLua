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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.Group;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;

public class FragmentMain extends Fragment {
    private final static String TAG = "XLua.Main";

    private boolean showAll = false;
    private String query = null;
    private ProgressBar pbApplication;
    private RecyclerView rvApplication;
    private Group grpApplication;
    private AdapterApp rvAdapter;

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View main = inflater.inflate(R.layout.restrictions, container, false);

        pbApplication = main.findViewById(R.id.pbApplication);
        grpApplication = main.findViewById(R.id.grpApplication);

        // Initialize app list
        rvApplication = main.findViewById(R.id.rvApplication);
        rvApplication.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity()) {
            @Override
            public boolean onRequestChildFocus(RecyclerView parent, RecyclerView.State state, View child, View focused) {
                return true;
            }
        };
        llm.setAutoMeasureEnabled(true);
        rvApplication.setLayoutManager(llm);
        rvAdapter = new AdapterApp(getActivity());
        rvApplication.setAdapter(rvAdapter);

        return main;
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter ifData = new IntentFilter(XProvider.ACTION_DATA_CHANGED);
        getContext().registerReceiver(dataChangedReceiver, ifData);

        IntentFilter ifPackage = new IntentFilter();
        ifPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_CHANGED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        ifPackage.addDataScheme("package");
        getContext().registerReceiver(packageChangedReceiver, ifPackage);

        // Load data
        Log.i(TAG, "Starting data loader");
        getActivity().getSupportLoaderManager().restartLoader(
                ActivityMain.LOADER_DATA, new Bundle(), dataLoaderCallbacks).forceLoad();
    }

    @Override
    public void onPause() {
        super.onPause();

        getContext().unregisterReceiver(dataChangedReceiver);
        getContext().unregisterReceiver(packageChangedReceiver);
    }

    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
        if (rvAdapter != null)
            rvAdapter.setShowAll(showAll);
    }

    public void filter(String query) {
        this.query = query;
        if (rvAdapter != null)
            rvAdapter.getFilter().filter(query);
    }

    LoaderManager.LoaderCallbacks dataLoaderCallbacks = new LoaderManager.LoaderCallbacks<DataHolder>() {
        @Override
        public Loader<DataHolder> onCreateLoader(int id, Bundle args) {
            return new DataLoader(getContext());
        }

        @Override
        public void onLoadFinished(Loader<DataHolder> loader, DataHolder data) {
            if (data.exception == null) {
                rvAdapter.set(showAll, query, data.hooks, data.apps);
                pbApplication.setVisibility(View.GONE);
                grpApplication.setVisibility(View.VISIBLE);
            } else {
                Log.e(TAG, Log.getStackTraceString(data.exception));
                Snackbar.make(getView(), data.exception.toString(), Snackbar.LENGTH_LONG).show();
            }
        }

        @Override
        public void onLoaderReset(Loader<DataHolder> loader) {
            // Do nothing
        }
    };

    private static class DataLoader extends AsyncTaskLoader<DataHolder> {
        DataLoader(Context context) {
            super(context);
            setUpdateThrottle(1000);
        }

        @Nullable
        @Override
        public DataHolder loadInBackground() {
            Log.i(TAG, "Data loader started");
            DataHolder data = new DataHolder();
            try {
                if (BuildConfig.DEBUG) {
                    String apk = getContext().getApplicationInfo().publicSourceDir;
                    List<XHook> hooks = XHook.readHooks(getContext(), apk);
                    Log.i(TAG, "Loaded hooks=" + hooks.size());
                    for (XHook hook : hooks) {
                        Bundle args = new Bundle();
                        args.putString("json", hook.toJSON());
                        getContext().getContentResolver()
                                .call(XProvider.URI, "xlua", "putHook", args);
                    }
                }

                Cursor chooks = null;
                try {
                    chooks = getContext().getContentResolver()
                            .query(XProvider.URI, new String[]{"xlua.getHooks"}, null, null, null);
                    while (chooks != null && chooks.moveToNext())
                        data.hooks.add(XHook.fromJSON(chooks.getString(0)));
                } finally {
                    if (chooks != null)
                        chooks.close();
                }

                Cursor capps = null;
                try {
                    capps = getContext().getContentResolver()
                            .query(XProvider.URI, new String[]{"xlua.getApps"}, null, null, null);
                    while (capps != null && capps.moveToNext())
                        data.apps.add(XApp.fromJSON(capps.getString(0)));
                } finally {
                    if (capps != null)
                        capps.close();
                }

            } catch (Throwable ex) {
                data.hooks.clear();
                data.apps.clear();
                data.exception = ex;
            }

            Log.i(TAG, "Data loader finished hooks=" + data.hooks.size() + " apps=" + data.apps.size());
            return data;
        }
    }

    private BroadcastReceiver dataChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            getActivity().getSupportLoaderManager().restartLoader(
                    ActivityMain.LOADER_DATA, new Bundle(), dataLoaderCallbacks).forceLoad();
        }
    };

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            getActivity().getSupportLoaderManager().restartLoader(
                    ActivityMain.LOADER_DATA, new Bundle(), dataLoaderCallbacks).forceLoad();
        }
    };

    private static class DataHolder {
        List<XHook> hooks = new ArrayList<>();
        List<XApp> apps = new ArrayList<>();
        Throwable exception = null;
    }
}
