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
import android.content.res.Resources;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FragmentMain extends Fragment {
    private final static String TAG = "XLua.Main";

    private boolean showAll = false;
    private String query = null;
    private ProgressBar pbApplication;
    private Spinner spGroup;
    private ArrayAdapter<XGroup> spAdapter;
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

        spAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spGroup = main.findViewById(R.id.spGroup);
        spGroup.setTag(null);
        spGroup.setAdapter(spAdapter);
        spGroup.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSelection();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                updateSelection();
            }

            private void updateSelection() {
                XGroup selected = (XGroup) spGroup.getSelectedItem();
                String group = (selected == null ? null : selected.name);
                if (group == null ? spGroup.getTag() != null : !group.equals(spGroup.getTag())) {
                    Log.i(TAG, "Select group=" + group);
                    spGroup.setTag(group);
                    pbApplication.setVisibility(View.VISIBLE);
                    grpApplication.setVisibility(View.GONE);
                    loadData(null, -1);
                }
            }
        });

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

        loadData(null, -1);
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

    private void loadData(String packageName, int uid) {
        XGroup selected = (XGroup) spGroup.getSelectedItem();
        String group = (selected == null ? null : selected.name);

        Log.i(TAG, "Starting data loader group=" + group);
        Bundle args = new Bundle();
        args.putString("group", group);
        args.putString("packageName", packageName);
        args.putInt("uid", uid);
        getActivity().getSupportLoaderManager().restartLoader(
                ActivityMain.LOADER_DATA, args, dataLoaderCallbacks).forceLoad();
    }

    LoaderManager.LoaderCallbacks dataLoaderCallbacks = new LoaderManager.LoaderCallbacks<DataHolder>() {
        @Override
        public Loader<DataHolder> onCreateLoader(int id, Bundle args) {
            DataLoader loader = new DataLoader(getContext());
            loader.setData(
                    args.getString("group"),
                    args.getString("packageName"),
                    args.getInt("uid"));
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<DataHolder> loader, DataHolder data) {
            if (data.exception == null) {
                spAdapter.clear();
                spAdapter.addAll(data.groups);
                spAdapter.notifyDataSetChanged();
                rvAdapter.set(showAll, query, data.hooks, data.apps, data.packageName, data.uid);
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
        private String group;
        private String packageName;
        private int uid;

        DataLoader(Context context) {
            super(context);
            setUpdateThrottle(1000);
        }

        void setData(String group, String packageName, int uid) {
            this.group = group;
            this.packageName = packageName;
            this.uid = uid;
        }

        @Nullable
        @Override
        public DataHolder loadInBackground() {
            Log.i(TAG, "Data loader started");
            DataHolder data = new DataHolder();
            data.packageName = packageName;
            data.uid = uid;
            try {
                // Define hooks
                if (BuildConfig.DEBUG) {
                    String apk = getContext().getApplicationInfo().publicSourceDir;
                    List<XHook> hooks = XHook.readHooks(getContext(), apk);
                    Log.i(TAG, "Loaded hooks=" + hooks.size());
                    for (XHook hook : hooks) {
                        Bundle args = new Bundle();
                        args.putString("id", hook.getId());
                        args.putString("definition", hook.toJSON());
                        getContext().getContentResolver()
                                .call(XProvider.URI, "xlua", "putHook", args);
                    }
                }

                // Load groups
                Resources res = getContext().getResources();
                Bundle result = getContext().getContentResolver()
                        .call(XProvider.URI, "xlua", "getGroups", new Bundle());
                for (String name : result.getStringArray("groups")) {
                    String g = name.toLowerCase().replaceAll("[^a-z]", "_");
                    int id = res.getIdentifier("group_" + g, "string", getContext().getPackageName());

                    XGroup group = new XGroup();
                    group.name = name;
                    group.title = (id > 0 ? res.getString(id) : name);
                    data.groups.add(group);
                }

                final Collator collator = Collator.getInstance(Locale.getDefault());
                collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc
                Collections.sort(data.groups, new Comparator<XGroup>() {
                    @Override
                    public int compare(XGroup group1, XGroup group2) {
                        return collator.compare(group1.title, group2.title);
                    }
                });

                XGroup all = new XGroup();
                all.name = null;
                all.title = getContext().getString(R.string.title_all);
                data.groups.add(0, all);

                // Load hooks
                Cursor chooks = null;
                try {
                    chooks = getContext().getContentResolver()
                            .query(XProvider.URI, new String[]{"xlua.getHooks"}, null, null, null);
                    while (chooks != null && chooks.moveToNext()) {
                        XHook hook = XHook.fromJSON(chooks.getString(0));
                        if (group == null || group.equals(hook.getGroup()))
                            data.hooks.add(hook);
                    }
                } finally {
                    if (chooks != null)
                        chooks.close();
                }

                // Load apps
                Cursor capps = null;
                try {
                    capps = getContext().getContentResolver()
                            .query(XProvider.URI, new String[]{"xlua.getApps"}, null, null, null);
                    while (capps != null && capps.moveToNext()) {
                        XApp app = XApp.fromJSON(capps.getString(0));
                        if (group != null)
                            for (XAssignment assignment : new ArrayList<>(app.assignments))
                                if (!group.equals(assignment.hook.getGroup()))
                                    app.assignments.remove(assignment);
                        data.apps.add(app);
                    }
                } finally {
                    if (capps != null)
                        capps.close();
                }
            } catch (Throwable ex) {
                data.hooks.clear();
                data.apps.clear();
                data.exception = ex;
            }

            Log.i(TAG, "Data loader finished groups=" + data.groups.size() +
                    " hooks=" + data.hooks.size() + " apps=" + data.apps.size());
            return data;
        }
    }

    private BroadcastReceiver dataChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            String packageName = intent.getStringExtra("packageName");
            int uid = intent.getIntExtra("uid", -1);
            Log.i(TAG, "pkg=" + packageName + ":" + uid);
            loadData(null, -1);
        }
    };

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            String packageName = intent.getData().getSchemeSpecificPart();
            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            Log.i(TAG, "pkg=" + packageName + ":" + uid);
            loadData(packageName, uid);
        }
    };

    private static class DataHolder {
        String packageName;
        int uid;
        List<XGroup> groups = new ArrayList<>();
        List<XHook> hooks = new ArrayList<>();
        List<XApp> apps = new ArrayList<>();
        Throwable exception = null;
    }

    private static class XGroup {
        String name;
        String title;

        @Override
        public String toString() {
            return title;
        }
    }
}
