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

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class XHook implements Parcelable {
    private String collection;
    private String group;
    private String name;
    private String author;

    private String className;
    private String methodName;
    private String[] parameterTypes;
    private String returnType;

    private int minSdk;
    private int maxSdk;
    private boolean enabled;

    private String luaScript;

    public XHook() {
    }

    public String getId() {
        return this.collection + "." + this.name;
    }

    @SuppressWarnings("unused")
    public String getCollection() {
        return this.collection;
    }

    public String getGroup() {
        return this.group;
    }

    public String getName() {
        return this.name;
    }

    @SuppressWarnings("unused")
    public String getAuthor() {
        return this.author;
    }

    public String getClassName() {
        return this.className;
    }

    public String getMethodName() {
        return this.methodName;
    }

    public String[] getParameterTypes() {
        return this.parameterTypes;
    }

    public String getReturnType() {
        return this.returnType;
    }

    public int getMinSdk() {
        return this.minSdk;
    }

    public int getMaxSdk() {
        return this.maxSdk;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public String getLuaScript() {
        return this.luaScript;
    }

    public void setLuaScript(String script) {
        this.luaScript = script;
    }

    public static final Parcelable.Creator<XHook> CREATOR = new Parcelable.Creator<XHook>() {
        public XHook createFromParcel(Parcel in) {
            return new XHook(in);
        }

        public XHook[] newArray(int size) {
            return new XHook[size];
        }
    };

    private XHook(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        writeString(out, this.collection);
        writeString(out, this.group);
        writeString(out, this.name);
        writeString(out, this.author);

        writeString(out, this.className);
        writeString(out, this.methodName);

        int argc = (this.parameterTypes == null ? -1 : this.parameterTypes.length);
        out.writeInt(argc);
        for (int i = 0; i < argc; i++)
            out.writeString(this.parameterTypes[i]);

        writeString(out, this.returnType);

        out.writeInt(this.minSdk);
        out.writeInt(this.maxSdk);
        out.writeInt(this.enabled ? 1 : 0);

        writeString(out, this.luaScript);
    }

    private void writeString(Parcel out, String value) {
        out.writeInt(value == null ? 1 : 0);
        if (value != null)
            out.writeString(value);
    }

    private void readFromParcel(Parcel in) {
        this.collection = readString(in);
        this.group = readString(in);
        this.name = readString(in);
        this.author = readString(in);

        this.className = readString(in);
        this.methodName = readString(in);

        int argc = in.readInt();
        this.parameterTypes = (argc < 0 ? null : new String[argc]);
        for (int i = 0; i < argc; i++)
            this.parameterTypes[i] = in.readString();

        this.returnType = readString(in);

        this.minSdk = in.readInt();
        this.maxSdk = in.readInt();
        this.enabled = (in.readInt() == 1);

        this.luaScript = readString(in);
    }

    private String readString(Parcel in) {
        return (in.readInt() > 0 ? null : in.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String toJSON() throws JSONException {
        return toJSONObject().toString(2);
    }

    public JSONObject toJSONObject() throws JSONException {
        JSONObject jroot = new JSONObject();

        jroot.put("collection", this.collection);
        jroot.put("group", this.group);
        jroot.put("name", this.name);
        jroot.put("author", this.author);

        jroot.put("className", this.className);
        jroot.put("methodName", this.methodName);

        JSONArray jparam = new JSONArray();
        for (int i = 0; i < this.parameterTypes.length; i++)
            jparam.put(this.parameterTypes[i]);
        jroot.put("parameterTypes", jparam);

        jroot.put("returnType", this.returnType);

        jroot.put("minSdk", this.minSdk);
        jroot.put("maxSdk", this.maxSdk);
        jroot.put("enabled", this.enabled);

        jroot.put("luaScript", this.luaScript);

        return jroot;
    }

    public static XHook fromJSON(String json) throws JSONException {
        return fromJSONObject(new JSONObject(json));
    }

    public static XHook fromJSONObject(JSONObject jroot) throws JSONException {
        XHook hook = new XHook();

        hook.collection = jroot.getString("collection");
        hook.group = jroot.getString("group");
        hook.name = jroot.getString("name");
        hook.author = jroot.getString("author");

        hook.className = jroot.getString("className");
        hook.methodName = jroot.getString("methodName");

        JSONArray jparam = jroot.getJSONArray("parameterTypes");
        hook.parameterTypes = new String[jparam.length()];
        for (int i = 0; i < jparam.length(); i++)
            hook.parameterTypes[i] = jparam.getString(i);

        hook.returnType = jroot.getString("returnType");

        hook.minSdk = jroot.getInt("minSdk");
        hook.maxSdk = jroot.getInt("maxSdk");
        hook.enabled = jroot.getBoolean("enabled");

        hook.luaScript = jroot.getString("luaScript");

        return hook;
    }

    @Override
    public String toString() {
        return this.getId() + "@" + this.className + ":" + this.methodName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof XHook))
            return false;
        XHook other = (XHook) obj;
        return this.getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return this.getId().hashCode();
    }
}
