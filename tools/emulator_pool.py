"""Two local Android emulators and a cross-worktree FIFO lease queue (stdlib only)."""

import argparse
from contextlib import closing, contextmanager
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import time
import uuid


SLOTS = {"emulator-5580": "notifly_agent_1", "emulator-5582": "notifly_agent_2"}
STATE = Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local/share")) / "Notifly/emulator-pool"


@contextmanager
def database(state):
    state.mkdir(parents=True, exist_ok=True)
    with closing(sqlite3.connect(state / "queue.sqlite3", timeout=30)) as db, db:
        db.row_factory = sqlite3.Row
        db.execute("""CREATE TABLE IF NOT EXISTS requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT, token TEXT UNIQUE NOT NULL,
            owner TEXT NOT NULL, preferred TEXT, serial TEXT UNIQUE,
            ttl REAL NOT NULL, expires REAL NOT NULL)""")
        db.execute("BEGIN IMMEDIATE")
        db.execute("DELETE FROM requests WHERE expires <= ?", (time.time(),))
        yield db


def enqueue(state, owner, preferred, ttl, wait):
    token = uuid.uuid4().hex
    with database(state) as db:
        db.execute("INSERT INTO requests(token,owner,preferred,ttl,expires) VALUES(?,?,?,?,?)",
                   (token, owner, preferred, ttl, time.time() + wait))
    return token


def dispatch(db, ready):
    free = set(ready) - {r[0] for r in db.execute("SELECT serial FROM requests WHERE serial IS NOT NULL")}
    # FIFO among requests compatible with a free slot; a busy preferred slot never blocks the other.
    for row in db.execute("SELECT * FROM requests WHERE serial IS NULL ORDER BY id").fetchall():
        choices = free & {row["preferred"]} if row["preferred"] else free
        if choices:
            serial = min(choices)
            db.execute("UPDATE requests SET serial=?, expires=? WHERE token=?",
                       (serial, time.time() + row["ttl"], row["token"]))
            free.remove(serial)


def claim(state, token, ready):
    with database(state) as db:
        dispatch(db, ready)
        row = db.execute("SELECT * FROM requests WHERE token=?", (token,)).fetchone()
        return dict(row) if row else None


def release(state, token):
    with database(state) as db:
        if not db.execute("DELETE FROM requests WHERE token=?", (token,)).rowcount:
            raise RuntimeError("Claim is absent or expired.")


def renew(state, token):
    with database(state) as db:
        if not db.execute("UPDATE requests SET expires=?+ttl WHERE token=? AND serial IS NOT NULL",
                          (time.time(), token)).rowcount:
            raise RuntimeError("Claim is absent or expired; stop using that emulator.")


def sdk_path(value):
    default = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk" if os.name == "nt" else Path.home() / "Android/Sdk"
    sdk = Path(value or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
               or default)
    if not (sdk / "emulator" / executable("emulator")).is_file():
        raise RuntimeError(f"Android SDK emulator missing in {sdk}; pass --sdk.")
    return sdk


def executable(name):
    return name + (".exe" if os.name == "nt" else "")


def adb(sdk, serial, *args):
    result = subprocess.run([str(sdk / "platform-tools" / executable("adb")), "-s", serial, *args],
                            capture_output=True, text=True, timeout=10)
    return result.stdout.strip() if result.returncode == 0 else ""


def ready_devices(sdk):
    return [serial for serial, name in SLOTS.items()
            if adb(sdk, serial, "emu", "avd", "name").splitlines()[:1] == [name]
            and adb(sdk, serial, "shell", "getprop", "sys.boot_completed") == "1"
            and adb(sdk, serial, "shell", "pm", "path", "android").startswith("package:")]


def start(args, sdk):
    image = sdk / "system-images" / args.image
    if not (image / "system.img").is_file():
        raise RuntimeError(f"Install SDK system image {args.image} first.")
    if image.name != "x86_64":
        raise RuntimeError("The pool requires an x86_64 system image.")
    avd_root = Path(os.environ.get("ANDROID_AVD_HOME", Path.home() / ".android/avd"))
    avd_root.mkdir(parents=True, exist_ok=True)
    with database(args.state_dir):
        for serial, name in SLOTS.items():
            current = adb(sdk, serial, "emu", "avd", "name").splitlines()
            if current:
                if current[0] != name:
                    raise RuntimeError(f"{serial} is occupied by {current[0]}; leaving it alone.")
                continue
            avd_dir = avd_root / (name + ".avd")
            avd_dir.mkdir(exist_ok=True)
            config = avd_dir / "config.ini"
            if not config.exists():
                values = {
                    "avd.ini.encoding": "UTF-8", "AvdId": name,
                    "avd.ini.displayname": name, "abi.type": "x86_64", "hw.cpu.arch": "x86_64",
                    "hw.cpu.ncore": "2", "hw.ramSize": "2048", "hw.gpu.enabled": "yes",
                    "hw.gpu.mode": args.gpu, "hw.lcd.width": "720", "hw.lcd.height": "1600",
                    "hw.lcd.density": "320", "hw.keyboard": "yes", "hw.mainKeys": "no",
                    "hw.audioInput": "no", "hw.audioOutput": "no", "hw.camera.back": "none",
                    "hw.camera.front": "none", "hw.sdCard": "no", "disk.dataPartition.size": "4G",
                    "image.sysdir.1": image.relative_to(sdk).as_posix() + "/",
                    "tag.id": image.parent.name, "target": image.parent.parent.name,
                    "skin.name": "720x1600", "showDeviceFrame": "no", "fastboot.forceColdBoot": "yes",
                }
                config.write_text("".join(f"{k}={v}\n" for k, v in values.items()), encoding="utf-8")
            (avd_root / (name + ".ini")).write_text(
                f"avd.ini.encoding=UTF-8\npath={avd_dir.resolve()}\ntarget={image.parent.parent.name}\n",
                encoding="utf-8")
            port = serial.split("-")[1]
            command = [str(sdk / "emulator" / executable("emulator")), "-avd", name,
                       "-port", port, "-no-audio", "-no-boot-anim", "-no-snapshot",
                       "-gpu", args.gpu, "-memory", "2048", "-cores", "2"]
            if not args.window:
                command.append("-no-window")
            options = {"creationflags": subprocess.CREATE_NO_WINDOW | subprocess.DETACHED_PROCESS} if os.name == "nt" else {"start_new_session": True}
            with (args.state_dir / f"{serial}.boot.log").open("ab") as log:
                subprocess.Popen(command, stdout=log, stderr=log, stdin=subprocess.DEVNULL, **options)
    deadline = time.monotonic() + args.boot_timeout
    while time.monotonic() < deadline:
        ready = ready_devices(sdk)
        if len(ready) == len(SLOTS):
            print(json.dumps({"ready": ready}))
            return
        print(f"Booting: {', '.join(set(SLOTS) - set(ready))}", file=sys.stderr, flush=True)
        time.sleep(5)
    raise RuntimeError(f"Boot timed out; inspect emulator boot logs in {args.state_dir}.")


