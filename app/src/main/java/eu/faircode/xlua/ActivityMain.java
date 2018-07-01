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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityMain extends ActivityBase {
    private final static String TAG = "XLua.Main";

    private FragmentMain fragmentMain = null;
    private DrawerLayout drawerLayout = null;
    private ListView drawerList;
    private ActionBarDrawerToggle drawerToggle = null;

    private Menu menu = null;

    private AlertDialog firstRunDialog = null;

    public static final int LOADER_DATA = 1;
    public static final String EXTRA_SEARCH_PACKAGE = "package";

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if service is running
        if (!XProvider.isAvailable(this)) {
            Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_no_service), Snackbar.LENGTH_INDEFINITE);

            final Intent intent = getPackageManager().getLaunchIntentForPackage("de.robv.android.xposed.installer");
            if (intent == null || intent.resolveActivity(getPackageManager()) != null)
                snackbar.setAction(R.string.title_fix, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startActivity(intent);
                    }
                });

            snackbar.show();
            return;
        }

        // Set layout
        setContentView(R.layout.main);

        // Prepare action bar
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Show fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentMain = new FragmentMain();
        fragmentTransaction.replace(R.id.content_frame, fragmentMain);
        fragmentTransaction.commit();

        // Get drawer layout
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.setScrimColor(Util.resolveColor(this, R.attr.colorDrawerScrim));

        // Create drawer toggle
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getSupportActionBar().setTitle(getString(R.string.app_name));
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getSupportActionBar().setTitle(getString(R.string.app_name));
            }
        };
        drawerLayout.addDrawerListener(drawerToggle);

        // Get drawer list
        drawerList = findViewById(R.id.drawer_list);

        // Handle drawer list click
        drawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DrawerItem item = (DrawerItem) parent.getAdapter().getItem(position);
                Log.i(TAG, "Drawer selected " + item.getTitle());
                item.onClick();
                if (!item.isCheckable())
                    drawerLayout.closeDrawer(drawerList);
            }
        });

        // Initialize drawer
        boolean notifyNew = XProvider.getSettingBoolean(this, "global", "notify_new_apps");
        boolean restrictNew = XProvider.getSettingBoolean(this, "global", "restrict_new_apps");

        final ArrayAdapterDrawer drawerArray = new ArrayAdapterDrawer(ActivityMain.this, R.layout.draweritem);

        drawerArray.add(new DrawerItem(this, R.string.menu_notify_new, notifyNew, new DrawerItem.IListener() {
            @Override
            public void onClick(DrawerItem item) {
                XProvider.putSettingBoolean(ActivityMain.this, "global", "notify_new_apps", item.isChecked());
                drawerArray.notifyDataSetChanged();
            }
        }));

        drawerArray.add(new DrawerItem(this, R.string.menu_restrict_new, restrictNew, new DrawerItem.IListener() {
            @Override
            public void onClick(DrawerItem item) {
                XProvider.putSettingBoolean(ActivityMain.this, "global", "restrict_new_apps", item.isChecked());
                drawerArray.notifyDataSetChanged();
            }
        }));

        drawerArray.add(new DrawerItem(this, R.string.menu_companion, new DrawerItem.IListener() {
            @Override
            public void onClick(DrawerItem item) {
                PackageManager pm = getPackageManager();
                Intent companion = pm.getLaunchIntentForPackage(Util.PRO_PACKAGE_NAME);
                if (companion == null) {
                    Intent browse = new Intent(Intent.ACTION_VIEW);
                    browse.setData(Uri.parse("https://lua.xprivacy.eu/pro/"));
                    if (browse.resolveActivity(pm) == null)
                        Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_no_browser), Snackbar.LENGTH_LONG).show();
                    else
                        startActivity(browse);
                } else
                    startActivity(companion);
            }
        }));

        drawerArray.add(new DrawerItem(this, R.string.menu_readme, new DrawerItem.IListener() {
            @Override
            public void onClick(DrawerItem item) {
                Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/M66B/XPrivacyLua"));
                if (browse.resolveActivity(getPackageManager()) == null)
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_no_browser), Snackbar.LENGTH_LONG).show();
                else
                    startActivity(browse);
            }
        }));

        drawerArray.add(new DrawerItem(this, R.string.menu_faq, new DrawerItem.IListener() {
            @Override
            public void onClick(DrawerItem item) {
                Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/M66B/XPrivacyLua/blob/master/FAQ.md"));
                if (browse.resolveActivity(getPackageManager()) == null)
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_no_browser), Snackbar.LENGTH_LONG).show();
                else
                    startActivity(browse);
            }
        }));

        drawerArray.add(new DrawerItem(this, R.string.menu_donate, new DrawerItem.IListener() {
            @Override
            public void onClick(DrawerItem item) {
                Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse("https://lua.xprivacy.eu/"));
                if (browse.resolveActivity(getPackageManager()) == null)
                    Snackbar.make(findViewById(android.R.id.content), getString(R.string.msg_no_browser), Snackbar.LENGTH_LONG).show();
                else
                    startActivity(browse);
            }
        }));

        drawerList.setAdapter(drawerArray);

        checkFirstRun();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (drawerToggle != null)
            drawerToggle.syncState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, "New " + intent);
        super.onNewIntent(intent);
        setIntent(intent);

        if (this.menu != null)
            updateMenu(this.menu);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (drawerToggle != null)
            drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(drawerList))
            drawerLayout.closeDrawer(drawerList);
        else
            finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "Create options");

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        this.menu = menu;

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.i(TAG, "Prepare options");

        // Search
        MenuItem menuSearch = menu.findItem(R.id.menu_search);
        final SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.i(TAG, "Search submit=" + query);
                if (fragmentMain != null) {
                    fragmentMain.filter(query);
                    searchView.clearFocus(); // close keyboard
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.i(TAG, "Search change=" + newText);
                if (fragmentMain != null)
                    fragmentMain.filter(newText);
                return true;
            }
        });

        menuSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                Log.i(TAG, "Search expand");
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                Log.i(TAG, "Search collapse");

                // Search uid once
                Intent intent = getIntent();
                intent.removeExtra(EXTRA_SEARCH_PACKAGE);
                setIntent(intent);

                return true;
            }
        });

        updateMenu(menu);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item))
            return true;

        Log.i(TAG, "Selected option " + item.getTitle());
        switch (item.getItemId()) {
            case R.id.menu_show:
                AdapterApp.enumShow show = (fragmentMain == null ? AdapterApp.enumShow.none : fragmentMain.getShow());
                this.menu.findItem(R.id.menu_show_user).setEnabled(show != AdapterApp.enumShow.none);
                this.menu.findItem(R.id.menu_show_icon).setEnabled(show != AdapterApp.enumShow.none);
                this.menu.findItem(R.id.menu_show_all).setEnabled(show != AdapterApp.enumShow.none);
                switch (show) {
                    case user:
                        this.menu.findItem(R.id.menu_show_user).setChecked(true);
                        break;
                    case icon:
                        this.menu.findItem(R.id.menu_show_icon).setChecked(true);
                        break;
                    case all:
                        this.menu.findItem(R.id.menu_show_all).setChecked(true);
                        break;
                }
                return true;

            case R.id.menu_show_user:
            case R.id.menu_show_icon:
            case R.id.menu_show_all:
                item.setChecked(!item.isChecked());
                final AdapterApp.enumShow set;
                switch (item.getItemId()) {
                    case R.id.menu_show_user:
                        set = AdapterApp.enumShow.user;
                        break;
                    case R.id.menu_show_all:
                        set = AdapterApp.enumShow.all;
                        break;
                    default:
                        set = AdapterApp.enumShow.icon;
                        break;
                }
                fragmentMain.setShow(set);
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        XProvider.putSetting(ActivityMain.this, "global", "show", set.name());
                    }
                });
                return true;

            case R.id.menu_help:
                menuHelp();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void menuHelp() {
        startActivity(new Intent(this, ActivityHelp.class));
    }

    public void updateMenu(Menu menu) {
        // Search
        MenuItem menuSearch = menu.findItem(R.id.menu_search);
        final SearchView searchView = (SearchView) menuSearch.getActionView();
        if (searchView != null) {
            String pkg = getIntent().getStringExtra(EXTRA_SEARCH_PACKAGE);
            if (pkg != null) {
                Log.i(TAG, "Search pkg=" + pkg);
                menuSearch.expandActionView();
                searchView.setQuery(pkg, true);
            }
        }
    }

    public void checkFirstRun() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean firstRun = prefs.getBoolean("firstrun", true);
        if (firstRun && firstRunDialog == null) {
            final Util.DialogObserver observer = new Util.DialogObserver();

            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.license, null, false);
            TextView tvLicence = view.findViewById(R.id.tvLicense);
            tvLicence.setMovementMethod(LinkMovementMethod.getInstance());

            int year = Calendar.getInstance().get(Calendar.YEAR);
            tvLicence.setText(Html.fromHtml(getString(R.string.title_license, year)));

            firstRunDialog = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.title_accept, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            prefs.edit().putBoolean("firstrun", false).apply();
                        }
                    })
                    .setNegativeButton(R.string.title_deny, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            System.exit(0);
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            firstRunDialog = null;
                            observer.stopObserving();
                        }
                    })
                    .create();
            firstRunDialog.show();

            observer.startObserving(this, firstRunDialog);
        }
    }

    private static class DrawerItem {
        private final int id;
        private final String title;
        private final boolean checkable;
        private boolean checked;
        private final IListener listener;

        DrawerItem(Context context, int title, IListener listener) {
            this.id = title;
            this.title = context.getString(title);
            this.checkable = false;
            this.checked = false;
            this.listener = listener;
        }

        DrawerItem(Context context, int title, boolean checked, IListener listener) {
            this.id = title;
            this.title = context.getString(title);
            this.checkable = true;
            this.checked = checked;
            this.listener = listener;
        }

        public int getId() {
            return this.id;
        }

        public String getTitle() {
            return this.title;
        }

        boolean isCheckable() {
            return this.checkable;
        }

        boolean isChecked() {
            return this.checked;
        }

        void onClick() {
            if (this.checkable)
                this.checked = !this.checked;
            if (this.listener != null)
                this.listener.onClick(this);
        }

        interface IListener {
            void onClick(DrawerItem item);
        }
    }

    private static class ArrayAdapterDrawer extends ArrayAdapter<DrawerItem> {
        private final int resource;

        ArrayAdapterDrawer(@NonNull Context context, int resource) {
            super(context, resource);
            this.resource = resource;
        }

        @Override
        @NonNull
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View row;
            if (null == convertView)
                row = LayoutInflater.from(getContext()).inflate(this.resource, null);
            else
                row = convertView;

            DrawerItem item = getItem(position);

            TextView tv = row.findViewById(R.id.tvItem);
            CheckBox cb = row.findViewById(R.id.cbItem);
            tv.setText(item.getTitle());
            cb.setVisibility(item.isCheckable() ? View.VISIBLE : View.GONE);
            cb.setChecked(item.isChecked());

            return row;
        }
    }
}
