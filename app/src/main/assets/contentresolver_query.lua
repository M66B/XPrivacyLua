-- This file is part of XPrivacyLua.

-- XPrivacyLua is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.

-- XPrivacyLua is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.

-- You should have received a copy of the GNU General Public License
-- along with XPrivacyLua.  If not, see <http://www.gnu.org/licenses/>.

-- Copyright 2017-2018 Marcel Bokhorst (M66B)

function after(hook, param)
    local uri = param:getArgument(0)
    local cursor = param:getResult()
    if uri == nil or cursor == nil then
        return false
    end

    local match = string.gmatch(hook:getName(), '[^/]+')
    match()
    local name = match()
    local authority = uri:getAuthority()

    if (name == 'blockednumber' and authority == 'com.android.blockednumber') or
            (name == 'calendars' and authority == 'com.android.calendar') or
            (name == 'call_log' and authority == 'call_log') or
            (name == 'call_log' and authority == 'call_log_shadow') or
            (name == 'contacts' and authority == 'icc') or
            (name == 'contacts' and authority == 'com.android.contacts') or
            (name == 'mmssms' and authority == 'mms') or
            (name == 'mmssms' and authority == 'sms') or
            (name == 'mmssms' and authority == 'mms-sms') or
            (name == 'mmssms' and authority == 'com.google.android.apps.messaging.shared.datamodel.BugleContentProvider') or
            (name == 'voicemail' and authority == 'com.android.voicemail') then

        if name == 'contacts' then
            local path = uri:getPath()
            if path == nil then
                return false
            end
            local prefix = string.gmatch(path, '[^/]+')()
            if prefix == 'provider_status' then
                return false
            end
        end

        local result = luajava.newInstance('android.database.MatrixCursor', cursor:getColumnNames())
        --result:setExtras(cursor:getExtras())
        --notify = cursor:getNotificationUri()
        --if notify ~= nil then
        --    result:setNotificationUri(param:getThis(), notify)
        --end
        param:setResult(result);
        return true
    else
        return false
    end
end