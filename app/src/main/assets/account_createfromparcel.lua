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

    local clsAm = luajava.bindClass('android.accounts.AccountManager')
    local am = clsAm:get(param:getApplicationContext())
    local auths = am:getAuthenticatorTypes()

    local restricted = true
    local packageName = param:getPackageName()
    for index = 1, auths['length'] do
        local auth = auths[index]
        if result.type == auth.type and auth.packageName == packageName then
            restricted = false
            break
        end
    end

    --log((restricted and 'Restricted' or 'Allowed') .. ' account ' .. result.type .. '/' .. result.name)
    if restricted then
        local old = result.name
        local fake = param:getSetting('value.email')
        if fake == nil then
            result.name = 'private@lua.xprivacy.eu'
        else
            result.name = fake
        end
        return true, old, fake
    else
        return false
    end
end
