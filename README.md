# Notifly

Parses payment notifications into a personal ledger. Android (KMP-ready).

## Setup

1. `gradle wrapper` — the wrapper is not committed.
2. Open in Android Studio, let it sync.
3. Versions in `gradle/libs.versions.toml` are unverified; fix resolution first.
4. `./gradlew :shared:allTests` to run the parser suite.

See `CLAUDE.md` for architecture rules and build order.
See `PLAN.md` for the phased build order.
See `docs/prototype.html` for the interaction spec.
