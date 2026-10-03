# Splash and launcher verification

Verified 2026-10-03 on `notifly_agent_1` / `emulator-5554`, Android 17 (API 37).
Physical devices were not used.

## Automated checks

```powershell
./gradlew.bat :shared:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew.bat :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w ph.notifly.android.test/androidx.test.runner.AndroidJUnitRunner
```

Results: 108 shared unit tests, 7 launcher unit tests, and 3 emulator instrumentation
tests passed. Debug assembly and lint passed. The launcher unit tests run against
Robolectric SDK 32 and 33, covering both sequential and batch switching. They check
all 15 manifest mappings, exactly one active entry, unchanged/repeated application,
enable-before-disable ordering, injected failure, and reconciliation on retry.
The persistence test reopens palette and theme mode together using the existing keys.
Its Windows host uses DataStore's Okio storage for atomic file replacement.

## Emulator checks

- All five palettes in System, Light, and Dark: appearance and launcher selection update.
- Settings mode/palette controls and Theme Gallery both update the launcher while
  the current screen stays open. Android requires a separate task rooted in the
  enabled activity; merely clearing an alias-rooted task retains its alias identity.
- Force-stop/relaunch and reboot retain Clay/System and exactly one launcher entry.
- System night-mode changes update the app and the launcher's full-color icon.
  The selected System alias remains the same.
- Portrait/landscape rotation, Activity recreation, background/resume, and tapping
  the launcher again retain launch completion.
- Onboarding, home, and PIN lock are revealed after startup. Lock covers app content;
  authentication is never restored from process saved state.
- Remove animations skips the launch sequence and its setting is restored after testing.
- The final recording shows an icon-free neutral starting window, the Clay mark
  tracing through its check, and the fade to home without a default-Ube frame.

Local evidence (ignored `captures/` directory): `splash-final.mp4`,
`splash-final-sheet.png`, `appearance-matrix.png`, and individual appearance/lock captures.

The emulator originally contained a newer database schema. Its original data remains
on that emulator in `databases.before-splash-test` and `files.before-splash-test`;
verification used separate clean data. The emulator is left on Clay/System, portrait,
system light mode, with animations enabled.

API 32 fallback was verified with Robolectric, not an older emulator. Launcher caches,
pinned shortcuts, and wallpaper-tinted monochrome icons remain launcher-controlled.
The implementation follows Android's [splash-screen guidance](https://developer.android.com/develop/ui/views/launch/splash-screen),
[PackageManager API](https://developer.android.com/reference/android/content/pm/PackageManager), and
[adaptive-icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
