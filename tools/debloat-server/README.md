# debloat

The [Android Debloat List](https://github.com/MuntashirAkon/android-debloat-list)
reference browser, served by nginx on port 3090. Builds use only MuntashirAkon's
five upstream package categories and replacement suggestions. No Motorola audit
ratings, Claude verdicts, user-app supplements or UAD-NG fallback records are applied.

Cana downloads the upstream repository ZIP directly and bundles an offline copy.
It maps upstream `delete`, `replace`, `caution` and `unsafe` guidance to the
Recommended, Advanced, Expert and Unsafe badges. Warnings, dependencies,
replacement app names, repository links and restriction notes are preserved.
Existing app settings pointing at the previous Cana server or the old UAD-NG
default are automatically routed to the new upstream source. Other custom URLs
remain supported in package-keyed JSON format.

## Build and serve

The Dockerfile clones upstream at `REF` (default `master`), runs upstream lint,
generates its browser pages, adds upstream replacement suggestions and their
restriction notes, and builds with mdBook 0.5.4. It also produces
`/uad_lists.json` for clients requiring package-keyed JSON. That file includes
source attribution and structured replacement records, plus display names for
older clients. The upstream AGPL license is served at `/LICENSE`.

```sh
python3 tools/debloat-server/prepare-context.py --destination /tmp/cana-debloat-stage
sudo docker build -t debloat:local /tmp/cana-debloat-stage
```

To update a deployed instance, rebuild and recreate its container using the
compose file. Set `REF` to an upstream commit to pin the data. Compose publishes
only `127.0.0.1:3090`; the existing tailnet endpoint can be exposed with:

```sh
sudo tailscale serve --bg --https=3090 http://127.0.0.1:3090
```

Changing these build files does not replace a running server. Cana's default
list updates work independently of the server.

## Device-reviewed lists

`prepare-context.py --device-list NAME=PATH` stages a package-keyed review as `overlays/NAME.json`.
The build merges it over the upstream list (the review decides rating, description, source and
Cana's verdict; upstream's display name, warning, references and replacement records stay) and
serves it at `/devices/NAME/uad_lists.json`. The default `/uad_lists.json` is unchanged.

The Metro Motorola Edge 2025 review lives in `docs/package-audit/2026-10-01/motorola_packages.json`
(current decisions: [v4, 2026-10-06](../../docs/package-audit/2026-10-06/rerating-v4-report.md)):

```sh
python3 tools/debloat-server/prepare-context.py --destination ~/containers/debloat \
    --device-list moto-edge-2025=docs/package-audit/2026-10-01/motorola_packages.json
cd ~/containers/debloat && sudo docker compose build && sudo docker compose up -d
```

In Cana, set Settings → Bloat list URL to
`https://ubuntu-server.taila70e29.ts.net:3090/devices/moto-edge-2025/uad_lists.json`
(the plain `/uad_lists.json` address is routed back to the upstream default).

## Historical audit tools

The optional converter flags, `apply_browser_lists.php`, `user_apps.json` and
[October 2026 audit artifacts](../../docs/package-audit/2026-10-01/README.md)
remain available for reference. They are excluded from the default build.
Run converter regression tests with `python3 tools/debloat-server/test_overlay.py`.
