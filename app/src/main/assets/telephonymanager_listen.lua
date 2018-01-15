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

function before(hook, param)
    local restricted = false;
    local events = param:getArgument(1)
    if hasbit(events, 16) then -- cell location
        restricted = true
        events = clearbit(events, 16)
    end
    if hasbit(events, 1024) then -- cell info
        restricted = true
        events = clearbit(events, 1024)
    end
    if restricted then
        param:setArgument(1, events)
    end
    return restricted
end

function bit(p)
    return 2 ^ (p - 1)
end

function hasbit(x, p)
    return x % (p + p) >= p
end

function setbit(x, p)
    return hasbit(x, p) and x or x + p
end

function clearbit(x, p)
    return hasbit(x, p) and x - p or x
end
