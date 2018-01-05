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
    key = param:getArg(0)
    if key == 'location' then
        loc = luajava.newInstance('android.location.Location', 'privacy')
        loc:setLatitude(0)
        loc:setLongitude(0)
        param:setResult(loc)
        return true
    else
        return false
    end
end
