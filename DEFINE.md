Defining restrictions
=====================

*Defining restrictions require the XPrivacyLua pro companion app with the definitions option activated.*

Restriction or hook definitions describe where to hook and what to do when the hook executes.

The *where to hook* is described as:

* Which [class](https://developer.android.com/reference/java/lang/Class.html)
* Which [method](https://developer.android.com/reference/java/lang/reflect/Method.html)

In the well documented [Android API](https://developer.android.com/reference/packages.html) you can find class and method names.
For more advanced hooks, see [here](https://github.com/rovo89/XposedBridge/wiki/Development-tutorial#exploring-your-target-and-finding-a-way-to-modify-it).

The *what to do when the hook executes* is described in the form of a [Lua](https://www.lua.org/pil/contents.html) script.

Unlike normal Xposed hooks, defined hooks can be added and updated at run time, with the big advantage that there is no reboot required to test a new or changed hook
(with the exception of persistent system apps).

Hook definitions are [JSON](https://en.wikipedia.org/wiki/JSON) formatted. An example:

```JSON
{
  "builtin": true,
  "collection": "Privacy",
  "group": "Read.Telephony",
  "name": "TelephonyManager\/getDeviceId",
  "author": "M66B",
  "className": "android.telephony.TelephonyManager",
  "resolvedClassName": "android.telephony.TelephonyManager",
  "methodName": "getDeviceId",
  "parameterTypes": [],
  "returnType": "java.lang.String",
  "minSdk": 1,
  "maxSdk": 999,
  "enabled": true,
  "optional": false,
  "usage": true,
  "notify": false,
  "luaScript": "function after(hook, param)\n    local result = param:getResult()\n    if result == nil then\n        return false\n    end\n\n    param:setResult(null)\n    return true\nend\n"
}
```

* The *collection*, *group* and *name* attributes are use to identify a hook
* The attributes *minSdk* and *maxSdk* determine for which [Android versions](https://source.android.com/setup/build-numbers) (API level) the hook should be used
* Setting *enabled* to *false* will switch the hook off (default *true*)
* Setting *optional* to *true* will suppress error messages about the class or method not being found (default *false*)
* Setting *usage* to *false* means that executing the hook will not be reported (default *true*)
* Setting *notify* to *true* will result in showing notifications when the hook is applied (default *false*)

The Lua script from the above definition without the JSON escapes looks like this:

```Lua
function after(hook, param)
	local result = param:getResult()
	if result == nil then
		return false
	end

	param:setResult(null)
	return true
end
```

There should be a *before* and/or an *after* function, which will be executed before/after the original method is executed.
The function will always have exacty two parameters:

* *hook*: information about the hooked method, see [here](https://github.com/M66B/XPrivacyLua/blob/master/app/src/main/java/eu/faircode/xlua/XHook.java) for the available public methods
* *param*: information about the current parameters, see [here](https://github.com/M66B/XPrivacyLua/blob/master/app/src/main/java/eu/faircode/xlua/XParam.java) for the available public methods

The before/after function should return *true* when something was restricted and *false* otherwise.

A common problem when developing an Xposed module is getting [a context](https://developer.android.com/reference/android/content/Context.html).
With XPrivacyLua you'll never have to worry about this because you can simply call:

```Lua
param:getApplicationContext()
```

You can also modify field values in an *after* function by prefixing the method name with a # character, for example:

```JSON
  "methodName": "#SERIAL"
```

Another special case is hooking a method of a field using the syntax *[field name]:[method name]*, for example:

```JSON
  "methodName": "CREATOR:createFromParcel"
```

An error in the definition, like class or method not found, or a compile time or run time error of/in the Lua script will result in a status bar notification.
By tapping on the error notification you can navigate to the app restriction settings where you can tap on the corresponding !-icon to see the details of the error.

To apply an updated definition an app needs to be force closed and started again.
The simplest way to do this is to toggle the restriction off and on.

Using the companion app you can edit built-in definitions, which will result in making a copy of the definition.
You could for example enable usage notifications or change returned fake values.
Deleting copied definitions will restore the built-in definitions.

The companion app can also export and import definitions, making it easy to use definitions provided by others.

You can find some example definitions [here](https://github.com/M66B/XPrivacyLua/tree/master/examples)
and the built-in definition [here](https://github.com/M66B/XPrivacyLua/tree/master/app/src/main/assets).
