# JetBrains Mono verification

Verified October 3, 2026. All 15 Compose typography roles, the Cana wordmark, native app descriptions and the Android base theme use bundled JetBrains Mono 2.304. Regular, Medium, SemiBold and Bold are unmodified upstream font files; the APK includes their license and provenance.

- Local verification passed 237 unit tests, release lint (0 errors, 704 warnings), and the debug build.
- [Release workflow 37164711336](https://github.com/jordanwoodson/cana/actions/runs/37164711336) succeeded for `85df62bfc1a8952572e994537e06ff9b4f8c8c92`, including the signed release build.
- [Release 3.2.2-cana.17](https://github.com/jordanwoodson/cana/releases/tag/3.2.2-cana.17) has version code 241. The downloaded APK passes its published checksum and verifies with the existing signing certificate.
- APK SHA-256: `34083caf94893e8405cea19d548282367f5e051b76ec46b559c64befdb62c975`.
- All four font files in the published, minified APK match the source files byte for byte. The font license and provenance are also present and unchanged.

Phone installation and visual checks were deferred at the user's request after wireless debugging disconnected.
