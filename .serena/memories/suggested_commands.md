# Commands

Windows host. Both PowerShell and a Git Bash-style shell are available; paths
below assume the repo root `C:\Users\kryoware\AndroidStudioProjects\notifly`.

## Gradle

The wrapper **is committed** (`gradlew`, `gradlew.bat`,
`gradle/wrapper/gradle-wrapper.jar` are all tracked) even though `README.md`
step 1 and `PLAN.md` Phase 0 say "run `gradle wrapper`, it is not committed".
Ignore that instruction; it is stale. Verified working: `./gradlew --version`
→ Gradle 9.6.0.

- `./gradlew help` — cheapest check that the version catalog resolves.
- `./gradlew :shared:compileKotlinAndroid` — compile shared for Android.
- `./gradlew :shared:allTests` — parser regression suite.
- `./gradlew :app:assembleDebug` — debug APK.
- On Windows CMD/PowerShell use `.\gradlew.bat`; in Git Bash `./gradlew` works.

## JDK

`java` is **not on `PATH`**. `JAVA_HOME` points at the Android Studio JBR
(`C:\Users\kryoware\AppData\Local\Programs\Android Studio\jbr`, JDK 25), which
the wrapper picks up. Invoke a raw JDK tool as `"$JAVA_HOME/bin/java"`.
Processes that inherit `JAVA_HOME` (the wrapper included) resolve a JDK fine.

## Shell notes

- Repo has LF in the index, CRLF in the working tree; `git diff` prints
  "LF will be replaced by CRLF" warnings. Harmless, not a change to fix.
- `docs/prototype.html` is opened in a browser, not served.
