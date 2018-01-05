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

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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

import java.util.List;

public class FragmentMain extends Fragment {
    private final static String TAG = "XLua.Main";

    private boolean showAll = false;
    private String query = null;
    private AdapterApp rvAdapter;

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

        // Load data
        Log.i(TAG, "Starting data loader");
        getActivity().getSupportLoaderManager().restartLoader(
                ActivityMain.LOADER_DATA, new Bundle(), dataLoaderCallbacks).forceLoad();
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            XService.getClient().unregisterEventListener(eventListener);
        } catch (Throwable ex) {
            Log.e(TAG, Log.getStackTraceString(ex));
        }
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
            if (data.exception == null)
                rvAdapter.set(showAll, query, data.hooks, data.apps);
            else {
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
        }

        @Nullable
        @Override
        public DataHolder loadInBackground() {
            Log.i(TAG, "Data loader started");
            DataHolder data = new DataHolder();
            try {
                IService client = XService.getClient();
                data.hooks = client.getHooks();
                data.apps = client.getApps();
            } catch (Throwable ex) {
                data.exception = ex;
            }
            Log.i(TAG, "Data loader finished");
            return data;
        }
    }

    private IEventListener.Stub eventListener = new IEventListener.Stub() {
        @Override
        public void dataChanged() throws RemoteException {
            Log.i(TAG, "Data changed");
            getActivity().getSupportLoaderManager().restartLoader(
                    ActivityMain.LOADER_DATA, new Bundle(), dataLoaderCallbacks).forceLoad();
        }

        @Override
        public void packageChanged() throws RemoteException {
            Log.i(TAG, "Package changed");
            getActivity().getSupportLoaderManager().restartLoader(
                    ActivityMain.LOADER_DATA, new Bundle(), dataLoaderCallbacks).forceLoad();
        }
    };

    private static class DataHolder {
        List<XHook> hooks = null;
        List<XApp> apps = null;
        Throwable exception = null;
    }
}
