---
title: Cana - Usage
description: Usage instructions for Cana.
---
# Usage

## Uninstalling Apps

### 1. Launch Cana
<div class="screenshot-container">
  <img src="/images/phoneScreenshots/screenshot-main.png" alt="Main screen" class="phone-screenshot">
  <div class="screenshot-caption">
    Home screen of Cana showing list of installed apps
  </div>
</div>

### 2. Choose a profile

Tap the profile icon in the top bar. Choose Personal, Work profile, or another
user on the device. The subtitle shows the selected profile and its Android user
ID. Grant Cana permission in Shizuku if prompted.

### 3. Select Apps to Uninstall
Search or filter the app list. Tap an app to read its description and community
recommendation, then select the apps you want to remove.

### 4. Click the Trash Button

::: info
You will need to grant Shizuku access for Cana upon first uninstallation.
:::

### 5. Confirm Uninstallation
Review the selection in the confirmation dialog before proceeding.

## Reinstalling Apps

Navigate to the uninstalled apps tab, select the apps and click the reinstall button.

## Profile restrictions

Cana respects Android's permissions and profile administrator policies:

- **Debugging disallowed:** Shizuku running through ADB cannot modify this profile.
- **Uninstalling disallowed:** Android rejects app removal in this profile.

Cana shows these restrictions in the profile picker. If an operation fails,
check **Logs** for Android's error message.

For the profile Cana runs in, removing a non-system app still removes it for all
users, as in Canta. For another selected profile, removal affects that profile
only. Restoring uses the package already present on the device.
