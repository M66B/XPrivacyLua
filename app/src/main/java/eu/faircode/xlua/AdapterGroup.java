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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdapterGroup extends RecyclerView.Adapter<AdapterGroup.ViewHolder> {
    private static final String TAG = "XLua.Group";

    private XApp app;
    private List<Group> groups = new ArrayList<>();

    private ExecutorService executor = Executors.newCachedThreadPool();

    public class ViewHolder extends RecyclerView.ViewHolder
            implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {
        Group group;

        final View itemView;
        final ImageView ivException;
        final ImageView ivInstalled;
        final TextView tvUsed;
        final TextView tvGroup;
        final AppCompatCheckBox cbAssigned;

        ViewHolder(View itemView) {
            super(itemView);

            this.itemView = itemView;
            ivException = itemView.findViewById(R.id.ivException);
            ivInstalled = itemView.findViewById(R.id.ivInstalled);
            tvUsed = itemView.findViewById(R.id.tvUsed);
            tvGroup = itemView.findViewById(R.id.tvGroup);
            cbAssigned = itemView.findViewById(R.id.cbAssigned);
        }

        private void wire() {
            ivException.setOnClickListener(this);
            tvGroup.setOnClickListener(this);
            cbAssigned.setOnCheckedChangeListener(this);
        }

        private void unwire() {
            ivException.setOnClickListener(null);
            tvGroup.setOnClickListener(null);
            cbAssigned.setOnCheckedChangeListener(null);
        }

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.ivException:
                    StringBuilder sb = new StringBuilder();
                    for (XAssignment assignment : app.assignments)
                        if (assignment.hook.getGroup().equals(group.name))
                            if (assignment.exception != null) {
                                sb.append("<b>");
                                sb.append(Html.escapeHtml(assignment.hook.getId()));
                                sb.append("</b><br>");
                                sb.append(Html.escapeHtml(assignment.exception).replace("\n", "<br>"));
                                sb.append("<br>");
                            }

                    LayoutInflater inflater = LayoutInflater.from(view.getContext());
                    View alert = inflater.inflate(R.layout.exception, null, false);
                    TextView tvException = alert.findViewById(R.id.tvException);
                    tvException.setText(Html.fromHtml(sb.toString()));

                    new AlertDialog.Builder(view.getContext())
                            .setView(alert)
                            .create()
                            .show();
                    break;

                case R.id.tvGroup:
                    cbAssigned.setChecked(!cbAssigned.isChecked());
                    break;
            }
        }

        @Override
        public void onCheckedChanged(final CompoundButton compoundButton, final boolean checked) {
            switch (compoundButton.getId()) {
                case R.id.cbAssigned:
                    for (XHook hook : group.hooks) {
                        app.assignments.remove(new XAssignment(hook));
                        if (checked)
                            app.assignments.add(new XAssignment(hook));
                    }

                    app.notifyChanged();

                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<String> hookids = new ArrayList<>();
                            for (XHook hook : group.hooks)
                                hookids.add(hook.getId());

                            Bundle args = new Bundle();
                            args.putStringArrayList("hooks", hookids);
                            args.putString("packageName", app.packageName);
                            args.putInt("uid", app.uid);
                            args.putBoolean("delete", !checked);
                            args.putBoolean("kill", !app.persistent);
                            compoundButton.getContext().getContentResolver()
                                    .call(XSettings.URI, "xlua", "assignHooks", args);
                        }
                    });
                    break;
            }
        }
    }

    AdapterGroup() {
        setHasStableIds(true);
    }

    void set(XApp app, List<XHook> hooks, Context context) {
        this.app = app;

        Map<String, Group> map = new HashMap<>();
        for (XHook hook : hooks) {
            Group group;
            if (map.containsKey(hook.getGroup()))
                group = map.get(hook.getGroup());
            else {
                group = new Group();

                Resources resources = context.getResources();
                String name = hook.getGroup().toLowerCase().replaceAll("[^a-z]", "_");
                group.id = resources.getIdentifier("group_" + name, "string", context.getPackageName());
                group.name = hook.getGroup();
                group.title = resources.getString(group.id);

                map.put(hook.getGroup(), group);
            }
            group.hooks.add(hook);
        }

        for (String groupid : map.keySet()) {
            for (XAssignment assignment : app.assignments)
                if (assignment.hook.getGroup().equals(groupid)) {
                    Group group = map.get(groupid);
                    if (assignment.exception != null)
                        group.exception = true;
                    if (assignment.installed >= 0)
                        group.installed++;
                    if (assignment.hook.isOptional())
                        group.optional++;
                    if (assignment.restricted)
                        group.used = Math.max(group.used, assignment.used);
                    group.assigned++;
                }
        }

        this.groups = new ArrayList<>(map.values());

        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY); // Case insensitive, process accents etc
        Collections.sort(this.groups, new Comparator<Group>() {
            @Override
            public int compare(Group group1, Group group2) {
                return collator.compare(group1.title, group2.title);
            }
        });

        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return groups.get(position).id;
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.group, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.unwire();
        holder.group = groups.get(position);

        // Get localized group name
        Context context = holder.itemView.getContext();
        Resources resources = holder.itemView.getContext().getResources();

        holder.ivException.setVisibility(holder.group.hasException() ? View.VISIBLE : View.GONE);
        holder.ivInstalled.setVisibility(holder.group.hasInstalled() ? View.VISIBLE : View.GONE);
        holder.ivInstalled.setAlpha(holder.group.allInstalled() ? 1.0f : 0.5f);
        holder.tvUsed.setVisibility(holder.group.lastUsed() < 0 ? View.GONE : View.VISIBLE);
        holder.tvUsed.setText(DateUtils.formatDateTime(context, holder.group.lastUsed(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL));
        holder.tvGroup.setText(holder.group.title);
        holder.cbAssigned.setChecked(holder.group.hasAssigned());
        holder.cbAssigned.setButtonTintList(ColorStateList.valueOf(resources.getColor(
                holder.group.allAssigned() ? R.color.colorAccent : android.R.color.darker_gray, null)));

        holder.wire();
    }

    private class Group {
        int id;
        String name;
        String title;
        boolean exception = false;
        int installed = 0;
        int optional = 0;
        long used = -1;
        int assigned = 0;
        List<XHook> hooks = new ArrayList<>();

        Group() {
        }

        boolean hasException() {
            return (assigned > 0 && exception);
        }

        boolean hasInstalled() {
            return (assigned > 0 && installed > 0);
        }

        boolean allInstalled() {
            return (assigned > 0 && installed + optional == assigned);
        }

        long lastUsed() {
            return used;
        }

        boolean hasAssigned() {
            return (assigned > 0);
        }

        boolean allAssigned() {
            return (assigned == hooks.size());
        }
    }
}
