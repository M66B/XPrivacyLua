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
    elseif type(command) == 'string' then
        log(command)
    else
        if name == 'ProcessBuilder.command/list' then
            command = command:toArray()
        end

        local index
        local length = command['length']
        for index = 1, length do
            log(command[index])
        end
    end
    return false
end