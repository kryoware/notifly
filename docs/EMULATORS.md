# Shared Android emulator pool

The pool contains `notifly_agent_1` (`emulator-5580`) and `notifly_agent_2`
(`emulator-5582`). Each has independent persistent data, 2 CPU cores, 2 GB RAM,
a 720 × 1600 display, and software graphics. WHPX accelerates the VMs on Windows.
Cold boots avoid dependence on Gradle-managed snapshots. The existing Gradle
device and connected physical phones are left alone.

Requires Python 3, the Android SDK emulator/platform-tools, and the installed
`system-images;android-37.0;google_apis;x86_64` image. Override `--sdk` if needed.
No new Python packages are required.

```powershell
python tools/emulator_pool.py start
python tools/emulator_pool.py status
```

`start` creates missing AVD configs, boots both, and waits for Android and its
package manager. It reuses already running pool devices. It defaults to headless;
pass `start --window` on the initial launch for native emulator windows. T3's
Device panel can stream the headless devices when device access is enabled.
`--gpu host` is available if software rendering is too slow. An existing device
must be shut down before changing launch options. `--image` selects a different
installed x86_64 SDK image when initially creating the AVDs; existing data/configs
are never reset by `start`.

## Agent protocol

Every agent must claim a pool device **before** installing an APK, tapping,
running UI tests, capturing screenshots, opening it with T3, or shutting it down.
Claims are cooperative: direct ADB/T3 access cannot enforce this protocol.
Build first, then claim for the shortest practical device session.

For one command (recommended), `run` queues, sets `ANDROID_SERIAL` and
`EMULATOR_SERIAL`, renews the claim while the command runs, and releases it on
success, failure, or interruption. It returns the command's exit code.

```powershell
python tools/emulator_pool.py run --owner agent-task-name -- adb shell wm size
python tools/emulator_pool.py run --owner agent-task-name -- adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a sequence of tool calls, including T3:

```powershell
python tools/emulator_pool.py acquire --owner agent-task-name
# JSON: token, serial, avd, expires, ... Save token and serial for the whole session.
# Optional: acquire --owner agent-task-name --serial emulator-5582
adb -s <serial> shell wm size
python tools/emulator_pool.py renew <token>
python tools/emulator_pool.py release <token>
```

With T3, call `device_list`, then `device_open` for the claimed `avd` using the
host/device IDs returned by the list. Use its returned pinned agent-device CLI
for interaction. For screenshots, explicitly pass that same host/device ID.
Release the token when finished; leave the emulator running for the next agent.
Acquire once for an entire install/test/screenshot sequence so another agent
cannot replace your APK between commands.

The default claim lasts 15 minutes without renewal. Renew before expiry (e.g.
every 5 minutes) during interactive work, or use `run` for automatic renewal.
An expired claim gives no right to keep using the device. If an agent or `run`
wrapper is forcibly killed, its claim expires; stop any surviving child command
before resuming device work. The queue does not reset application data: each
agent must prepare the test state it needs after claiming.

Waiting is FIFO among requests compatible with a ready, free device. A request
for one busy device does not block use of the other. `--wait` defaults to 30
minutes, after which the request is removed and the command fails. Queued or
held claims from abandoned sessions expire automatically. Tokens prevent one
agent from accidentally releasing another's claim. `status` shows owners and
queued requests without exposing tokens.

All worktrees use the same SQLite queue at
`%LOCALAPPDATA%/Notifly/emulator-pool/queue.sqlite3`; runtime files stay outside Git.
Leave `--state-dir` unchanged during real use, otherwise separate queues cannot
coordinate the same devices. Startup logs in that directory contain emulator
boot diagnostics; this tool never captures notification text or runs logcat.

```powershell
python tools/test_emulator_pool.py
```

The check exercises FIFO, contention from independent processes, slot preference,
renewal, expiry, offline-device exclusion, and command heartbeat/cleanup without
requiring emulators.
