# debloat

The [SysAdminDoc android-debloat-list](https://github.com/SysAdminDoc/android-debloat-list) (ADL) reference
browser at https://ubuntu-server.taila70e29.ts.net:3090 (tailnet only). The merged browser includes
ADL, supplements, UAD-NG extras and 766 individually reviewed Motorola package identities.
The October 1 audit expanded the served database from 5,573 to 5,893 package IDs.
The October 2 v2 review adds 26 user apps to the original 740, making 766 reviewed IDs. With the
same upstream inputs the prepared merged total would be 5,919; no new Docker build was run to
measure it. The live server is not changed until separate deployment.
It's a reference only and doesn't touch a phone.

The Dockerfile clones the repo at `REF` (default `master`, set in compose.yaml). It runs upstream's
lint and baseline tests, merges and converts the reviewed data, then runs lint/schema validation,
`browser_generator.php` and one final mdBook 0.5.4 build. nginx serves the result, with no volumes or state.

- **Update to the latest upstream**: `sudo docker compose build --pull && sudo docker compose up -d`.
  If upstream's tests fail (e.g. its hard-coded package counts drift), the build stops and the running
  container is left as it was. The same command also picks up UAD-NG's latest list and this directory's
  `user_apps.json`.
- **Pin a release**: set `REF: v0.1.0` (or a commit SHA) in compose.yaml, then rebuild. `UADNG_REF`
  (default `main`) pins the UAD-NG branch or commit the same way.
- **Tailnet exposure**: `sudo tailscale serve --bg --https=3090 http://127.0.0.1:3090`.
- **Canta/Cana**: set Settings → Bloat list URL to
  `https://ubuntu-server.taila70e29.ts.net:3090/uad_lists.json`, then pull to refresh the app list.
  Canta only reads UAD-NG's format, so `uad_lists.php` builds that file at build time from three sources,
    with reviewed overrides taking precedence, then the original sources below:
  0. `motorola_packages.json`: individually reviewed package records from the authorized Motorola
     Edge 2025 Android 16 audit. This UAD-schema overlay **replaces** earlier descriptions and removal
     recommendations, including records already in ADL; future upstream rebuilds preserve it.
  1. ADL's five category files (delete → Recommended, replace → Advanced, caution → Expert,
     unsafe → Unsafe). `warning` is appended to the description and suggestion lists are spelled out as
     app names.
  2. `user_apps.json` in this directory: third-party "user apps" ADL doesn't cover (regional marketplaces,
     preinstalled games, messengers, streaming apps), written in ADL's schema so entries can be sent
     upstream as they are. They land in the Misc list with their Play Store link appended. An entry ADL
     later adds is skipped with a note in the build log; a malformed entry fails the build.
  3. UAD-NG's current `uad_lists.json`, for packages neither of the above lists. ADL's dataset has been
     frozen since 2026-05-14, so this picks up UAD-NG's newer OEM packages.

  Every entry carries a `source` field naming its origin; Canta ignores it, Cana
  shows it on the app's overview. The build log prints the count per source. The phone has to be on the
  tailnet to update the list; nginx's ETag lets Cana skip unchanged downloads.
- `user_apps.json` is also served raw at `/user_apps.json`. The raw ADL JSON is on GitHub. Only the
  rendered site and the converted lists are served here.

The October 1, 2026 audit covers normal APK packages, retained uninstalled packages, hidden static
libraries (`--match-libraries`) and APEX containers. Each reviewed entry explains its function,
associated features and removal consequences, with conservative treatment of core services and
unresolved proprietary behavior. Overlay resource targets are documented as feature relationships,
not automatically inserted as hard dependencies.

- `/motorola_packages.json` is the reviewed overlay consumed by Cana's merged list. Its system-package
  entries carry a `verdict` (`Keep`/`Uninstall`, privacy/performance gains, reason) from
  `docs/package-audit/2026-10-01/cana_verdicts.json`; `uad_lists.php` validates and keeps it, and the
  browser pages currently label the same derived action "Claude's recommendation". This is no longer
  a separate policy: **Uninstall if and only if Recommended**, otherwise Keep.
- `/motorola_review.json` records sources, confidence, feature links and remaining uncertainties.
- `/tracker_findings.json` records the versioned static APK class-signature findings and completeness.
- `/exodus-trackers.json` is the attributed, reverified Exodus catalog, compatible with Cana's custom
  tracker-source URL setting. Its signatures were unchanged in the live October 1 API check.

The searchable mdBook pages are regenerated from the same merged JSON, including corrected current
APK labels, associated features, citations and review confidence. Upstream's fixed-count baseline
tests run before the local data conversion; the converted records then pass ADL lint/schema validation
and browser generation. Raw phone dumps, owner names and ADB endpoints are
not served. The corrected ledger includes selected package facts supporting the re-audit. Static signature matches do not establish live collection, and missing matches do not
establish absence of tracking. Stripped/compiled firmware code, native behavior and APEX payloads
have separate inspection limits recorded in the review.

`--overlay` inputs are validated before output replacement; generated JSON is replaced atomically
with nginx-readable permissions. Rebuild with the existing compose command to apply the reviewed
files. Preserve the review date/source and unresolved points when editing an override.


## October 2 v2 bootloop correction

