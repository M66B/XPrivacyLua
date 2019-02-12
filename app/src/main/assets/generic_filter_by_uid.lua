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
    local list = param:getResult()
    if list == nil then
        return false
    end

    local filtered = false
    local name = hook:getName()
    local cuid = param:getUid()

    local index
    local info = list:toArray()
    for index = info['length'], 1, -1 do
        local item = info[index]

        local uid
        if item == nil then
            uid = -1
        elseif name == 'PackageManager.getInstalledPackages' or
                name == 'PackageManager.getPackagesHoldingPermissions' or
                name == 'PackageManager.getPreferredPackages' then
            uid = item.applicationInfo.uid -- PackageInfo
        elseif name == 'PackageManager.queryIntentActivities' or
                name == 'PackageManager.queryIntentActivityOptions' then
            uid = item.activityInfo.applicationInfo.uid -- ResolveInfo
        elseif name == 'PackageManager.queryIntentContentProviders' then
            uid = item.providerInfo.applicationInfo.uid -- ResolveInfo
        elseif name == 'PackageManager.queryIntentServices' then
            uid = item.serviceInfo.applicationInfo.uid -- ResolveInfo
        else
            uid = item.uid
        end

        if uid ~= cuid then
            filtered = true
            list:remove(index - 1)
        end
    end

    return filtered
end
