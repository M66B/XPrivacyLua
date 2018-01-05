package eu.faircode.xlua;

import android.os.Parcel;
import android.os.Parcelable;

public class XAssignment implements Parcelable {
    XHook hook;
    long installed = -1;
    long used = -1;
    boolean restricted = false;
    String exception;

    XAssignment(XHook hook) {
        this.hook = hook;
    }

    public static final Parcelable.Creator<XAssignment> CREATOR = new Parcelable.Creator<XAssignment>() {
        public XAssignment createFromParcel(Parcel in) {
            return new XAssignment(in);
        }

        public XAssignment[] newArray(int size) {
            return new XAssignment[size];
        }
    };

    private XAssignment(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(this.hook, 0);
        out.writeLong(this.installed);
        out.writeLong(this.used);
        out.writeInt(this.restricted ? 1 : 0);
        writeString(out, this.exception);
    }

    private void writeString(Parcel out, String value) {
        out.writeInt(value == null ? 1 : 0);
        if (value != null)
            out.writeString(value);
    }

    private void readFromParcel(Parcel in) {
        this.hook = in.readParcelable(XHook.class.getClassLoader());
        this.installed = in.readLong();
        this.used = in.readLong();
        this.restricted = (in.readInt() == 1);
        this.exception = readString(in);
    }

    private String readString(Parcel in) {
        return (in.readInt() > 0 ? null : in.readString());
    }

    @Override
    public int describeContents() {
        return 0;
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
