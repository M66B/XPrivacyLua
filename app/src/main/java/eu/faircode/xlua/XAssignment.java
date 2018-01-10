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
