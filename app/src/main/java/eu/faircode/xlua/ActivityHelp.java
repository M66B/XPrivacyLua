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

import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Calendar;

public class ActivityHelp extends ActivityBase {
    private static final String TAG = "XLua.Help";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.help);

        getSupportActionBar().setTitle(R.string.menu_help);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView tvVersion = findViewById(R.id.tvVersion);
        TextView tvLicense = findViewById(R.id.tvLicense);
        TextView tvInstructions = findViewById(R.id.tvInstructions);
        ImageView ivInstalled = findViewById(R.id.ivInstalled);
        TextView tvInstalled = findViewById(R.id.tvInstalled);

        tvLicense.setMovementMethod(LinkMovementMethod.getInstance());
        tvInstructions.setMovementMethod(LinkMovementMethod.getInstance());

        int year = Calendar.getInstance().get(Calendar.YEAR);

        tvVersion.setText(BuildConfig.VERSION_NAME);
        tvLicense.setText(Html.fromHtml(getString(R.string.title_license, year)));
        tvInstructions.setText(Html.fromHtml(getString(R.string.title_help_instructions)));

        ivInstalled.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        tvInstalled.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "Selected option " + item.getTitle());
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