def acquire(args, sdk):
    token = enqueue(args.state_dir, args.owner, args.serial, args.ttl, args.wait)
    announced = 0
    try:
        while True:
            row = claim(args.state_dir, token, ready_devices(sdk))
            if row is None:
                raise RuntimeError("Timed out waiting for an emulator; run start/status to check the pool.")
            if row["serial"]:
                row["avd"] = SLOTS[row["serial"]]
                return row
            if time.monotonic() - announced > 30:
                print(f"Queued for {args.serial or 'either emulator'} as {args.owner}", file=sys.stderr, flush=True)
                announced = time.monotonic()
            time.sleep(1)
    except BaseException:
        with database(args.state_dir) as db:
            db.execute("DELETE FROM requests WHERE token=?", (token,))
        raise


def stop_child(child):
    if child.poll() is None:
        if os.name == "nt":
            subprocess.run(["taskkill", "/PID", str(child.pid), "/T", "/F"], capture_output=True)
        else:
            child.terminate()
        child.wait(timeout=10)


def run(args, sdk):
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        raise RuntimeError("Pass a command after --, e.g. run --owner agent-1 -- adb shell wm size")
    row = acquire(args, sdk)
    child = None
    try:
        env = dict(os.environ, ANDROID_SERIAL=row["serial"], EMULATOR_SERIAL=row["serial"],
                   EMULATOR_LEASE_TOKEN=row["token"])
        env["PATH"] = str(sdk / "platform-tools") + os.pathsep + env.get("PATH", "")
        print(f"Claimed {row['serial']} for {row['owner']}", file=sys.stderr, flush=True)
        child = subprocess.Popen(command, env=env)
        while True:
            try:
                return child.wait(timeout=min(args.ttl / 3, 30))
            except subprocess.TimeoutExpired:
                renew(args.state_dir, row["token"])
    finally:
        if child:
            stop_child(child)
        with database(args.state_dir) as db:
            db.execute("DELETE FROM requests WHERE token=?", (row["token"],))


def positive(value):
    number = float(value)
    if not 0 < number < float("inf"):
        raise argparse.ArgumentTypeError("Must be a finite positive number.")
    return number


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--state-dir", type=Path, default=STATE, help="Shared across ALL worktrees; normally leave unchanged.")
    parser.add_argument("--sdk")
    commands = parser.add_subparsers(dest="action", required=True)
    boot = commands.add_parser("start", help="Create and boot both independent AVDs.")
    boot.add_argument("--image", default="android-37.0/google_apis/x86_64")
    boot.add_argument("--gpu", default="software", choices=["software", "host", "auto", "swiftshader", "swangle", "lavapipe"])
    boot.add_argument("--window", action="store_true")
    boot.add_argument("--boot-timeout", type=positive, default=300)
    commands.add_parser("status")
    for action in ("acquire", "run"):
        cmd = commands.add_parser(action)
        cmd.add_argument("--owner", required=True, help="Agent/task/worktree name.")
        cmd.add_argument("--serial", choices=SLOTS, help="Wait for this slot, or omit for either.")
        cmd.add_argument("--ttl", type=positive, default=900, help="Seconds without renewal before claim expires.")
        cmd.add_argument("--wait", type=positive, default=1800, help="Maximum seconds in the queue.")
        if action == "run":
            cmd.add_argument("command", nargs=argparse.REMAINDER)
    for action in ("release", "renew"):
        cmd = commands.add_parser(action)
        cmd.add_argument("token")
    args = parser.parse_args()
    try:
        if args.action in ("release", "renew"):
            (release if args.action == "release" else renew)(args.state_dir, args.token)
            return 0
        sdk = sdk_path(args.sdk)
        if args.action == "start":
            start(args, sdk)
        elif args.action == "status":
            ready = ready_devices(sdk)
            with database(args.state_dir) as db:
                rows = [dict(r) for r in db.execute("SELECT id,owner,preferred,serial,expires FROM requests ORDER BY id")]
            print(json.dumps({"ready": ready, "requests": rows}, indent=2))
        elif args.action == "acquire":
            print(json.dumps(acquire(args, sdk)))
        else:
            return run(args, sdk)
        return 0
    except (RuntimeError, OSError, sqlite3.Error, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
