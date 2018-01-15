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
    else
        local latitude = 0
        local longitude = 0
        local type = param:getSetting('location.type')
        if type == 'set' then
            latitude = param:getSetting('location.latitude')
            longitude = param:getSetting('location.longitude')
            if latitude == nil or longitude == nil then
                latitude = 0
                longitude = 0
            end
        elseif type == 'coarse' then
            local accuracy = param:getSetting('location.accuracy') -- meters
            if accuracy ~= nil then
                accuracy = accuracy / 111000
                latitude = math.floor(result:getLatitude() / accuracy) * accuracy
                longitude = math.floor(result:getLongitude() / accuracy) * accuracy
            end
        end

        if result:hasAccuracy() then
            local radius = result:getAccuracy() / 111000
            latitude = latitude + radius * 2 * math.random() - radius
            longitude = longitude + radius * 2 * math.random() - radius
        end

        result:setLatitude(latitude)
        result:setLongitude(longitude)
        log(result)
        return true
    end
end
