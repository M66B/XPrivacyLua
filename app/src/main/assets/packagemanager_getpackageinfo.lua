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
    local result = param:getResult()
    if result == nil then
        return false
    end

    local cuid = param:getUid()
    local uid = result.applicationInfo.uid

    local ai = luajava.bindClass('android.content.pm.ApplicationInfo')
    local system = bit32.bor(ai.FLAG_SYSTEM, ai.FLAG_UPDATED_SYSTEM_APP)

    if uid ~= cuid and bit32.band(result.applicationInfo.flags, system) == 0 then
        local name = param:getArgument(0)

        if type(name) ~= 'string' then -- VersionedPackage
            name = name:getPackageName()
        end

        local ex = luajava.newInstance('android.content.pm.PackageManager$NameNotFoundException', name)
        param:setResult(ex)
        return true
    end

    return false
end
