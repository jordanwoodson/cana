<div align="center">

<img src="assets/branding/cana-icon.svg" alt="Cana Open C icon" width="128" />

# (can)a

**Do more. Because you can.**

More control over your Android apps. Debloat without root across **personal, work,
and other user profiles**.\*
Powered by [Shizuku](https://shizuku.rikka.app/).

---

</div>

> [!Warning]
> **DISCLAIMER:** ⛔ Use at your own risk. Nobody is responsible for any data loss or damage caused by this app ⛔.

## 📖 About

Cana is a fork of [Canta](https://github.com/samolego/Canta) by [samolego](https://github.com/samolego).
The name turns **(cant)a** into **(can)a**: a fork built to do more. Cana lets you
**pick which user profile to work on**, so system apps inside a work profile can
be debloated too. It uses Shizuku and recommendations from the
[Universal Debloat List](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/).

- ✅ No root required
- ✅ Personal, work, clone and private space profiles, and secondary users
- ✅ Detects previously uninstalled apps, per profile
- ✅ Works on Android **9.0+ (SDK 28+)**
- ⚠️ No *permanent* bricking (If you uninstall **critical apps**, you can experience bootloop and have to **factory reset**!)

\* Some apps (e.g. some oplus apps) don't allow uninstallation, and a work profile's admin can
block it entirely, see [below](#limits-set-by-the-work-profile-admin).

### What's different from Canta

Canta only sees and uninstalls apps in the profile it's installed in, and with Shizuku running
over adb it always uninstalls for user 0. Cana adds a profile switcher (the person / briefcase
icon in the top bar), which lists every user on the device through Shizuku. Picking one loads
that profile's apps, and uninstall / reinstall then act on that profile only
(`pm uninstall --user <id>` / `pm install-existing --user <id>` semantics).

* Uninstall / reinstall wait for the real result from Android. Failures (e.g. blocked by the
  work profile's admin) are reported in a toast with the reason in *Logs*, instead of the app
  being marked as uninstalled.
* On Cana's own profile, uninstalling a *non-system* app still removes it for all users, like
  Canta does. On any other profile it only removes it from that profile.
* Application id is `io.github.jordanwoodson.cana`, so Cana installs next to Canta.

## Install

* [Obtainium](https://github.com/ImranR98/Obtainium): add `https://github.com/jordanwoodson/cana`
* or grab the APK from [Releases](https://github.com/jordanwoodson/cana/releases/latest)

### Verification

You can verify the authenticity of downloaded APKs using this SHA-256 certificate fingerprint:
```
74:8E:75:1A:9D:D9:24:0F:9E:8C:C7:CD:7F:72:18:DD:15:41:E6:AB:AA:09:60:3D:28:22:84:06:26:39:E3:45
```

## How-to

Cana needs [Shizuku](https://shizuku.rikka.app/) running. Shizuku gets its shell (adb)
privileges from an adb connection, either from the phone itself
([wireless debugging](https://shizuku.rikka.app/guide/setup/)) or from a computer. For example
with adb over Tailscale, with Tailscale on both the phone and your computer:

1. Enable *Developer options → USB debugging* on the phone and connect once over USB, or pair
   with *Wireless debugging*.
2. Make adbd listen on TCP (resets on reboot): `adb tcpip 5555`
3. Connect over Tailscale: `adb connect <phone-tailscale-ip>:5555`
4. Start Shizuku. The exact command is shown in the Shizuku app under *Start via connected
   computer*, usually:
   `adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh`
5. Open Cana, tap the profile icon, grant the Shizuku permission and pick *Work profile*.
6. Select apps and tap the trash button.

The adb equivalents, handy for checking what Cana did:

```sh
adb shell pm list users                                     # find the work profile id (usually 10)
adb shell pm list packages --user 10 -s                     # system apps in the work profile
adb shell pm uninstall --user 10 <package>                  # what Cana does on uninstall
adb shell cmd package install-existing --user 10 <package>  # what Cana does on reinstall
```

### Limits set by the work profile admin

These are enforced by Android itself, and no app can bypass them without root. The profile
switcher warns about both:

* **DISALLOW_DEBUGGING_FEATURES**: Android refuses every shell / adb request that modifies the
  profile (`Shell does not have permission to access user 10`). Shizuku over adb can't help
  here. Shizuku started with root isn't affected.
* **DISALLOW_UNINSTALL_APPS**: uninstalling in that profile fails with
  `DELETE_FAILED_USER_RESTRICTED`.

## Building

```sh
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease  # needs key.properties (storeFile, storePassword, keyAlias, keyPassword)
```

The Open C artwork and export instructions live in [assets/branding](assets/branding/README.md).

## Thanks

* [samolego](https://github.com/samolego) and the contributors of
  [Canta](https://github.com/samolego/Canta), which Cana is built on. If you like Cana,
  consider [supporting Canta](https://www.paypal.com/donate/?hosted_button_id=FD4R46ZZ5EWME).
* [Universal-Debloater-Alliance](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/)
  for the debloat list
* @RikkaApps for Shizuku

## License

[LGPL-3.0](LICENSE), same as Canta.
