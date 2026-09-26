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
