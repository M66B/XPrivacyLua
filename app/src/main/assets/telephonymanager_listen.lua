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

function before(hook, param)
    local restricted = false;
    local events = param:getArgument(1)
    if bit32.band(events, 16) ~= 0 then -- cell location
        restricted = true
        events = bit32.bxor(events, 16)
    end
    if bit32.band(events, 1024) ~= 0 then -- cell info
        restricted = true
        events = bit32.bxor(events, 1024)
    end
    if restricted then
        param:setArgument(1, events)
    end
    return restricted
end
