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

import org.json.JSONException;
import org.json.JSONObject;

class XAssignment {
    XHook hook;
    long installed = -1;
    long used = -1;
    boolean restricted = false;
    String exception;

    private XAssignment() {
    }

    XAssignment(XHook hook) {
        this.hook = hook;
    }

    String toJSON() throws JSONException {
        return toJSONObject().toString(2);
    }

    JSONObject toJSONObject() throws JSONException {
        JSONObject jroot = new JSONObject();

        jroot.put("hook", this.hook.toJSONObject());
        jroot.put("installed", this.installed);
        jroot.put("used", this.used);
        jroot.put("restricted", this.restricted);
        jroot.put("exception", this.exception);

        return jroot;
    }

    static XAssignment fromJSON(String json) throws JSONException {
        return fromJSONObject(new JSONObject(json));
    }

    static XAssignment fromJSONObject(JSONObject jroot) throws JSONException {
        XAssignment assignment = new XAssignment();

        assignment.hook = XHook.fromJSONObject(jroot.getJSONObject("hook"));
        assignment.installed = jroot.getLong("installed");
        assignment.used = jroot.getLong("used");
        assignment.restricted = jroot.getBoolean("restricted");
        assignment.exception = (jroot.has("exception") ? jroot.getString("exception") : null);

        return assignment;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof XAssignment))
            return false;
        XAssignment other = (XAssignment) obj;
        return this.hook.getId().equals(other.hook.getId());
    }

    @Override
    public int hashCode() {
        return this.hook.getId().hashCode();
    }
}
