XPrivacyLua
===========

Frequently Asked Questions
--------------------------

<a name="FAQ1"></a>
**(1) How can I clear all data?**

You can clear all data by deleting the folder /data/system/xlua

Secondary users can clear their own data by uninstalling XPrivacyLua while it is running.

<a name="FAQ2"></a>
**(2) Can I run XPrivacy and XPrivacyLua side by side?**

Yes, you can, but be aware that both modules will apply the configured restrictions.

<a name="FAQ3"></a>
**(3) How can I fix 'Module not running or updated'?**

This message means either that:

* The Xposed framework is not running: check if Xposed is enabled and running in the Xposed installer app.
* The XPrivacyLua module is not running: check if XPrivacyLua is enabled in the Xposed installer app and make sure you restarted your device after installing/updating XPrivacyLua.
* There is a problem with the XPrivacyLua service: check the Xposed log in the Xposed installer app for XPrivacyLua problems.

<a name="FAQ4"></a>
**(4) Can you add ...?**

* *Network and storage restrictions*: access to the internet and to the device storage can only be prevented by revoking Linux permission from an app, which will often result in the app crashing. Therefore this will not be added.
* *Tracking/profiling restrictions*: there are hundreds of data items that can be used for tracking and profiling purposes. It is too much work to add restrictions for all of them.
* *User interface features*: I want to limit the time I put into this project and I want to keep things simple, so don't expect anything more than basic restriction management.
* *On demand restricting*: It is not really possible to add on demand restricting so that it works stable and can be supported on the long term, so this will not be added.

You can ask for new restrictions, but you'll need to explain how it would improve your privacy as well.

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

<br>

If you have another question, you can use [this forum](https://forum.xda-developers.com/xposed/modules/xprivacylua6-0-android-privacy-manager-t3730663).
