"""Run: python tools/test_emulator_pool.py (no Android SDK required)."""

from concurrent.futures import ThreadPoolExecutor
from argparse import Namespace
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from unittest.mock import patch

from emulator_pool import SLOTS, claim, database, enqueue, release, renew, run


def check():
    with tempfile.TemporaryDirectory() as directory:
        state = Path(directory)
        first, second = SLOTS
        a = enqueue(state, "agent-a", None, 60, 60)
        b = enqueue(state, "agent-b", None, 60, 60)
        c = enqueue(state, "agent-c", None, 60, 60)
        # A later process polls first; FIFO must still assign the two earlier tickets.
        with ThreadPoolExecutor(max_workers=3) as executor:
            rows = list(executor.map(lambda token: claim(state, token, SLOTS), (c, b, a)))
        assert rows[0]["serial"] is None
        assert {r["serial"] for r in rows[1:]} == set(SLOTS)
        release(state, a)
        assert claim(state, c, SLOTS)["serial"] == first
        release(state, b)
        release(state, c)

        a = enqueue(state, "holder", first, 60, 60)
        assert claim(state, a, SLOTS)["serial"] == first
        b = enqueue(state, "preferred-waiter", first, 60, 60)
        c = enqueue(state, "flexible-waiter", None, 60, 60)
        assert claim(state, b, SLOTS)["serial"] is None
        assert claim(state, c, SLOTS)["serial"] == second
        with database(state) as db:
            db.execute("UPDATE requests SET expires=? WHERE token=?", (time.time() + 1, a))
        renew(state, a)
        assert claim(state, a, SLOTS)["expires"] > time.time() + 50
        with database(state) as db:
            db.execute("UPDATE requests SET expires=0 WHERE token=?", (a,))
        assert claim(state, b, SLOTS)["serial"] == first
        try:
            renew(state, a)
        except RuntimeError:
            pass
        else:
            raise AssertionError("Expired token was able to renew/reclaim another agent's emulator")
        release(state, b)
        release(state, c)

        # An offline slot must never be assigned; expired waiters must leave the queue.
        a = enqueue(state, "offline-waiter", first, 60, 60)
        assert claim(state, a, [second])["serial"] is None
        with database(state) as db:
            db.execute("UPDATE requests SET expires=0 WHERE token=?", (a,))
        assert claim(state, a, SLOTS) is None

        # Independent processes compete for the same two leases without duplicate owners.
        tokens = [enqueue(state, f"process-{i}", None, 60, 60) for i in range(4)]
        code = "from pathlib import Path; import sys,json; from emulator_pool import claim,SLOTS; print(json.dumps(claim(Path(sys.argv[1]),sys.argv[2],SLOTS)))"
        children = [subprocess.Popen([sys.executable, "-c", code, str(state), token],
                                    cwd=Path(__file__).parent, stdout=subprocess.PIPE, text=True)
                    for token in reversed(tokens)]
        results = [json.loads(child.communicate(timeout=20)[0]) for child in children]
        assert all(child.returncode == 0 for child in children)
        assert [row["serial"] for row in results[:2]] == [None, None]
        assert {row["serial"] for row in results[2:]} == set(SLOTS)
        for token in tokens:
            release(state, token)

        # Exercise the real command/heartbeat/cleanup path with just readiness replaced.
        args = Namespace(state_dir=state, owner="wrapper", serial=first, ttl=1, wait=5)
        with patch("emulator_pool.ready_devices", return_value=list(SLOTS)):
            args.command = [sys.executable, "-c", "import os,time; assert os.environ['ANDROID_SERIAL']=='emulator-5580'; time.sleep(2)"]
            assert run(args, state) == 0
            args.command = [sys.executable, "-c", "raise SystemExit(7)"]
            assert run(args, state) == 7
            args.command = [str(state / "missing-command")]
            try:
                run(args, state)
            except OSError:
                pass
            else:
                raise AssertionError("Missing child executable succeeded")
        with database(state) as db:
            assert db.execute("SELECT COUNT(*) FROM requests").fetchone()[0] == 0
    print("PASS: FIFO, exclusive leases, preferred/offline slots, renewal/expiry, cross-process contention, wrapper heartbeat/cleanup")


if __name__ == "__main__":
    check()
