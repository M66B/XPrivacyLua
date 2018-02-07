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
    local name = hook:getName()
    local command = param:getArgument(0)
    if command == nil then
        log('null')
    elseif name == 'Runtime.exec/cmd' or
            name == 'Runtime.exec/cmd/env' or
            name == 'Runtime.exec/cmd/env/file' then
        log(command)
    else
        if name == 'ProcessBuilder.command/list' then
            command = command:toArray()
        end

        local index
        local array = luajava.bindClass('java.lang.reflect.Array')
        local length = array:getLength(command)
        for index = 0, length - 1 do
            log(array:get(command, index))
        end
    end
    return false
end