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

import java.util.ArrayList;
import java.util.List;

public class XApp implements Parcelable {
    String packageName;
    int uid;
    int icon;
    String label;
    boolean enabled;
    boolean persistent;
    List<XAssignment> assignments;

    public XApp() {
    }

    static final Parcelable.Creator<XApp> CREATOR = new Parcelable.Creator<XApp>() {
        public XApp createFromParcel(Parcel in) {
            return new XApp(in);
        }

        public XApp[] newArray(int size) {
            return new XApp[size];
        }
    };

    private XApp(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        writeString(out, this.packageName);
        out.writeInt(this.uid);
        out.writeInt(this.icon);
        writeString(out, this.label);
        out.writeInt(this.enabled ? 1 : 0);
        out.writeInt(this.persistent ? 1 : 0);

        int hookc = (this.assignments == null ? -1 : this.assignments.size());
        out.writeInt(hookc);
        for (int i = 0; i < hookc; i++)
            out.writeParcelable(this.assignments.get(i), 0);
    }

    private void writeString(Parcel out, String value) {
        out.writeInt(value == null ? 1 : 0);
        if (value != null)
            out.writeString(value);
    }

    private void readFromParcel(Parcel in) {
        this.packageName = readString(in);
        this.uid = in.readInt();
        this.icon = in.readInt();
        this.label = readString(in);
        this.enabled = (in.readInt() != 0);
        this.persistent = (in.readInt() != 0);

        int hookc = in.readInt();
        this.assignments = (hookc < 0 ? null : new ArrayList<XAssignment>());
        for (int i = 0; i < hookc; i++)
            this.assignments.add((XAssignment) in.readParcelable(XAssignment.class.getClassLoader()));
    }

    private String readString(Parcel in) {
        return (in.readInt() > 0 ? null : in.readString());
    }

    @Override
    public int describeContents() {
        return 0;
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
