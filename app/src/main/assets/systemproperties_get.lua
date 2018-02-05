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
    local result = param:getResult()
    if result == nil then
        return false
    end

    local key = param:getArgument(0)

    local h = hook:getName()
    local match = string.gmatch(h, '[^/]+')
    local func = match()
    local name = match()

    if func ~= 'SystemProperties.get' and func ~= 'SystemProperties.get.default' then
        log(func .. ' unsupported')
        return false
    end

    log(key .. '=' .. result .. ' name=' .. name)

    if name == 'serial' and (key == 'ro.serialno' or key == 'ro.boot.serialno') then
        local fake = param:getSetting('value.serial')
        if fake == nil then
            fake = 'unknown'
        end

        param:setResult(fake)
        return true, result, fake
    elseif name == 'vendor' and string.sub(key, 1, string.len('ro.vendor.')) == 'ro.vendor.' then
        local fake
        param:setResult(fake)
        return true, result, fake
    else
        return false
    end
end
