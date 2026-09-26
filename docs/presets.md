---
title: Cana - Presets
description: Review, save, and apply package and privacy configurations.
---
# Presets

A preset contains a name, description, stable identity, package removals, optional privacy controls, and an optional profile-kind hint. Saving or importing a preset does not apply its actions.

## Create and edit

Open **Presets** from the menu. Create a preset from the removed apps in the current profile, optionally including supported privacy settings. This capture uses the profile's full inventory, even when the main list is filtered. Review and edit the package list before sharing it.

## Import and export

Export copies JSON to the clipboard. Import accepts clipboard or pasted JSON and opens a review before saving. The review shows added/removed actions, privacy differences, profile mismatches, and package availability.

If an imported identity already exists, **Update** replaces that preset only after confirmation. If the saved preset changes while review is open, Cana requests another review. **Cancel** leaves the existing preset unchanged. Older JSON without an identity is imported as a new preset.

Missing packages remain in the saved preset so it can be used on another device. Their absence is visible in review; it is not treated as proof that a profile could be inspected when access failed.

## Apply

Choose **Apply preset** and review the target profile or profiles, exact actions, unavailable packages, and safety warnings. No actions run until you confirm and complete any configured device authentication.

Progress remains available outside the dialog. **Stop after current item** leaves subsequent items unstarted. History records per-item outcomes and recovery details. If the process closes, unfinished work is shown for review and is never automatically replayed.

Uninstalling can delete app data. Reinstall and recovery cannot recreate deleted data, removed APK versions, or unavailable APK files. Profile administrator restrictions and Android's permission rules still apply.
