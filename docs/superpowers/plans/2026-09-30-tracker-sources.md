# Tracker source research — September 30, 2026

The most promising additional class-signature source found is App Manager.
Broader data should be compared as signatures and distinct named trackers, not as
raw row counts. Cana currently has 432 Exodus records, 428 with code signatures,
and 588 distinct literal alternatives (measured from the bundled JSON).

| Source | Evidence and fit for Cana |
| --- | --- |
| [App Manager class signatures](https://github.com/MuntashirAkon/AppManager/blob/2d9223a7a541ad40ec927e429d7a9ab58d27a90d/app/src/main/res/values/trackers.xml) | Downloaded XML has 989 paired signatures/names, 671 distinct raw names. 404 signature strings are absent by exact equality from Cana's Exodus alternatives; that is **not** 404 additional trackers or necessarily new coverage. Trailing dots, aliases and overlapping prefixes need normalization. Every signature inspected is a literal compatible with Cana's prefix matcher. The file declares GPL-3.0-or-later. The `²` marker identifies ETIP entries still under investigation, according to App Manager’s documentation and generator. |
| [Exodus ETIP](https://github.com/Exodus-Privacy/etip) | A tracker investigation platform with change history and collision checks. Database terms are ODbL 1.0 / DbCL 1.0. Its [documented administration API](https://github.com/Exodus-Privacy/etip/blob/master/doc/api.md) requires token authentication. It is a research input, not a verified anonymous replacement URL for Cana. |
| [TrackerControl](https://github.com/TrackerControl/tracker-control-android) | Distinguishes network detection (Disconnect, DuckDuckGo and its own domain data) from embedded library detection (ClassyShark3xodus / Exodus). Its domain coverage would require a network-analysis feature; adding domains to Cana's class-prefix matcher would not detect them. |
| [DuckDuckGo Tracker Radar](https://github.com/duckduckgo/tracker-radar) | A domain dataset with ownership and behavior metadata, not Android class signatures. The published dataset terms are CC BY-NC-SA 4.0. Useful for separate network analysis, not a direct static-scanner upgrade. |

## Provenance and coverage comparison

[App Manager’s scanner documentation](https://muntashir.dev/AppManager/en/#sec:scanner-page)
explains that its superscript-2 entries remain under investigation. The pinned
[generator](https://github.com/MuntashirAkon/AppManager/blob/2d9223a7a541ad40ec927e429d7a9ab58d27a90d/scripts/update_libraries.php)
adds that marker when an entry has an ETIP ID but no Exodus ID. Its
[submodule configuration](https://github.com/MuntashirAkon/AppManager/blob/2d9223a7a541ad40ec927e429d7a9ab58d27a90d/.gitmodules)
points to [Android Libraries](https://github.com/MuntashirAkon/android-libraries),
whose README describes an unstable schema and declares AGPL-3.0-or-later. The
generated XML declares GPL-3.0-or-later; that difference requires provenance and
attribution work before copying a derived dataset into Cana.

Measured against the pinned XML:

- 408 rows / 247 distinct raw names carry the ETIP marker.
- After accounting for prefixes already covered by the bundled Exodus matcher,
  399 rows / 243 raw names are not wholly covered.
- Of those, 398 rows / 242 names are ETIP-marked. The remaining signature is
  `com.google.firebase.analytics.` (Firebase Analytics).

These are literal prefix comparisons, not measured detection accuracy. For each
candidate prefix, the comparison checks whether an existing anchored prefix
covers it, or an existing infix signature occurs within it. It does not resolve
tracker aliases or prove that tentative signatures represent tracking behavior.

**Decision:** retain the bundled confirmed Exodus snapshot for this change. App
Manager/ETIP is the strongest candidate for an optional, clearly labeled
investigative corpus; importing it wholesale into confirmed tracker counts would
misrepresent its status. Network-domain lists remain a separate feature. No new
third-party dataset is bundled.

## Requirements for a future investigative corpus

- Deduplicate identities against Exodus, preserve tentative/confirmed status,
  and compare actual matches on representative APK fixtures.
- Preserve attribution and applicable data terms for any derived asset; keep its
  source revision and a reproducible converter or update script.
- Expose the selected source and its snapshot date; do not imply a signature match
  proves live tracking or that no match proves an app does not track.
- Keep the scanner’s completeness and cache protections: this audit now rejects
  unreadable splits and oversized DEX, invalidates results after source changes,
  and checks APK identity plus version for list, filter, sort and detail counts.

No additional database has been bundled at this checkpoint. The downloaded
App Manager tree and XML are `/tmp/cana-appmanager-tree.json` and
`/tmp/cana-appmanager-trackers.xml`; ETIP API documentation is
`/tmp/cana-etip-api.md`. Counts above are reproducible from these source files.
