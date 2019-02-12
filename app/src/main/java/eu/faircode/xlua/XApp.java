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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class XApp implements Parcelable {
    String packageName;
    int uid;
    int icon;
    String label;
    boolean enabled;
    boolean persistent;
    boolean system;
    boolean forceStop = true;
    List<XAssignment> assignments;

    XApp() {
    }

    List<XAssignment> getAssignments(String group) {
        if (group == null)
            return assignments;

        List<XAssignment> filtered = new ArrayList<>();
        for (XAssignment assignment : assignments)
            if (group.equals(assignment.hook.getGroup()))
                filtered.add(assignment);

        return filtered;
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
        jroot.put("system", this.system);
        jroot.put("forcestop", this.forceStop);

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
        app.system = jroot.getBoolean("system");
        app.forceStop = jroot.getBoolean("forcestop");

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

    void notifyAssign(Context context, String groupName, boolean assign) {
        if (this.listener != null)
            this.listener.onAssign(context, groupName, assign);
    }

    public interface IListener {
        void onAssign(Context context, String groupName, boolean assign);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof XApp))
            return false;
        XApp other = (XApp) obj;
        return (this.packageName.equals(other.packageName) && this.uid == other.uid);
    }

    @Override
    public int hashCode() {
        return this.packageName.hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.packageName);
        dest.writeInt(this.uid);
        dest.writeInt(this.icon);
        dest.writeString(this.label);
        dest.writeByte(this.enabled ? (byte) 1 : (byte) 0);
        dest.writeByte(this.persistent ? (byte) 1 : (byte) 0);
        dest.writeByte(this.system ? (byte) 1 : (byte) 0);
        dest.writeByte(this.forceStop ? (byte) 1 : (byte) 0);
        dest.writeTypedList(this.assignments);
    }

    protected XApp(Parcel in) {
        this.packageName = in.readString();
        this.uid = in.readInt();
        this.icon = in.readInt();
        this.label = in.readString();
        this.enabled = (in.readByte() != 0);
        this.persistent = (in.readByte() != 0);
        this.system = (in.readByte() != 0);
        this.forceStop = (in.readByte() != 0);
        this.assignments = in.createTypedArrayList(XAssignment.CREATOR);
    }

    public static final Parcelable.Creator<XApp> CREATOR = new Parcelable.Creator<XApp>() {
        @Override
        public XApp createFromParcel(Parcel source) {
            return new XApp(source);
        }

        @Override
        public XApp[] newArray(int size) {
            return new XApp[size];
        }
    };
}
