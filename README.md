XPrivacyLua
===========


Really simple to use privacy manager for Android 6.0 Marshmallow and later (successor of [XPrivacy](https://forum.xda-developers.com/xposed/modules/xprivacy-ultimate-android-privacy-app-t2320783"]XPrivacy[/URL])).

Revoking Android permissions from apps often let apps crash or malfunction.
XPrivacyLua solves this by feeding apps fake data instead of real data.

Features
--------

* Simple to use
* Manage any user or system app
* [Extensible](https://github.com/M66B/XPrivacyLua/blob/master/DEFINE.md)
* Multi-user support
* Free and open source

Restrictions
------------

* Determine activity (fake unknown activity, see [here](https://developers.google.com/location-context/activity-recognition/))
* Get applications (hide installed apps and [widgets](https://developer.android.com/reference/android/appwidget/AppWidgetManager.html))
* Get calendars (hide calendars)
* Get call log (hide call log)
* Get contacts (hide contacts with the pro option to allow (non) starred contacts, hide blocked numbers)
* Get location (fake location, hide [NMEA](https://en.wikipedia.org/wiki/NMEA_0183) messages)
* Get messages (hide MMS, SMS, SIM, voicemail)
* Get sensors (hide all available sensors)
* Read [account](https://developer.android.com/reference/android/accounts/Account.html) name (fake name, mostly e-mail address)
* Read clipboard (fake paste)
* Read identifiers (fake build serial number, Android ID, advertising ID, GSF ID)
* Read notifications (fake [status bar notifications](https://developer.android.com/reference/android/service/notification/StatusBarNotification.html))
* Read network data (hide cell info, Wi-Fi networks, fake Wi-Fi network name)
* Read sync data (hide [sync data](https://developer.android.com/training/sync-adapters/creating-sync-adapter.html))
* Read telephony data (fake IMEI, MEI, SIM serial number, voicemail number, etc)
* Record audio (prevent recording)
* Record video (prevent recording)
* Send messages (prevent sending MMS, SMS, data)
* Use analytics ([Fabric/Crashlytics](https://get.fabric.io/), [Facebook app events](https://developers.facebook.com/docs/reference/androidsdk/current/facebook/com/facebook/appevents/appeventslogger.html/), [Firebase Analytics](https://firebase.google.com/docs/analytics/), [Google Analytic](https://www.google.com/analytics/), [Mixpanel](https://mixpanel.com/), [Segment](https://segment.com/))
* Use camera (fake camera not available and/or hide cameras)
* Use tracking (fake user agent for [WebView](https://developer.android.com/reference/android/webkit/WebView.html) only, [Build properties](https://developer.android.com/reference/android/os/Build.html), network/SIM country/operator)

The tracking restrictions will work only if the code of the target app was not [obfuscated](https://developer.android.com/studio/build/shrink-code.html).
The other restrictions will work always.

Hide or fake?

* Hide: return empty list
* Fake: return empty or fake value

It is possible to add custom restriction definitions, see [this FAQ](https://github.com/M66B/XPrivacyLua/blob/master/FAQ.md#user-content-faq8) for details.

You can see all technical details [here](https://github.com/M66B/XPrivacyLua/blob/master/app/src/main/assets/hooks.json).

Notes
-----

* Some apps will start the camera app to take pictures. This cannot be restricted and there is no need for this, because only you can take pictures in this scenario, not the app.
* Some apps will use [OpenSL ES for Android](https://developer.android.com/ndk/guides/audio/opensl-for-android.html) to record audio, an example is WhatsApp. Xposed cannot hook into native code, so this cannot be prevented.
* The get applications restriction will not restrict getting information about individual apps for stability and performance reasons.
* The telephony data restriction will result in apps seeing a fake IMEI. However, this doesn't change the IMEI address of your device.
* Restricting activity recognition (location) results for recent Google Maps versions and possibly other apps in the error *... java.lang.ClassNotFoundException ...* for unknown reasons.

Compatibility
-------------

XPrivacyLua is supported on Android 6.0 Marshmallow and later.
For Android 4.0.3 KitKat to Android 5.1.1 Lollipop you can use [XPrivacy](https://github.com/M66B/XPrivacy/blob/master/README.md).

XPrivacyLua was tested with the original Xposed framework only.

Hooking *com.google.android.gms.location.ActivityRecognitionResult.extractResult* (restriction *Determine activity*)
is known to fail with *script:25 vm error: java.lang.ClassNotFoundException: com.google.android.gms.location.DetectedActivity*
and *script:28 attempt to call nil* for some apps, like Google Maps and NetFlix, for yet unknown reasons.

Installation
------------

* Download, install and activate the [Xposed framework](http://forum.xda-developers.com/xposed)
* Download, install and activate the [XPrivacyLua module](http://repo.xposed.info/module/eu.faircode.xlua)

Certificate fingerprints:

* MD5: 42:93:4F:A4:D5:AC:53:7B:04:97:3B:29:A6:6E:7B:B3
* SHA1: 10:62:0A:E9:61:D7:88:54:F6:C9:CD:87:2C:43:88:23:28:49:C7:99
* SHA256: 5E:69:9C:5D:AF:61:2C:AB:71:3A:35:BB:38:7C:F6:A8:86:8C:A0:DD:5D:CE:B4:CE:C1:53:8E:82:65:21:95:77

Frequently Asked Questions
--------------------------

See [here](https://github.com/M66B/XPrivacyLua/blob/master/FAQ.md) for a list of often asked questions.

Support
-------

Only the XPrivacyLua version released in the Xposed repository is supported.

XPrivacyLua is supported with the original Xposed framework only.

XPrivacyLua with [Island](http://forum.xda-developers.com/android/-t3366295) is not supported.

* For support on Xposed, please go [here](http://forum.xda-developers.com/xposed)
* For support on XPrivacyLua, please go [here](https://forum.xda-developers.com/xposed/modules/xprivacylua6-0-android-privacy-manager-t3730663)

Donations
---------

See [here](https://lua.xprivacy.eu/) about how you can donate.

Contributing
------------

*Documentation*

Contributions to this document and the frequently asked questions
are prefered in the form of [pull requests](https://help.github.com/articles/creating-a-pull-request/).

*Translations*

* You can translate the in-app texts of XPrivacyLua [here](https://crowdin.com/project/xprivacylua/)
* You can download the in-app texts of XPrivacyLua Pro for translation [here](https://lua.xprivacy.eu/strings_pro.xml)
* If your language is not listed, please send a message through [this contact form](https://contact.faircode.eu/)

*Source code*

Building XPrivacyLua from source code is straightforward with [Android Studio](http://developer.android.com/sdk/).
It is expected that you can solve build problems yourself, so there is no support on building.

Source code contributions are prefered in the form of [pull requests](https://help.github.com/articles/creating-a-pull-request/).
Please [contact me](https://contact.faircode.eu/) first to tell me what your plans are.

Please note that you agree to the license below by contributing, including the copyright.

Attribution
-----------

XPrivacyLua uses:

* [LuaJ](https://sourceforge.net/projects/luaj/). Copyright 2007-2013 LuaJ. All rights reserved. See [license](http://luaj.sourceforge.net/license.txt).
* [Glide](https://bumptech.github.io/glide/). Copyright 2014 Google, Inc. All rights reserved. See [license](https://raw.githubusercontent.com/bumptech/glide/master/LICENSE).
* [Android Support Library](https://developer.android.com/tools/support-library/). Copyright (C) 2011 The Android Open Source Project. See [license](https://android.googlesource.com/platform/frameworks/support/+/master/LICENSE.txt).

License
-------

[GNU General Public License version 3](https://www.gnu.org/licenses/gpl.txt)

Copyright (c) 2017-2018 Marcel Bokhorst. All rights reserved

XPrivacyLua is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

XPrivacyLua is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with XPrivacyLua. If not, see [https://www.gnu.org/licenses/](https://www.gnu.org/licenses/).

Trademarks
----------

*Android is a trademark of Google Inc. Google Play is a trademark of Google Inc*
