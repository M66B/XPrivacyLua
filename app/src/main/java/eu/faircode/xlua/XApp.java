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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class XApp {
    String packageName;
    int uid;
    int icon;
    String label;
    boolean enabled;
    boolean persistent;
    List<XAssignment> assignments;

    XApp() {
    }

    String toJSON() throws JSONException {
        return toJSONObject().toString(2);
    }

    JSONObject toJSONObject() throws JSONException {
        JSONObject jroot = new JSONObject();

        jroot.put("packageName", this.packageName);
        jroot.put("uid", this.uid);
        jroot.put("icon", this.icon);
        jroot.put("label", this.label);
        jroot.put("enabled", this.enabled);
        jroot.put("persistent", this.persistent);

        JSONArray jassignments = new JSONArray();
        for (XAssignment assignment : this.assignments)
            jassignments.put(assignment.toJSONObject());
        jroot.put("assignments", jassignments);

        return jroot;
    }

    static XApp fromJSON(String json) throws JSONException {
        return fromJSONObject(new JSONObject(json));
    }

    static XApp fromJSONObject(JSONObject jroot) throws JSONException {
        XApp app = new XApp();

        app.packageName = jroot.getString("packageName");
        app.uid = jroot.getInt("uid");
        app.icon = jroot.getInt("icon");
        app.label = (jroot.has("label") ? jroot.getString("label") : null);
        app.enabled = jroot.getBoolean("enabled");
        app.persistent = jroot.getBoolean("persistent");

        app.assignments = new ArrayList<>();
        JSONArray jassignment = jroot.getJSONArray("assignments");
        for (int i = 0; i < jassignment.length(); i++)
            app.assignments.add(XAssignment.fromJSONObject((JSONObject) jassignment.get(i)));

        return app;
    }

    private IListener listener = null;

    void setListener(IListener listener) {
        this.listener = listener;
    }

    void notifyChanged() {
        if (this.listener != null)
            this.listener.onChange();
    }

    public interface IListener {
        void onChange();
    }
}
