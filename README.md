XPrivacyLua
===========


Really simple to use privacy manager for Android 6.0 Marshmallow and later (successor of [XPrivacy](https://forum.xda-developers.com/xposed/modules/xprivacy-ultimate-android-privacy-app-t2320783"]XPrivacy[/URL])).

Revoking Android permissions from apps often let apps crash or malfunction.
XPrivacyLua solves this by feeding apps fake data instead of real data.

Features
--------

* Simple to use
* Manage any user or system app
* Multi-user support
* Free and open source

Restrictions
------------

* Get applications (hide installed apps)
* Get calendars (hide calendars)
* Get call log (hide call log)
* Get contacts (hide contacts, including blocked numbers)
* Get location (fake location, hide [NMEA](https://en.wikipedia.org/wiki/NMEA_0183) messages)
* Get messages (hide MMS, SMS, SIM, voicemail)
* Get sensors (hide all sensors)
* Read account name (fake name, mostly e-mail address)
* Read clipboard (fake paste)
* Read identifiers (fake build serial number, Android ID, GSF ID, advertising ID)
* Read network data (hide cell info, Wi-Fi networks / scan results / network name)
* Read telephony data (hide IMEI, MEI, SIM serial number, voicemail number, etc)
* Record audio (prevent recording)
* Record video (prevent recording)
* Use camera (fake camera not available)

You can see [here](https://github.com/M66B/XPrivacyLua/blob/master/app/src/main/assets/hooks.json) all technical details.

Compatibility
-------------

XPrivacyLua is supported on Android 6.0 Marshmallow and later.
For Android 4.0.3 KitKat to Android 5.1.1 Lollipop you can use [XPrivacy](https://github.com/M66B/XPrivacy/blob/master/README.md).

Installation
------------

* Download, install and activate the [Xposed framework](http://forum.xda-developers.com/xposed)
* Download, install and activate the [XPrivacyLua module](http://repo.xposed.info/module/eu.faircode.xlua)

Frequently Asked Questions
--------------------------

See [here](https://github.com/M66B/XPrivacyLua/blob/master/FAQ.md) for a list of often asked questions.

Support
-------

* For support on Xposed, please go [here](http://forum.xda-developers.com/xposed)
* For support on XPrivacyLua, please go [here](https://forum.xda-developers.com/xposed/modules/xprivacylua6-0-android-privacy-manager-t3730663)

Donations
---------

See [here](https://lua.xprivacy.eu/) about how you can donate.

Contributing
------------

*Source code*

Building XPrivacyLua from source code is straightforward with [Android Studio](http://developer.android.com/sdk/).
It is expected that you can solve build problems yourself, so there is no support on building.

Source code contributions are prefered in the form of [pull requests](https://help.github.com/articles/creating-a-pull-request/).
Please [contact me](https://contact.faircode.eu/) first to tell me what your plans are.

*Translations*

* You can translate the in-app texts [here](https://crowdin.com/project/xprivacylua/)
* If your language is not listed, please send a message through [this contact form](https://contact.faircode.eu/)

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
