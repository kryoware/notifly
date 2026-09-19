# Tech stack

- Kotlin Multiplatform, Compose Multiplatform. Targets: `androidTarget`,
  `iosArm64`, `iosSimulatorArm64` (`iosX64()` is commented out in
  `shared/build.gradle.kts`, together with its `kspIosX64` Room entry — keep
  those two in sync if re-enabling).
- Gradle 9.6 wrapper, Kotlin DSL, version catalog `gradle/libs.versions.toml`.
- JVM target 17 for both modules (`jvmToolchain(17)` in `app`).
  compileSdk/targetSdk 36, minSdk 26.
- DI: Koin (`koin-core`, `koin-compose-viewmodel`, `koin-android`).
- Persistence (declared, not yet used): Room 2.x KMP + `androidx.sqlite:sqlite-bundled`,
  KSP per-target, schemas exported to `shared/schemas/`. DataStore Preferences
  for settings (theme palette, retention switch).
- Async/time: kotlinx-coroutines, kotlinx-datetime (`Instant`, `Clock.System`).
- Tests: `kotlin("test")` + `kotlinx-coroutines-test` in `commonTest`.
- Namespaces: app `ph.notifly.android`, shared `ph.notifly.shared`;
  package root `ph.notifly`.

## Version catalog trap

The catalog was written offline and **never resolved against a repository**
(banner comment at the top of the file). Wrong/nonexistent versions are
expected. When a build fails, suspect the catalog before the code, and fix
resolution first — it is Phase 0 of `PLAN.md`.

`gradle.properties` turns on configuration cache, parallel, and build cache, and
sets `android.builtInKotlin=false` / `android.newDsl=false`; a version bump of
AGP/Kotlin can break those flags.
