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
    local ai = param:getApplicationContext():getApplicationInfo()
    local dataDir = ai.dataDir .. '\/'
    local clsFile = luajava.bindClass('java.io.File')
    local sourceDir = luajava.new(clsFile, ai.sourceDir):getParent() .. '\/'

    local path = param:getArgument(0)
    if path == nil or
            string.sub(path, 1, string.len(dataDir)) == dataDir or
            string.sub(path, 1, string.len(sourceDir)) == sourceDir then
        log('Allow ' .. path)
        return false
    else
        log('Deny ' .. path)
        local clsFileNotFound = luajava.bindClass('java.io.FileNotFoundException')
        local fake = luajava.new(clsFileNotFound, path)
        param:setResult(fake)
        return true
    end
end
