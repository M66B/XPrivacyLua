Defining restrictions
=====================

*Defining restrictions require the XPrivacyLua pro companion app with the definitions option activated.*

Restriction or hook definitions describe where to hook and what to do when the hook executes.

The *where to hook* is described as:

* Which [class](https://developer.android.com/reference/java/lang/Class.html)
* Which [method](https://developer.android.com/reference/java/lang/reflect/Method.html)

See [here](https://github.com/rovo89/XposedBridge/wiki/Development-tutorial#exploring-your-target-and-finding-a-way-to-modify-it) about finding out where to hook.

The *what to do when the hook executes* is described in the form of a [Lua](https://www.lua.org/pil/contents.html) script.

Unlike normal Xposed hooks, defined hooks can be added and updated at run time, with the big advantage that there is no reboot required to test a new or changed hook
(with the exception of persistent system apps).

Hook definitions are [JSON](https://en.wikipedia.org/wiki/JSON) formatted. A simple example:

```
{
  "collection": "Privacy",
  "group": "Read.Device",
  "name": "Build.FINGERPRINT",
  "author": "M66B",

  "className": "android.os.Build",
  "methodName": "#FINGERPRINT",
  "parameterTypes": [],
  "returnType": "java.lang.String",

  "minSdk": 1,
  "maxSdk": 999,
  "enabled": true,
  "optional": false,
  "usage": true,
  "notify": false,

  "luaScript": "function after(hook, param)\n  param:setResult(\"unknown\")\n  return true\nend\n"
}
```

* The *collection*, *group* and *name* attributes are use to identify a hook
* The attributes *minSdk* and *maxSdk* determine for which [Android versions](https://source.android.com/setup/build-numbers) (API level) the hook should be used
* Setting *enabled* to *false* will switch the hook off (default *true*)
* Setting *optional* to *true* will suppress error messages about the class or method not being found (default *false*)
* Setting *usage* to *false* means that executing the hook will not be reported (default *true*)
* Setting *notify* to *true* will result in showing notifications when the hook is applied (default *false*)

The Lua script from the above definition without the JSON escapes looks like this:

```
function after(hook, param)
  param:setResult("unknown")
  return true
end
```

There should be a *before* and/or and *after* function, which will be executed before/after the original method is executed.
The function will always have exacty two parameters:

* *hook*: information about the hooked method, see [here](https://github.com/M66B/XPrivacyLua/blob/master/app/src/main/java/eu/faircode/xlua/XHook.java) for the available public methods
* *param*: information about the current parameters, see [here](https://github.com/M66B/XPrivacyLua/blob/master/app/src/main/java/eu/faircode/xlua/XParam.java) for the available public methods

These functions should return *true* when something was restricted and *false* otherwise.

An error in the definition, like class or method not found or a compile time or run time error in the Lua script will result in a status bar notification.

Using the companion you can edit built-in definitions, which will result in making a copy of the definition.
You could for example enable notifications or change the returned fake value.
Deleting copied definitions will restore the built-in definitions.

The companion app can export and import definitions.
