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

import eu.faircode.xlua.XHook;
import eu.faircode.xlua.XApp;
import eu.faircode.xlua.IEventListener;

interface IService {
    // This needs to be the first method
    int getVersion();

    List<XHook> getHooks();
    List<XApp> getApps();
    void assignHooks(in List<String> hookid, String packageName, int uid, boolean delete, boolean kill);
    List<XHook> getAssignedHooks(String packageName, int uid);

    void registerEventListener(IEventListener listener);
    void unregisterEventListener(IEventListener listener);

    void report(String hookid, String packageName, int uid, String event, in Bundle data);
    void notify(int what, in Bundle data);

    String getSetting(int userid, String category, String name);
    void putSetting(int userid, String category, String name, String value);

    void clearData();
}
