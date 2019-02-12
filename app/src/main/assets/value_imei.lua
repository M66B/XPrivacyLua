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
    local this = param:getThis()
    if this == nil then
        return false
    end

    local result = param:getResult()
    local type = this:getPhoneType()

    local fake
    if type == 1 then -- GSM
        fake = param:getSetting('value.imei')
    end

    if result == nil and fake == nil then
        return false
    end

    param:setResult(fake)
    return true, result, fake
end
