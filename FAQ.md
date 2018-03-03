XPrivacyLua
===========

Frequently Asked Questions
--------------------------

<a name="FAQ1"></a>
**(1) How can I clear all data?**

Primary users can clear all data of all users by uninstalling XPrivacyLua *while it is running*.

Secondary users can clear their own data by uninstalling XPrivacyLua *while it is running*.

All data is stored in the folder */data/system/xlua*.

<a name="FAQ2"></a>
**(2) Can I run XPrivacy and XPrivacyLua side by side?**

Since XPrivacyLua is [in many aspects better](#FAQ7) than XPrivacy, running XPrivacy and XPrivacyLua side by side isn't supported.

<a name="FAQ3"></a>
**(3) How can I fix 'Module not running or updated'?**

This message means either that:

* The Xposed framework is not running: check if Xposed is enabled and running in the Xposed installer app.
* The XPrivacyLua module is not running: check if XPrivacyLua is enabled in the Xposed installer app and make sure you restarted your device (hard reboot) after installing/updating XPrivacyLua.
* There is a problem with the XPrivacyLua service: check the Xposed log in the Xposed installer app for XPrivacyLua problems.

Rebooting too soon after updating an Xposed module (before the Xposed installer shows the update notification) is known to cause problems.
Disable and enable the module in the Xposed installer and hard reboot again to fix this problem.

<a name="FAQ4"></a>
**(4) Can you add ...?**

* *Network and storage restrictions*: access to the internet and to the device storage can only be prevented by revoking Linux permission from an app, which will often result in the app crashing. Therefore this will not be added.
* *User interface features* like *templates*: I want to limit the time I put into this project and I want to keep things simple, so don't expect anything more than basic restriction management.
* *On demand restricting*: It is not really possible to add on demand restricting so that it works stable and can be supported on the long term, so this will not be added. See also [here](https://forum.xda-developers.com/showpost.php?p=75419161&postcount=49).
* *Randomizing fake values*: this is known to let apps crash, so this will not be added.
* *App specific*: anything specific for an app will not be added.
* *Security specific*: features related to security only will not be added.
* *User choice*: if you can already control the data, like selecting an account, no restriction is needed.
* *Crowd sourced restrictions*: there are not enough users for this to be useful.
* *An app settings button*: see [here](https://forum.xda-developers.com/showpost.php?p=75745469&postcount=2071) why this won't be added.

If you want to confine apps to their own folder, you can download the hook definition *BlockGuardOs.open*
from the [hook definition repository](https://lua.xprivacy.eu/repo/) using the pro companion app.

Apps having access to the IP address generally have access to the internet and therefore can get your IP address in a simple way,
see for example [here](https://www.privateinternetaccess.com/pages/whats-my-ip/). Therefore an IP address restriction doesn't make sense.

Revoking internet permission will result in apps crashing
and faking offline state doesn't prevent apps from accessing the internet.
Therefore internet restriction cannot properly be implemented.
You are adviced to use a firewall app to control internet access, for example [NetGuard](https://forum.xda-developers.com/android/apps-games/app-netguard-root-firewall-t3233012).

If you still want to fake offline state, you can download the hook definition *NetworkInfo.createFromParcel*
from the [hook definition repository](https://lua.xprivacy.eu/repo/) using the pro companion app.

MAC addresses are [not available anymore](https://developer.android.com/training/articles/user-data-ids.html#version_specific_details_identifiers_in_m) on supported Android versions.
Some manufacturers made an exception for this and to fix this you can download the hook definitions *BluetoothAdapter.getAddress*, *WifiInfo.getMacAddress* and *NetworkInterface.getHardwareAddress*
from the [hook definition repository](https://lua.xprivacy.eu/repo/) using the pro companion app.

Since XPrivacyLua version 1.22 it is possible to enable status bar notifications on applying restrictions using the pro companion app.
This can be used as a replacement for on demand restricting by removing a restriction when needed.

You can ask for new restrictions, but you'll need to explain how it would improve your privacy as well.

See also [question 7](#FAQ7).

<a name="FAQ5"></a>
**(5) How can I fix 'There is a Problem Parsing the Package'?**

This error could mean that the downloaded file is corrupt, which could for example be caused by a connection problem or by a virus scanner.

It could also mean that you are trying to install XPrivacyLua on an unsupported Android version.
See [here](https://github.com/M66B/XPrivacyLua/blob/master/README.md#compatibility) for which Android versions are supported.

<a name="FAQ6"></a>
**(6) Why is a check box shown grey?**

An app level check box is shown grey when one of the restriction level check boxes is not ticked.

A restriction level check box is shown grey
if one of the hooks that [compose the restriction](https://github.com/M66B/XPrivacyLua/blob/master/app/src/main/assets/hooks.json) is not enabled
(hooks are not shown in the app to keep things simple).
This can happen when a new version adds new hooks. These new hooks are not enabled by default to make sure your apps keeps working.
You can enable the new hooks by toggling the check box once (turning it off and on once).

<a name="FAQ7"></a>
**(7) How does XPrivacyLua compare to XPrivacy?**

* XPrivacy supports Android 4.0.3 KitKat to Android 5.1.1 Lollipop and XPrivacyLua supports Android version 6.0 Marshmallow and later, see [here](https://github.com/M66B/XPrivacyLua/blob/master/README.md#compatibility) about compatibility
* The user interface of XPrivacyLua is simpler than of XPrivacy, see also [question 4](#FAQ4)
* The restrictions of XPrivacyLua are designed to prevent apps from crashing, while a number of XPrivacy restrictions can apps cause to crash, see also [question 4](#FAQ4)
* XPrivacyLua has no on demand restricting for stability and maintenance reasons, see also [question 4](#FAQ4)
* XPrivacyLua can unlike XPrivacy restrict analytics services like [Google Analytics](https://www.google.com/analytics/) and [Fabric/Crashlytics](https://get.fabric.io/)
* XPrivacyLua [is user extensible](https://github.com/M66B/XPrivacyLua/blob/master/DEFINE.md) while XPrivacy is not
* XPrivacyLua is easier to maintain than XPrivacy, so XPrivacyLua is easier to update for new Android versions

In general XPrivacyLua and XPrivacy are comparable in protecting your privacy.
For a detailed comparison with XPrivacy see [here](https://github.com/M66B/XPrivacyLua/blob/master/XPRIVACY.md).

<a name="FAQ8"></a>
**(8) How can I define custom restrictions?**

Yes, see [here](https://github.com/M66B/XPrivacyLua/blob/master/DEFINE.md) for the documentation on defining hooks.

<a name="FAQ9"></a>
**(9) Why can an app still access my accounts?**

If you see an app accessing the list of accounts while the accounts restriction is being applied,
it is likely the Android account selector dialog you are seeing.
The app will see only the account you actually select.

<a name="FAQ10"></a>
**(10) Can applying a restriction let an app crash?**

XPrivacyLua is designed to let apps not crash.
However, sometimes an app will crash because of a restriction because there is a bug in the app.
For example XPrivacyLua can return no data to an app while the app is not expecting this but should be prepared to handle this because the Android API documentation says this might happen.

If you suspect that a restriction is causing a crash because there is a bug in the restriction, please provide a logcat and I will check the restriction.

<a name="FAQ11"></a>
**(11) How can I filter on ...?**

You can filter on restricted apps, on not restricted apps, on system apps and on user apps by typing special characters into the search field:

* Filter on restricted apps: exclamation mark (!)
* Filter on not restricted apps: question mark (?)
* Filter on system apps: hash character (#)
* Filter on user apps: at character (@)

The special search characters should be the first characters in the search field (it is possible to combine special characters)
and can be followed by additional characters to refine the search result.

<a name="FAQ12"></a>
**(12) Can I get a discount / use an XPrivacy pro license to get the XPrivacyLua pro features?**

XPrivacyLua was written from the ground up to support recent Android versions, which was really a lot of work.
Since I believe everybody should be able to protect his/her privacy, XPrivacyLua is free to use.
However, the XPrivacyLua pro features, mostly convencience and advanced features, not really needed to protect your privacy,
need to be purchased and it is not possible to 'pay' with an XPrivacy pro license and there will be no discounts either.

<a name="FAQ13"></a>
**(13) Will XPrivacyLua slow down my apps?**

Depending on the number of applied restrictions, you might notice a slight delay when starting apps,
but you will generally not notice delays when using apps.

<a name="FAQ14"></a>
**(14) Will XPrivacyLua keep running after updating?**

Yes, if XPrivacyLua was running before the update, it will keep running after the update,
even though the user interface and new features will not be available until after a reboot.

<a name="FAQ15"></a>
**(15) Can I get a refund?**

If a purchased pro feature doesn't work properly
and this isn't caused by a problem in the free features
and I cannot fix the problem in a timely manner, you can get a refund.
In all other cases there is no refund possible.
In no circumstances there is a refund possible for any problem related to the free features, which include all restrictions,
since there wasn't paid anything for them and because they can be evaluated without any limitation.
I take my responsibility as seller to deliver what has been promised
and I expect that you take responsibility for informing yourself of what you are buying.

<br>

If you have another question, you can use [this forum](https://forum.xda-developers.com/xposed/modules/xprivacylua6-0-android-privacy-manager-t3730663).
