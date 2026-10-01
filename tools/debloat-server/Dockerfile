# Builds the SysAdminDoc android-debloat-list reference browser (static mdBook site) straight from
# GitHub and serves it with nginx. REF picks the branch, tag or commit to build. UADNG_REF picks the
# UAD-NG branch whose uad_lists.json fills in packages ADL lacks.
ARG REF=master
ARG UADNG_REF=main

FROM php:8.4-cli-alpine AS build
# The repo's build is verified against exactly this mdBook version.
ARG MDBOOK_VERSION=0.5.4
RUN wget -qO- "https://github.com/rust-lang/mdBook/releases/download/v${MDBOOK_VERSION}/mdbook-v${MDBOOK_VERSION}-$(uname -m)-unknown-linux-musl.tar.gz" \
    | tar -xz -C /usr/local/bin mdbook
ARG REF
ARG UADNG_REF
ADD https://github.com/SysAdminDoc/android-debloat-list.git#${REF} /src
ADD https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/${UADNG_REF}/resources/assets/uad_lists.json /lists/uad-ng.json
COPY user_apps.json /lists/
COPY motorola_packages.json motorola_review.json tracker_findings.json exodus-trackers.json /lists/
COPY uad_lists.php apply_browser_lists.php /usr/local/bin/
WORKDIR /src
# Run upstream's baseline lint/tests, then generate/build once from reviewed data.
# The license page links to ../LICENSE, which is the site root when served, so copy it (and the
# notices) there. uad_lists.json is ADL + user_apps.json + UAD-NG's extras in UAD-NG's format, for
# Canta/Cana's "Bloat list URL"; user_apps.json is served raw too, so the supplement can be reviewed.
# Then precompress text files for gzip_static: the search index alone is ~20 MB.
RUN php scripts/lint.php \
 && php scripts/test.php \
 && php /usr/local/bin/uad_lists.php . /lists/merged-uad.json --uadng /lists/uad-ng.json --supplement /lists/user_apps.json --overlay /lists/motorola_packages.json \
 && php /usr/local/bin/apply_browser_lists.php . /lists/merged-uad.json /lists/motorola_review.json \
 && printf '\n## Motorola Edge 2025 review\n\nThe October 1, 2026 audit individually reviews all 740 visible package identities on Android 16. These searchable pages use the same reviewed descriptions and recommendations as Cana. [Review ledger](motorola_review.json), [package overlay](motorola_packages.json), [static tracker findings](tracker_findings.json) and [confirmed Exodus catalog](exodus-trackers.json) are available for inspection. Raw installation state and device identifiers are excluded. Static matches do not establish runtime data collection; unresolved behavior is explicitly recorded.\n' >> browser/README.md \
 && php scripts/lint.php \
 && php scripts/browser_generator.php \
 && mdbook build browser \
 && cp LICENSE THIRD-PARTY-NOTICES.txt browser/book/ \
 && cp /lists/merged-uad.json browser/book/uad_lists.json \
 && cp /lists/user_apps.json browser/book/ \
 && cp /lists/motorola_packages.json /lists/motorola_review.json /lists/tracker_findings.json /lists/exodus-trackers.json browser/book/ \
 && find browser/book -type f \( -name '*.html' -o -name '*.js' -o -name '*.css' -o -name '*.json' \
      -o -name '*.svg' -o -name '*.txt' -o -name LICENSE \) -size +1k -exec gzip -9kn {} +

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/browser/book /usr/share/nginx/html
