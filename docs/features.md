---
title: Cana - Features
description: App, privacy, and profile management with reviewed changes and recovery history.
---
# Features

Cana is a native Android 9+ app manager powered by Shizuku. It supports personal, work, clone, private-space, and secondary-user profiles when Android permits access. Profile administrator restrictions still apply.

## Find and inspect apps

Installed and Removed tabs identify the current view. The active profile stays visible while searching. Filters are grouped by app state, removal risk, and category, with removable active-filter chips. Named views save filters and optional package collections; a view can be limited to one profile.

Selected apps stay selected when you change the view. The selection bar reports both the total and how many are hidden, and lets you review or clear the whole selection.

Each app has an Overview, Privacy, and Components screen. Android Settings opens for the selected profile when supported. The profile comparison screen shows installed, removed, absent, disabled, suspended, and unavailable states. Saved privacy intent is labeled separately from verified enforcement.

## Review and apply changes

Cana supports uninstall, reinstall, disable/enable, suspend/unsuspend, retained-data removal, and supported update cleanup. A preflight review lists the exact packages and profile, exclusions, warnings, and recovery limits. Removing updates can affect the shared app version across profiles.

Batches keep their progress outside the initiating screen. Stop lets the current operation finish and leaves later items unstarted. If Cana's process ends, unfinished items are shown in History for review and are not automatically replayed.

History groups operations by batch and retains recovery information. Uncertain outcomes remain eligible for investigation; they are not presented as verified success. Reinstalling an app cannot recreate deleted data or an old APK payload.

## Privacy controls

Depending on Android support, Cana can revoke selected runtime permissions, restrict background activity, deny metered background data, or apply a supported network restriction. Shared UIDs and critical roles require review. The privacy dashboard distinguishes saved intent, verified state, unavailable access, failures, and rules needing fresh consent.

Saved rules are tied to the installation and package identity. Changed identities require review; forgetting a stale rule removes Cana's saved intent without modifying a different app's reused UID. Shizuku availability and Android scheduling affect when rules can be checked.

## Presets

Presets can include package removals and privacy settings. Import review shows new or updated preset identity, action differences, profile mismatches, and missing packages before saving. Cancel leaves the saved preset unchanged. Apply review checks the selected profiles before any actions run. Missing entries remain visible rather than being silently removed from the preset.

## Recommendations and accessibility

Removal recommendations are guidance, not guarantees. Readable badges identify Recommended, Advanced, Expert, Unsafe, and System apps. The UI uses Material 3, localized resources, visible labels, and Android font scaling.

Recommendation and tracker data can be cached for offline use. Downloads contact the configured sources and expose normal network request metadata. Cana has no analytics SDK. See the [privacy policy](https://github.com/jordanwoodson/cana/blob/master/privacy_policy.md) for local storage, backup, download, and export details.

Cana is an LGPL-3.0 fork of [Canta](https://github.com/samolego/Canta).
