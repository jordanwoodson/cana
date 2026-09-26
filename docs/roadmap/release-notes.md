Cana now includes the complete debloat, privacy, management and System roadmap.

- Per-profile uninstall fixes and verified system-update cleanup with cross-profile warnings.
- Essential-app protection, Disable/Suspend/keep-data removal, operation history, Undo last batch and PC restore export. Recovery preserves retained data and keeps interrupted operations recoverable.
- Per-profile permission, background, metered-data and Android 14+ network controls with saved intent and revert.
- The authentication preference applies to presets, OTA removals and undo as well as ordinary package actions.
- Offline recommendations, tracker component inspection, richer presets, OTA change review and size/usage sorting.
- Device-wide Private DNS, captive checks, scanning, mobile-data and Data Saver controls.

Android 15 shell-backed Shizuku cannot modify ordinary apps' components. Usage statistics are limited to Cana's own profile where supported. Network and metered restrictions are reapplied after reboot when Shizuku returns; startup is not continuously blocked. Undo cannot recover deleted application data or missing APKs.

Install over the existing Cana release, or let Obtainium update from this repository. Package id and release signing certificate are unchanged. Version code 227.

Verified with 81 JVM tests, debug/release lint, signed builds and Android 15 personal/work-profile acceptance.

Known minor: pull to refresh the main app list after History undo or OTA reapply.

[Roadmap report](https://github.com/jordanwoodson/cana/blob/3.2.2-cana.3/docs/roadmap/release-report.md): per-phase changes, upstream candidates, framework findings and verification.
