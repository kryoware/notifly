# Definition of done

Run, in this order, whatever the change touched:

1. `./gradlew :shared:allTests` — always, if any `shared/` code changed.
2. `./gradlew :shared:compileKotlinAndroid` — for KMP/source-set changes.
3. `./gradlew :app:assembleDebug` — for anything in `app/` or the manifest.

There is no linter, formatter, or separate type-check step configured; the
Kotlin compiler is the type check. Serena's Kotlin language server does not
start here, so `get_diagnostics_for_file` is not an option either.

## Gates specific to this project

- **Never change parser behaviour to make a test pass.** The tests in
  `NotificationParserTest` were written before the implementation and encode
  intended behaviour; a failure means the parser is wrong, not the test.
- After touching capture/log/sync code, grep for log statements that could
  carry notification text:
  `grep -rnE "Log\.|println" app/src shared/src` (Git Bash; currently returns
  nothing, which is the expected state)
  Package names and counts only, never bodies, never at DEBUG.
- After touching anything on the sync path, confirm no raw body and no
  `NEEDS_REVIEW` row can reach a payload.
- UI work cannot be verified by the agent without a device/emulator — say so
  explicitly instead of claiming a visual result, and check the intended
  behaviour against `docs/prototype.html`.
- Tick the corresponding checkbox in `PLAN.md` when a phase item is genuinely
  finished.
