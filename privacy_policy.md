# Privacy Policy for Cana

Updated September 26, 2026.

Cana manages Android apps and profiles locally using Shizuku. Cana has no analytics SDK, advertising SDK, developer account system, or developer-operated collection endpoint.

## Information on your device

Cana reads package names, app versions, installation state, profile identifiers, and the permission or system settings needed for the controls you use. If you enable usage access, it can read app usage timestamps for sorting and filtering. It stores preferences, presets, saved views, desired privacy rules, and operation/recovery history in its private app storage. Diagnostic logs can contain package names, profile IDs, command results, and errors; the in-app log is bounded and kept in memory.

Android protects this storage using its app sandbox. Operational history, pending batches, privacy rules, and management snapshots are excluded from Android backup and device transfer. Portable preferences, presets, and saved views may be included if Android backup is enabled. Installation identity checks keep older restored operational records from being silently reapplied.

## Network requests

Recommendation descriptions and badges can be downloaded from the Universal Debloater Alliance dataset. Tracker definitions can be downloaded from the configured tracker source. Cana keeps cached/bundled data for offline use. Automatic recommendation updates and unmetered-only behavior are configurable; a manual refresh may also make a request.

These downloads contact GitHub or the source URL you configure. The destination and network providers receive normal request information, such as your IP address, requested URL, and cache validation headers. Cana does not attach your installed-app inventory, operation history, or usage timestamps to these downloads. A custom URL can itself contain information you entered. Links opened in an external browser follow that browser and destination's policies.

## Permissions and elevated access

- Package visibility lists apps that Cana can inspect.
- Shizuku grants elevated access for supported package, profile, permission, and system operations. Android and administrator restrictions still apply.
- Internet and network-state access support data downloads and update preferences.
- Optional usage access supports last-used sorting/filtering; optional secure-settings access supports the System controls.
- Boot/package events schedule checks for desired rules and system-update changes. Notifications are optional.
- Optional device authentication is handled by Android. Cana does not receive biometric templates or device credentials.

## Export and sharing

Presets, logs, and recovery scripts leave the app only when you copy, export, or share them. Those exports can reveal package names, profile identifiers, settings, or operation results. The destination you choose controls the exported copy. Cana cannot delete copies you share with other apps or people.

## Deletion and contact

You can delete presets and saved views in Cana. Uninstalling Cana or clearing its storage removes its local records and recovery information. Android settings already changed by Cana may remain in effect; review or restore them before deleting the records you need for recovery.

Questions or corrections can be raised in the [Cana repository](https://github.com/jordanwoodson/cana). If you post a diagnostic report there, it is shared with GitHub and anyone who can access the report.
