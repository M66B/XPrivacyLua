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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentMain extends Fragment {
    private final static String TAG = "XLua.Main";

    private boolean showAll = false;
    private String query = null;
    private AdapterApp rvAdapter;

    private ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View main = inflater.inflate(R.layout.restrictions, container, false);

        // Initialize app list
        RecyclerView rvApplication = main.findViewById(R.id.rvApplication);
        rvApplication.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setAutoMeasureEnabled(true);
        rvApplication.setLayoutManager(llm);
        rvAdapter = new AdapterApp(getActivity());
        rvApplication.setAdapter(rvAdapter);

        return main;
    }

    @Override
    public void onResume() {
        super.onResume();

        try {
            XService.getClient().registerEventListener(eventListener);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
        }

        // Listen for package changes
        IntentFilter piff = new IntentFilter();
        piff.addAction(Intent.ACTION_PACKAGE_ADDED); // installed
        piff.addAction(Intent.ACTION_PACKAGE_CHANGED); // enabled/disabled
        piff.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED); // uninstalled
        piff.addDataScheme("package");
        getActivity().registerReceiver(packageChangedReceiver, piff);

        // Load data
        updateData();
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            XService.getClient().unregisterEventListener(eventListener);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
        }

        getActivity().unregisterReceiver(packageChangedReceiver);
    }

    private void updateData() {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    IService client = XService.getClient();
                    final List<XHook> hooks = client.getHooks();
                    final List<XApp> apps = client.getApps();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            rvAdapter.set(showAll, query, hooks, apps);
                        }
                    });
                } catch (Throwable ex) {
                    Log.e(TAG, Log.getStackTraceString(ex));
                    Snackbar.make(getView(), ex.toString(), Snackbar.LENGTH_LONG).show();
                }
            }
        });
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

    private IEventListener.Stub eventListener = new IEventListener.Stub() {
        @Override
        public void usageDataChanged() throws RemoteException {
            Log.i(TAG, "Usage data changed");
            updateData();
        }
    };

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            String pkg = intent.getData().getSchemeSpecificPart();
            int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);
            Log.i(TAG, "pkg=" + pkg + ":" + uid);
            updateData();
        }
    };
}