The October 1 recommendations were followed by a bootloop and recovery factory reset. The reviewed
v2 inputs replace all 740 original ratings and add 26 post-reset user apps, for **766 IDs**:
**545 system/APEX and 221 user apps**. Current ratings: **115 Recommended, 189 Advanced,
179 Expert, 283 Unsafe**. Verdicts: **115 Uninstall, 651 Keep**.
System ratings are **71 Recommended / 20 Advanced / 171 Expert / 283 Unsafe**; user ratings are
**44 Recommended / 169 Advanced / 8 Expert / 0 Unsafe**. Confidence is **245 high / 466 medium / 55 low**.
The superseded Metro-pass counts were 401 / 186 / 74 / 79; the historical first pass was
44 / 208 / 206 / 282 over 740 IDs.

The goals are privacy and performance debloating, plus consolidating to one app per job:

- **Recommended**: the agent actually recommends removal to advance those goals. Positive evidence
  establishes boot/core-phone safety. Named feature loss is allowed; merely being removable is not
  a reason to recommend removal.
- **Advanced**: keep an app or feature the owner needs or likely uses. Removal is boot-safe but
  loses that feature and possibly local data, stated in `Removing breaks:`.
- **Expert**: keep because boot/core integration is unresolved or removal risks broad/unpredictable
  setup, updates, sign-in, Play services, multi-app or carrier breakage. Unknown optional feature
  value alone does not justify Expert when boot safety is established.
- **Unsafe**: never remove; boot/crash loops, Rescue Party factory-reset prompts, or loss of calls/911,
  SMS, data, SIM/eSIM, Wi-Fi, Bluetooth, SystemUI, Settings, the launcher, installs, security or
  charging/thermal protection are possible.

Persistent packages, active privileged shared UIDs, RROs, APEXes/contained APKs, library/WebView
providers and low-confidence system packages cannot be Recommended. Core bindings/queries and
boot/setup/telephony/carrier/operator paths stay protected unless positive, individually scoped
inert evidence exists. Four optional core queries have such evidence: ATT IQI, Glance, Motorola
Help and Verizon ECID; their absence handling/observed surviving optional paths are documented.
Historical shared-UID declarations are preserved alongside fresh Package Manager observations.
An active Metro SIM does not establish that other carriers' boot or provisioning services are inert.
RRO state is per user; static/immutable status does not prove per-user uninstall is ignored.

The 58 surviving removals and five October 2 boot records support the observed boot-safe combination,
not complete feature safety; removal timestamps and failed-boot logs are unavailable. Consolidation
requires retaining the replacement, especially the default, Direct-Boot-aware FUTO keyboard.

The requested overrides keep Free up space (`com.android.storagemanager`) and the only voice recorder
(`com.motorola.audiorecorder`) Advanced. The 26 new user records have full UAD/ledger entries and
explicit `not_scanned` tracker records. No tracker scan or manifest-relationship capture was done
for them; existing scan records, dates/hashes and manifest relationships are preserved. Null user
descriptions inherit v1. Supplied `Source:` lines are preserved. The user input's SwiftKey reason
still names HeliBoard even though HeliBoard is also Recommended; the report flags this without
changing the approved decision. FUTO is retained.

Read the [historical post-mortem](../../docs/package-audit/2026-10-01/bootloop-postmortem.md),
[system decisions](../../docs/package-audit/2026-10-01/rerating-v2-system.json),
[user decisions](../../docs/package-audit/2026-10-01/rerating-v2-user.json),
[all 115 recommendations](../../docs/package-audit/2026-10-01/rerating-v2-report.md) and
[24-package restore list](../../docs/package-audit/2026-10-01/restore-2026-10-02.md).
The v1 decisions/report remain history. To reproduce the reviewed JSON before preparing a context:

```sh
python3 scripts/apply-package-rerating.py
python3 scripts/apply-package-rerating.py --check
python3 scripts/validate-package-rerating.py --report docs/package-audit/2026-10-01/validation/rerating-v2.json
```

The apply script reads the captured pre-re-rating HEAD (`dedbed0c2546f80140e296cf1ef7ab3710a54d7b`)
using read-only Git queries, replays v1 for description inheritance, then applies both v2 files.
The validator reproduces all five outputs, checks idempotence, all 766 IDs and verdict equivalence,
system safety guards, report/restore coverage, and byte preservation of unrelated bundle records
against HEAD. The app bundle now contains 5,742 unique IDs (26 more than 5,716).

No Docker build, sudo, live-server update, phone mutation, commit, push or app release was performed.
The raw October 1 audit is missing. No JDK/Android SDK is available, so Gradle checks were not run;
the app's hardcoded Kotlin rating explanations require a later update. No Kotlin was changed.

This directory is the versioned server-source snapshot. The larger sanitized audit payloads live
in `docs/package-audit/2026-10-01/`, and the confirmed tracker catalog is Cana's bundled asset.
Prepare a complete build context from the repository root before building:

```sh
python3 tools/debloat-server/prepare-context.py --destination /tmp/cana-debloat-stage
sudo docker build -t debloat:motorola-audit /tmp/cana-debloat-stage
python3 tools/debloat-server/test_overlay.py
```

The live server directory must contain these sources plus the four JSON payloads. Keep a backup of
its existing source and image before copying changed files and running its normal compose rebuild.
The staging helper prepares a complete snapshot; it should not be used to overwrite a separately
customized live `user_apps.json`, `compose.yaml` or `nginx.conf`.
