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

import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.Calendar;

public class ActivityHelp extends AppCompatActivity {
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

        tvLicense.setMovementMethod(LinkMovementMethod.getInstance());
        tvInstructions.setMovementMethod(LinkMovementMethod.getInstance());

        int year = Calendar.getInstance().get(Calendar.YEAR);

        tvVersion.setText(Util.getSelfVersionName(this));
        tvLicense.setText(Html.fromHtml(getString(R.string.title_license, year)));
        tvInstructions.setText(Html.fromHtml(getString(R.string.title_help_instructions)));
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
