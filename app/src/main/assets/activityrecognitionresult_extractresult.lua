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
        log('result null')
        return false
    end

    local loader = param:getClassLoader()
    local class = luajava.bindClass('java.lang.Class')
    local classActivity = class:forName('com.google.android.gms.location.DetectedActivity', false, loader)
    local detected = luajava.new(classActivity, 4, 100) -- unknown, 100%
    local classResult = class:forName('com.google.android.gms.location.ActivityRecognitionResult', false, loader)
    local time = result:getTime()
    local elapsed = result:getElapsedRealtimeMillis()
    local fake = luajava.new(classResult, detected, time, elapsed)
    param:setResult(fake)
    return true
end
