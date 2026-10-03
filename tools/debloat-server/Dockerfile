# Builds MuntashirAkon's Android Debloat List browser directly from upstream.
# REF picks a branch or commit. No supplements, UAD-NG extras or audit overrides are applied.
ARG REF=master

FROM php:8.4-cli-alpine AS build
# The repo's build is verified against exactly this mdBook version.
ARG MDBOOK_VERSION=0.5.4
RUN wget -qO- "https://github.com/rust-lang/mdBook/releases/download/v${MDBOOK_VERSION}/mdbook-v${MDBOOK_VERSION}-$(uname -m)-unknown-linux-musl.tar.gz" \
    | tar -xz -C /usr/local/bin mdbook
ARG REF
ADD https://github.com/MuntashirAkon/android-debloat-list.git#${REF} /src
COPY uad_lists.php browser_suggestions.php /usr/local/bin/
WORKDIR /src
# Convert only upstream's five categories for clients using the package-keyed JSON format.
# The reference browser uses upstream's original descriptions, warnings and replacements.
RUN php scripts/lint.php \
 && php scripts/test.php \
 && php /usr/local/bin/uad_lists.php . /tmp/uad_lists.json \
 && php scripts/browser_generator.php \
 && php /usr/local/bin/browser_suggestions.php . \
 && sed -i -e 's|site-url = .*|site-url = "/"|' -e '/^multilingual = /d' browser/book.toml \
 && mdbook build browser \
 && cp COPYING browser/book/LICENSE \
 && cp /tmp/uad_lists.json browser/book/ \
 && find browser/book -type f \( -name '*.html' -o -name '*.js' -o -name '*.css' -o -name '*.json' \
      -o -name '*.svg' -o -name '*.txt' -o -name LICENSE \) -size +1k -exec gzip -9kn {} +

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/browser/book /usr/share/nginx/html
