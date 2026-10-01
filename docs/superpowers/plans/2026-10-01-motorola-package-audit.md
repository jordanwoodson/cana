# Motorola package database audit implementation plan

> **For agentic workers:** Execute this plan natively with superpowers:executing-plans. Steps use checkbox syntax for tracking. The user authorized proceeding on October 1, 2026.

**Goal:** Research every package visible to Android Package Manager on the authorized Motorola and update Cana and the local debloat server with evidence-backed descriptions, feature relationships, removal recommendations, and tracker findings.

**Architecture:** Capture a read-only, versioned device inventory across all accessible users, including retained uninstalled packages and APEX modules. Inspect readable base and split APKs for manifest metadata and declared DEX classes; correlate results with primary Android, Motorola, and publisher sources. Preserve reviewed entries in a highest-priority server overlay and Cana's bundled package database, with provenance and uncertainty recorded separately.

**Tech Stack:** ADB, Python standard library, Android build-tools, existing PHP list converter and Docker/nginx server, Cana's existing UAD and Exodus JSON schemas.

**Spec:** The user's October 1, 2026 request in this conversation; scope and safeguards are copied below.

## Global constraints

- Inspect the authorized `motorola-edge-2025` over its explicitly authorized Tailscale endpoint; explicitly select its ADB serial for every phone operation.
- Include installed system and user packages, disabled packages, retained uninstalled packages, accessible profiles, and APEX modules.
- Read-only phone investigation: no uninstall, disable, component toggles, app launch, settings changes, root, or reboot.
- Preserve core phone features by default. Optional removals must explain feature loss; unresolved packages cannot become Recommended merely from their names.
- Explain package purpose, associated features, verified dependencies and removal consequences. Keep inferred feature associations distinct from manifest-declared dependencies.
- Static tracker signatures demonstrate included code, not runtime collection. Missing signatures and unreadable APKs cannot establish absence of tracking.
- Keep code signatures and network-domain data distinct. Preserve attribution and snapshot provenance.
- Do not publish raw device metadata or installation history to GitHub; leave changes locally reviewable.
- Preserve unrelated edits and existing server list coverage; do not let future upstream refreshes overwrite reviewed records silently.

## Review focus

- A package uninstalled for one profile can still be installed elsewhere; retain all profile states.
- Factory APKs, installed updates, splits, shared libraries and APEX packages have different inspection and removal properties.
- An overlay target is a feature relationship, not automatically a hard dependency.
- Permission requests and embedded tracker classes do not prove actual data collection.
- A list overlay must override existing ADL entries as well as add missing packages, and malformed input must fail before replacement.

### Task 1: Complete device evidence

**Files:** Create `scripts/audit-device-packages.py`; create local evidence under `docs/package-audit/2026-10-01/` with installation states excluded from served public data.

**Interfaces:** Explicit ADB serial in; complete package-ID set, per-profile state, APK identity, manifest and class evidence out.

- [x] Connect paired wireless ADB and verify manufacturer/model/build before inventory.
- [x] Capture per-user installed, system, third-party, disabled and `-u` sets plus APEX sets; reconcile counts and preserve command errors.
- [x] Inspect every available base/split APK. Record failures and completeness explicitly.
- [x] Validate DEX parsing against known classes and correlate package metadata with Android's own output.

### Task 2: Per-package research and recommendations

**Files:** Create a package-keyed research ledger and `motorola_packages.json` in the existing UAD schema; modify `app/src/main/assets/uad_lists.json` and, only where evidence supports it, `app/src/main/assets/exodus-trackers.json`.

**Interfaces:** Task 1 inventory and APK facts in; one review record and one database entry per inventoried package out.

- [x] Read existing ADL/UAD entries and verify each package against device evidence and primary publisher/platform sources.
- [x] Write individual descriptions, feature associations, removal consequences, citations, confidence and unresolved points.
- [x] Review critical providers, roles, permission controllers, network/telephony, launchers, keyboards, overlays, APEX and OEM service clusters conservatively.
- [x] Update confirmed tracker signatures only with validated, attributed evidence. Record static per-package findings and scope limitations.
- [x] Assert exact inventory coverage and independently review every removal recommendation and unresolved record.

### Task 3: Persistent merge and validation

**Files:** Modify the staged then authorized server `uad_lists.php`, `Dockerfile`, and `README.md`; add the reviewed overlay and provenance. Keep staging under this workspace until ready to install outside its writable roots.

