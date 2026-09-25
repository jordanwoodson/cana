---
title: Cana - Features
description: Features of Cana.
---
# Features

## Core Features

### More control across profiles

Choose a personal, work, clone, private space, or secondary user profile from the
top bar. Cana loads that profile's apps and reports Android's actual result when
uninstalling or restoring a package.

Android and profile administrator restrictions still apply. Removing critical
system apps can cause a bootloop and require a factory reset. See
[profile restrictions](/usage#profile-restrictions) before making changes.

### System Requirements
* Android 9.0 (SDK 28) or higher
* No root required
* Elevates permissions using [Shizuku](https://shizuku.rikka.app/)

## Cool Features

### App Management
* Filter apps by type (system apps, user apps) or badge
* Search functionality
* Batch selection for multiple uninstalls
* See app info and size details

### Presets
* Create reusable app removal configurations
* Import and export presets as JSON
* Share presets across devices and with others

### App Badges
* 🟢 **Safe to Remove** - Non-essential apps. Still review them, though.
* 🟡 **Advanced** - May affect some functionality.
* 🔴 **Expert** - Can break important functionality.
* 🟣 **Unsafe** - Apps that can break vital system components.

### User Interface
App uses a modern Material Design 3 interface that adapts to your device's theme preferences.
The interface provides detailed information about each app while maintaining simplicity in its controls.
Finding specific apps is effortless thanks to the quick search functionality and comprehensive filtering options.

<div class="screenshot-container">
  <img src="/images/phoneScreenshots/screenshot-main.png" alt="Main screen" class="phone-screenshot">
  <div class="screenshot-caption">
      Cana Home Screen
  </div>
</div>

## Advanced Features

### Presets System
Cana's preset system allows you to create, manage, and share collections of apps for removal. This feature enables:

* **Device Consistency**: Apply the same bloatware removal across multiple devices
* **Community Sharing**: Share your carefully curated removal lists with others
* **Quick Setup**: Rapidly configure new devices with proven app configurations
* **Backup & Restore**: Save your uninstall preferences before major changes

Presets are stored locally and can be exported as JSON for easy sharing. When importing presets, Cana automatically validates app availability on your device and filters out incompatible entries.

[Learn more about Presets →](/presets)

## Privacy & Security

### Privacy-Focused
Cana can operate without internet connection and collects no data whatsoever.
There's no analytics or tracking built into the app.

::: warning NOTE
Badges information and app descriptions are fetched from GitHub repository if connected to the internet.
No data is uploaded whatsoever.
:::

### Open Source
Cana is licensed under LGPL-3.0, with its complete source code available on [GitHub](https://github.com/jordanwoodson/cana). This transparency ensures you can verify the app's functionality and contribute to its development if you wish.

Cana is a fork of [Canta](https://github.com/samolego/Canta) by samolego.
