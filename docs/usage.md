---
title: Cana - Usage
description: Review and manage apps in a selected Android profile.
---
# Usage

1. Start Shizuku and grant Cana access when prompted.
2. Choose a profile from the top bar. Its name and Android user ID stay visible,
   including while searching.
3. Open **Installed** or **Removed**, then search or filter. Tap an app for its
   Overview, Privacy, and Components pages. Android App info targets that profile.
4. Select apps individually or use **Select visible**. The selection bar shows
   all selected targets and how many are hidden. **Review selection** lists them;
   **Clear selection** removes them all.
5. Choose **Review actions**, select an action, and inspect the plan. Check the
   exact apps, profile, exclusions, data-loss limits, and effects on other profiles.
   Confirm with **Apply reviewed changes** and authenticate if requested.

The Shizuku setup dialog updates when you return from Shizuku. Authorization waits
for one permission prompt at a time. If permission is denied, the dialog stays
open so you can retry; if Shizuku blocks further prompts, open Shizuku and allow
Cana under **Authorized applications**. USB debugging is optional recovery
preparation, not a prerequisite for authorization.

App rows and Overview show **APK files**, including the active base APK and its
splits. Updated system apps also show **System update**, which is already included
in the APK total. These files are shared across profiles. Sizes exclude app data,
cache, extracted libraries and the factory image; they do not predict space freed
by removing an app from one profile. An unreadable APK size appears as
**Unavailable** instead of a partial total.

The batch banner remains visible across pages. **Stop** finishes the current item
and leaves subsequent items unstarted. After process interruption, History marks
unfinished work for review; Cana does not automatically execute it again.

An Android installer timeout means the result is still unknown. Cana retains the
pending record, waits for verification, and prevents conflicting package actions.
Review History before retrying. A pending result is never reported as success.

## Recovery

History groups changes by batch and exposes **Undo last batch**, individual
results, and expandable before/after diagnostics. Undo reviews the recorded
profile and any required current safety consent. Recovery cannot recreate deleted
app data or an APK that Android has completely removed.

**Export restore script** includes archived recovery records. Review the exported
commands before running them from a computer. Operational history and saved
restrictions are excluded from Android backup; presets, settings, and saved views
remain portable. Old or restored operational data requires review.

## Filters, collections, and comparison

The filter sheet separates sort order, app state, risk, and category. Save a named
view, optionally for one profile, and optionally include a collection of package
names. Profile comparison provides a read-only matrix; unavailable data stays
explicitly unavailable.

## Privacy

The Privacy dashboard shows saved intent separately from verified enforcement.
Open an app to revoke selected mutable permissions, restrict background activity,
or review metered/network restrictions. Shared-UID effects require review.

After reboot, enforcement can depend on Shizuku returning and reconciliation
running. Failed verification and changed safety conditions remain visible. Use
**Forget saved restriction** for deleted or changed-UID apps to stop Cana trying
to restore the old intent; this does not alter a live UID rule.

## Profile restrictions

Android administrator policies can prohibit debugging or app removal. Cana shows
these restrictions in the profile picker and preserves Android's failure details
in diagnostics. Package removal targets the selected profile; removing a system
app update changes a shared APK and can affect other profiles.

## Tracker scans

Open an app’s Overview to scan its APK classes, or use **Scan for trackers** in
Filters to scan the selected profile. Results are shared across profiles because
the APK code is shared. Cached results require the same version, APK paths,
lengths, modification times and signature set; refresh the app list after an app
changes. The metadata identity is a freshness check, not a cryptographic integrity
check of APK contents.

A missing split, malformed DEX or scan limit produces a failed scan, not a clean
result. Scans process one DEX at a time, up to 64 MiB per DEX and 256 MiB of DEX
content per APK. Cancel interrupts parsing, and batch completion reports failures
separately. Existing cached scans are discarded once after this scanner update.

The scan does not inspect native libraries, downloaded code or live network
traffic. A match indicates embedded code associated with a signature; it does
not prove data collection. No matches do not prove an app is free of tracking.
Custom source failures display the bundled fallback explicitly. The
[tracker-source comparison](superpowers/plans/2026-09-30-tracker-sources.md)
explains the broader sources investigated and why tentative entries were not
merged into the default counts.
