---
title: Cana - Settings
description: Configure recommendations, reviewed actions, and appearance.
---
# Settings

## Recommendations and custom sources

Cana shows cached or bundled app recommendations first. **Auto-update Bloat List**
refreshes the recommendations in the background when enabled. Community risk
labels are guidance; they do not guarantee that removal is safe on every device.

To change a custom list URL, edit it and press **Save**. The address must use HTTPS
or HTTP and contain a host. Cancel leaves the stored source and cache settings
unchanged. Downloads contact the selected source; see the [privacy policy](https://github.com/jordanwoodson/cana/blob/roadmap/privacy_policy.md).

## Reviewed actions

Every package action opens a review of the exact apps, profile, exclusions,
shared effects, and recovery limits. Filtering does not clear selections, so
check the total and hidden counts before proceeding. Review is always required.

**Allow removal of Unsafe apps** permits reviewed removal of apps classified
Unsafe, including actions in presets. It does not override Cana's protected-app
checks, Android administrator restrictions, or required safety acknowledgments.
Disabling an app is a separate action and can still affect device functions.

If device authentication is enabled, Cana requests authentication before applying
a reviewed change. Hiding successful result dialogs does not hide failed or
unverified outcomes; History retains the results.

## Selection and appearance

**Select visible** is available directly in the app list. It selects only apps in
the current view; the selection bar also counts selected apps hidden by filters.
Use **Clear selection** to remove all selected targets. No version-tapping shortcut
is needed.

Theme and dynamic colors follow the appearance options in Settings. Android's
font-size setting applies to labels and controls.