**Interfaces:** Reviewed UAD-schema package overlay in; deterministic merged server list and Cana bundle out.

- [x] Add a validated highest-priority overlay after ADL/supplements/UAD fallback; exercise override, missing-package addition, malformed input and dependency metadata.
- [x] Validate JSON enums, IDs, nonempty descriptions, source fields, tracker-parser compatibility, dependency references and coverage.
- [x] Run relevant existing Cana package and tracker tests. Verify reviewed server entries survive the normal merge.
- [x] Install the authorized local server changes with filesystem escalation; rebuild/serve only after validation and verify the served list.
- [x] Record honest final counts, remaining uncertainties, validation results, and changed file locations.

## Progress and rulings

- Initial Cana tree clean on branch `ux`. Existing bundle: 5,381 packages, 432 Exodus tracker records.
- Live Exodus API downloaded October 1, 2026 matches the bundled tracker JSON semantically; SHA-256 `bf6922f7c95ceb130fcfabf96c32c615e60d62ca507a4b82dcd74b41061c5121`.
- Existing server source is `/home/jordan/containers/debloat`; its Docker build clones ADL and merges `user_apps.json` then UAD-NG. It is not a separate Git checkout.
- ADB pairing and connection succeeded. Complete Package Manager inventory: 740 identities, 698 APK and 42 APEX. Hidden Trichrome library included with `--match-libraries`. One accessible profile: 524 installed / 174 retained-uninstalled APK identities; 503 system / 195 third-party; 56 disabled.
- Ruling: The user's explicit authorization for this audit supersedes the older emulator-only restriction in prior project prompts; phone operations remain read-only.
- Ruling: Preserve core services and selected roles through conservative advice. The user authorized database/server updates and read-only ADB investigation; no phone removal experiments or management changes are included.

- Individual research ownership reconciled: 183 platform, 171 OEM/carrier, 173 third-party, 213 APEX/overlay entries. Every identity has an individual review, source, feature list, rationale and explicit confidence.
- APK evidence: 1,123 files, zero remaining collector/aapt/DEX errors; all 698 APK identities complete. Factory variants for all 34 updated system apps are included. DEX 041 was verified with official Android build-tools. APK bytes are transient and removed after inspection.
- Independent review caught omitted factory APKs and VPN/IME/administrator consequences. Both Important findings were fixed and rechecked; no Critical or Important findings remain. Active APK labels take priority over older factory branding.
- Final ratings: 168 Recommended / 164 Advanced / 183 Expert / 225 Unsafe. Confidence: 670 high / 62 medium / 8 low. Every low-confidence record is Expert or Unsafe.
- Tracker findings: 130 package identities match at least one confirmed Exodus signature in inspected variants; 187 have no DEX in readable APKs. Native code, compiled APEX implementations and runtime traffic remain explicitly outside static-scan certainty.
- Source/API verification: 432 official Exodus definitions unchanged; provenance and verification date refreshed without importing tentative signatures.
- Cana bundle: 5,381 to 5,716 records (+335), all 740 reviewed overrides applied. Unrelated records preserved byte-for-byte.
- Server merge/browser source now versioned under `tools/debloat-server/`; reviewed overrides apply after upstream sources and browser pages consume the same merged JSON. Six offline Docker tests pass, including malformed-input protection and readable atomic output. Final image build/deployment verified: merged 5,893 entries (+320), all 740 rendered reviewed pages match, all four payload hashes match, HTTPS tailnet feed responds with exact data, and conditional ETag requests return 304.

- Final verification: forced fresh `:app:testDebugUnitTest :app:assembleDebug` succeeded (225 tests, zero failures/errors); six server pipeline tests pass. Upstream ADL baseline tests: 45 passed; post-conversion ADL lint/schema validation passed and generated 5,893 package pages. `git diff --check` passes.
- Deployment: only debloat container replaced with verified `debloat:motorola-audit-final` retagged `debloat:local`. Server sources/payloads installed in `/home/jordan/containers/debloat`; compose, nginx and original user supplement hashes remain unchanged. Backup source/image retained at `/tmp/cana-debloat-before-audit-20261001` and `debloat:before-motorola-audit-20261001`.
- Validation evidence: `docs/package-audit/2026-10-01/validation/` contains fresh build/test logs and exact coverage/live-output checks. Static-scan and proprietary-function limitations remain in the report; no phone package/settings changes, commits, pushes or GitHub publication were performed.
