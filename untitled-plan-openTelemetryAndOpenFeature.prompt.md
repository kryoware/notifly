## Plan: OpenTelemetry and OpenFeature Foundations

Add privacy-preserving observability and feature-evaluation foundations to the existing KMP architecture.

**Steps**

1. Verify compatible OpenTelemetry and OpenFeature artifacts before changing dependencies.
2. Add shared, platform-neutral telemetry and feature-evaluation interfaces.
3. Provide iOS no-op implementations and Android implementations through existing Koin modules.
4. Configure OpenTelemetry for local, redacted instrumentation only:
   - Capture result categories and counts
   - Repository success/failure categories
   - Sync queue counts
   - No exporter or network transmission
5. Add OpenFeature SDK/API wiring with an in-process/default provider.
   - No remote provider, backend, authentication, or networking
   - Safe defaults for missing flags
6. Keep telemetry consent separate from Sentry’s crash-reporting preference.
   - Default off
   - Immediate revocation
   - No sensitive data in attributes or evaluation context
7. Initialize services safely in [NotiflyApplication.kt](app/src/main/kotlin/ph/notifly/android/NotiflyApplication.kt), without allowing telemetry or provider failures to block startup.
8. Add focused tests for:
   - Feature defaults and missing flags
   - Telemetry redaction
   - Consent gating
   - Notification-like strings never entering telemetry
9. Update [PRIVACY.md](docs/PRIVACY.md), [IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md), and [PLAN.md](PLAN.md) to document the local-only phase and deferred backend decisions.
10. Verify with:
    - `.\gradlew.bat :shared:testDebugUnitTest`
    - `.\gradlew.bat :shared:allTests`
    - `.\gradlew.bat :app:assembleDebug`
    - `.\gradlew.bat :app:lintDebug`
    - `git diff --check`

**Relevant files**

- [libs.versions.toml](gradle/libs.versions.toml) — dependency versions and aliases
- [shared/build.gradle.kts](shared/build.gradle.kts) — KMP dependency wiring
- [ErrorReporter.kt](shared/src/commonMain/kotlin/ph/notifly/domain/diagnostics/ErrorReporter.kt) — existing diagnostics boundary
- [CrashReporting.kt](shared/src/androidMain/kotlin/ph/notifly/data/diagnostics/CrashReporting.kt) — existing privacy and consent model
- [Modules.kt](shared/src/commonMain/kotlin/ph/notifly/di/Modules.kt), [Modules.android.kt](shared/src/androidMain/kotlin/ph/notifly/di/Modules.android.kt), and [Modules.ios.kt](shared/src/iosMain/kotlin/ph/notifly/di/Modules.ios.kt) — platform wiring
- [AppPreferences.kt](shared/src/commonMain/kotlin/ph/notifly/data/local/AppPreferences.kt) — only if a visible telemetry setting is added

**Decisions**

- “openfeatur” means OpenFeature.
- OpenFeature will be SDK/API-only for now.
- OpenTelemetry will be local-only with no exporter.
- Android gets real implementations; iOS gets no-op behavior.
- Telemetry consent remains distinct from Sentry consent.
- Notification bodies, transaction data, amounts, merchants, exception messages, and raw text are excluded from telemetry and feature context.

Recommended scope decision: design the consent boundary now, but defer adding a visible Settings toggle until an exporter is actually introduced.