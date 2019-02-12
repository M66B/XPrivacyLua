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

-- Copyright 2017-2019 Marcel Bokhorst (M66B)

function after(hook, param)
    local intent = param:getResult()
    if intent == nil then
        return false
    end

    local action = intent:getAction()
    if action == nil then
        return false
    end

    local h = hook:getName()
    local match = string.gmatch(h, '[^/]+')
    local func = match()
    local name = match()

    if name == 'package' and (action == 'android.intent.action.PACKAGE_ADDED' or
            action == 'android.intent.action.PACKAGE_CHANGED' or
            action == 'android.intent.action.PACKAGE_DATA_CLEARED' or
            action == 'android.intent.action.PACKAGE_FIRST_LAUNCH' or
            action == 'android.intent.action.PACKAGE_FULLY_REMOVED' or
            action == 'android.intent.action.PACKAGE_INSTALL' or
            action == 'android.intent.action.PACKAGE_NEEDS_VERIFICATION' or
            action == 'android.intent.action.PACKAGE_REMOVED' or
            action == 'android.intent.action.PACKAGE_REPLACED' or
            action == 'android.intent.action.PACKAGE_RESTARTED' or
            action == 'android.intent.action.PACKAGE_VERIFIED') then
        local uriClass = luajava.bindClass('android.net.Uri')
        local uri = uriClass:parse('package:' .. param:getPackageName())
        intent:setData(uri)
        return true
    elseif name == 'package' and (action == 'android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE' or
            action == 'android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE' or
            action == 'android.intent.action.PACKAGES_SUSPENDED' or
            action == 'android.intent.action.PACKAGES_UNSUSPENDED') then
        local stringClass = luajava.bindClass('java.lang.String')
        local arrayClass = luajava.bindClass('java.lang.reflect.Array')
        local stringArray = arrayClass:newInstance(stringClass, 0)
        intent:putExtra('android.intent.extra.changed_package_list', stringArray)
        return true
    elseif name == 'message' and action == 'android.provider.Telephony.SMS_RECEIVED' then
        local objectClass = luajava.bindClass('java.lang.Object')
        local arrayClass = luajava.bindClass('java.lang.reflect.Array')
        local objectArray = arrayClass:newInstance(objectClass, 0)
        intent:putExtra('pdus', objectArray)
        return true
    else
        return false
    end
end
