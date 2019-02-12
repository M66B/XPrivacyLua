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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.Group;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class FragmentMain extends Fragment {
    private final static String TAG = "XLua.Fragment";

    private ProgressBar pbApplication;
    private Spinner spGroup;
    private ArrayAdapter<XGroup> spAdapter;
    private Button btnRestrict;
    private TextView tvRestrict;
    private Group grpApplication;
    private SwipeRefreshLayout swipeRefresh;
    private AdapterApp rvAdapter;

    private AdapterApp.enumShow show = AdapterApp.enumShow.none;

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View main = inflater.inflate(R.layout.restrictions, container, false);

        pbApplication = main.findViewById(R.id.pbApplication);
        btnRestrict = main.findViewById(R.id.btnRestrict);
        tvRestrict = main.findViewById(R.id.tvRestrict);
        grpApplication = main.findViewById(R.id.grpApplication);

        int colorAccent = Util.resolveColor(getContext(), R.attr.colorAccent);

        swipeRefresh = main.findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(colorAccent, colorAccent, colorAccent);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadData();
            }
        });

        // Initialize app list
        RecyclerView rvApplication = main.findViewById(R.id.rvApplication);
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
                    rvAdapter.setGroup(group);
                }

                tvRestrict.setVisibility(group == null ? View.VISIBLE : View.GONE);
                btnRestrict.setVisibility(group == null ? View.INVISIBLE : View.VISIBLE);
            }
        });

        btnRestrict.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                XGroup selected = (XGroup) spGroup.getSelectedItem();
                Util.areYouSure(
                        (ActivityBase) getActivity(),
                        getString(R.string.msg_restrict_sure, selected.title),
                        new Util.DoubtListener() {
                            @Override
                            public void onSure() {
                                rvAdapter.restrict(getContext());
                            }
                        });
            }
        });

        return main;
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter ifPackage = new IntentFilter();
        ifPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_CHANGED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        ifPackage.addDataScheme("package");
        getContext().registerReceiver(packageChangedReceiver, ifPackage);

        loadData();
    }

    @Override
    public void onPause() {
        super.onPause();

        getContext().unregisterReceiver(packageChangedReceiver);
    }

    public AdapterApp.enumShow getShow() {
        return this.show;
    }

    public void setShow(AdapterApp.enumShow value) {
        this.show = value;
        if (rvAdapter != null)
            rvAdapter.setShow(value);
    }

    public void filter(String query) {
        if (rvAdapter != null)
            rvAdapter.getFilter().filter(query);
    }

    private void loadData() {
        Log.i(TAG, "Starting data loader");
        LoaderManager manager = getActivity().getSupportLoaderManager();
        manager.restartLoader(ActivityMain.LOADER_DATA, new Bundle(), dataLoaderCallbacks).forceLoad();
    }

    LoaderManager.LoaderCallbacks dataLoaderCallbacks = new LoaderManager.LoaderCallbacks<DataHolder>() {
        @Override
        public Loader<DataHolder> onCreateLoader(int id, Bundle args) {
            return new DataLoader(getContext());
        }

        @Override
        public void onLoadFinished(Loader<DataHolder> loader, DataHolder data) {
            if (data.exception == null) {
                ActivityBase activity = (ActivityBase) getActivity();
                if (!data.theme.equals(activity.getThemeName()))
                    activity.recreate();

                spAdapter.clear();
                spAdapter.addAll(data.groups);

                show = data.show;
                rvAdapter.setShow(data.show);
                rvAdapter.set(data.collection, data.hooks, data.apps);

                swipeRefresh.setRefreshing(false);
                pbApplication.setVisibility(View.GONE);
                grpApplication.setVisibility(View.VISIBLE);

                XGroup selected = (XGroup) spGroup.getSelectedItem();
                String group = (selected == null ? null : selected.name);
                tvRestrict.setVisibility(group == null ? View.VISIBLE : View.GONE);
                btnRestrict.setVisibility(group == null ? View.INVISIBLE : View.VISIBLE);
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
                data.theme = XProvider.getSetting(getContext(), "global", "theme");
                if (data.theme == null)
                    data.theme = "light";

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
                                .call(XProvider.getURI(), "xlua", "putHook", args);
                    }
                }

                String show = XProvider.getSetting(getContext(), "global", "show");
                if (show != null && show.equals("user"))
                    data.show = AdapterApp.enumShow.user;
                else if (show != null && show.equals("all"))
                    data.show = AdapterApp.enumShow.all;
                else
                    data.show = AdapterApp.enumShow.icon;

                // Get collection
                String collection = XProvider.getSetting(getContext(), "global", "collection");
                if (collection == null)
                    data.collection.add("Privacy");
                else
                    Collections.addAll(data.collection, collection.split(","));

                // Load groups
                Resources res = getContext().getResources();
                Bundle result = getContext().getContentResolver()
                        .call(XProvider.getURI(), "xlua", "getGroups", new Bundle());
                if (result != null)
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
                            .query(XProvider.getURI(), new String[]{"xlua.getHooks2"}, null, null, null);
                    while (chooks != null && chooks.moveToNext()) {
                        byte[] marshaled = chooks.getBlob(0);
                        Parcel parcel = Parcel.obtain();
                        parcel.unmarshall(marshaled, 0, marshaled.length);
                        parcel.setDataPosition(0);
                        XHook hook = XHook.CREATOR.createFromParcel(parcel);
                        parcel.recycle();
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
                            .query(XProvider.getURI(), new String[]{"xlua.getApps2"}, null, null, null);
                    while (capps != null && capps.moveToNext()) {
                        byte[] marshaled = capps.getBlob(0);
                        Parcel parcel = Parcel.obtain();
                        parcel.unmarshall(marshaled, 0, marshaled.length);
                        parcel.setDataPosition(0);
                        XApp app = XApp.CREATOR.createFromParcel(parcel);
                        parcel.recycle();
                        data.apps.add(app);
                    }
                } finally {
                    if (capps != null)
                        capps.close();
                }
            } catch (Throwable ex) {
                data.collection = null;
                data.groups.clear();
                data.hooks.clear();
                data.apps.clear();
                data.exception = ex;
            }

            Log.i(TAG, "Data loader finished groups=" + data.groups.size() +
                    " hooks=" + data.hooks.size() + " apps=" + data.apps.size());
            return data;
        }
    }

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            String packageName = intent.getData().getSchemeSpecificPart();
            int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
            Log.i(TAG, "pkg=" + packageName + ":" + uid);
            loadData();
        }
    };

    private static class DataHolder {
        AdapterApp.enumShow show;
        String theme;
        List<String> collection = new ArrayList<>();
        List<XGroup> groups = new ArrayList<>();
        List<XHook> hooks = new ArrayList<>();
        List<XApp> apps = new ArrayList<>();
        Throwable exception = null;
    }
}
