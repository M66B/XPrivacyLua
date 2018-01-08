-- This file is part of XPrivacy/Lua.

-- XPrivacy/Lua is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.

-- XPrivacy/Lua is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.

-- You should have received a copy of the GNU General Public License
-- along with XPrivacy/Lua.  If not, see <http://www.gnu.org/licenses/>.

-- Copyright 2017-2018 Marcel Bokhorst (M66B)

function after(hook, param)
    uri = param:getArgument(0)
    cursor = param:getResult()
    if uri == nil or uri:getPath() == nil or cursor == nil then
        return false
    elseif uri:getAuthority() == 'com.android.contacts' then
        prefix = string.gmatch(uri:getPath(), '[^/]+')()
        if prefix == 'provider_status' then
            return false
        else
            result = luajava.newInstance('android.database.MatrixCursor', cursor:getColumnNames())
            result:setExtras(cursor:getExtras())
            notify = cursor:getNotificationUri()
            --if notify ~= nil then
            --    result:setNotificationUri(param:getThis(), notify)
            --end
            param:setResult(result);
            return true
        end
    else
        return false
    end
end
