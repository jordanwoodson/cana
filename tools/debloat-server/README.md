# debloat

The [SysAdminDoc android-debloat-list](https://github.com/SysAdminDoc/android-debloat-list) (ADL) reference
browser at https://ubuntu-server.taila70e29.ts.net:3090 (tailnet only). The merged browser includes
ADL, supplements, UAD-NG extras and 740 individually reviewed Motorola package identities.
The October 1 audit expands the served database from 5,573 to 5,893 package IDs.
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
  browser pages show it as "Claude's recommendation".
- `/motorola_review.json` records sources, confidence, feature links and remaining uncertainties.
- `/tracker_findings.json` records the versioned static APK class-signature findings and completeness.
- `/exodus-trackers.json` is the attributed, reverified Exodus catalog, compatible with Cana's custom
  tracker-source URL setting. Its signatures were unchanged in the live October 1 API check.

The searchable mdBook pages are regenerated from the same merged JSON, including corrected current
APK labels, associated features, citations and review confidence. Upstream's fixed-count baseline
tests run before the local data conversion; the converted records then pass ADL lint/schema validation
and browser generation. Raw phone installation state, owner names and ADB endpoints are
not served. Static signature matches do not establish live collection, and missing matches do not
establish absence of tracking. Stripped/compiled firmware code, native behavior and APEX payloads
have separate inspection limits recorded in the review.

`--overlay` inputs are validated before output replacement; generated JSON is replaced atomically
with nginx-readable permissions. Rebuild with the existing compose command to apply the reviewed
files. Preserve the review date/source and unresolved points when editing an override.

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
