"""Run the Razi server on a Windows VPS and start it automatically at boot.

Usage (from C:\\razi\\backend, PowerShell "Run as administrator"):
    .venv\\Scripts\\python scripts\\autostart.py install   # register + start now
    .venv\\Scripts\\python scripts\\autostart.py status
    .venv\\Scripts\\python scripts\\autostart.py stop
    .venv\\Scripts\\python scripts\\autostart.py start
    .venv\\Scripts\\python scripts\\autostart.py remove
    .venv\\Scripts\\python scripts\\autostart.py run       # what the task runs

"install" registers a Windows Scheduled Task that runs "run" at every boot
as SYSTEM (no login needed). "run" keeps uvicorn alive: if it exits, it is
started again after a short pause. Output goes to backend\\logs\\server.log.
"""

from __future__ import annotations

import subprocess
import sys
import time
from datetime import datetime
from logging.handlers import RotatingFileHandler
from pathlib import Path

TASK_NAME = "RaziServer"
BACKEND = Path(__file__).resolve().parents[1]
LOG_DIR = BACKEND / "logs"
HOST, PORT = "0.0.0.0", "8000"
RESTART_DELAY = 5  # seconds between restarts after a crash
BOOT_DELAY = "0001:00"  # mm:ss wait after boot so PostgreSQL is up first


def uvicorn_command() -> list[str]:
    return [sys.executable, "-m", "uvicorn", "app.main:app", "--host", HOST, "--port", PORT]


def schtasks_create_command() -> list[str]:
    action = f'"{sys.executable}" "{Path(__file__).resolve()}" run'
    return [
        "schtasks", "/Create", "/F",
        "/TN", TASK_NAME,
        "/TR", action,
        "/SC", "ONSTART",
        "/DELAY", BOOT_DELAY,
        "/RU", "SYSTEM",
        "/RL", "HIGHEST",
    ]  # fmt: skip


# schtasks creates tasks that Windows stops after 72 hours; lift that limit.
SETTINGS_PS = (
    "$s = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) "
    "-AllowStartIfOnBatteries -DontStopIfGoingOnBatteries "
    "-RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1); "
    f"Set-ScheduledTask -TaskName {TASK_NAME} -Settings $s | Out-Null"
)


def _log_handler() -> RotatingFileHandler:
    LOG_DIR.mkdir(exist_ok=True)
    # Keeps at most ~25 MB of logs (5 files x 5 MB).
    return RotatingFileHandler(LOG_DIR / "server.log", maxBytes=5_000_000, backupCount=4, encoding="utf-8")


def run(max_restarts: int | None = None) -> None:
    """Keep the server running. max_restarts exists only for tests."""
    handler = _log_handler()

    def note(msg: str) -> None:
        handler.stream.write(f"{datetime.now():%Y-%m-%d %H:%M:%S} [autostart] {msg}\n")
        handler.flush()

    starts = 0
    while True:
        note("starting: " + " ".join(uvicorn_command()))
        proc = subprocess.Popen(
            uvicorn_command(),
            cwd=BACKEND,  # settings read .env from the working directory
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        assert proc.stdout is not None
        for line in proc.stdout:
            handler.stream.write(line)
            handler.flush()
            if handler.stream.tell() >= handler.maxBytes:
                handler.doRollover()
        code = proc.wait()
        starts += 1
        note(f"server exited with code {code}; restarting in {RESTART_DELAY}s")
        if max_restarts is not None and starts > max_restarts:
            return
        time.sleep(RESTART_DELAY)


def _schtasks(*args: str) -> int:
    return subprocess.call(["schtasks", *args])


def main(argv: list[str]) -> int:
    cmd = argv[1] if len(argv) > 1 else ""
    if cmd == "run":
        run()
        return 0
    if sys.platform != "win32":
        print("This command is for the Windows server only.")
        return 1
    if cmd == "install":
        if not (BACKEND / ".env").exists():
            print("backend\\.env not found. Create it first (scripts\\make_staging_env.py).")
            return 1
        if subprocess.call(schtasks_create_command()) != 0:
            print("Could not create the task. Open PowerShell with 'Run as administrator'.")
            return 1
        if subprocess.call(["powershell", "-NoProfile", "-Command", SETTINGS_PS]) != 0:
            print("Warning: could not remove the 3-day time limit of the task.")
        _schtasks("/Run", "/TN", TASK_NAME)
        print(f"Done. The server starts at every boot. Logs: {LOG_DIR / 'server.log'}")
        return 0
    if cmd == "start":
        return _schtasks("/Run", "/TN", TASK_NAME)
    if cmd == "stop":
        _schtasks("/End", "/TN", TASK_NAME)
        # /End stops the watcher; make sure no uvicorn child keeps port 8000.
        return subprocess.call(
            ["powershell", "-NoProfile", "-Command",
             "Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | "
             "Where-Object { $_.CommandLine -like '*uvicorn*app.main:app*' } | "
             "ForEach-Object { Stop-Process -Id $_.ProcessId -Force }"]
        )  # fmt: skip
    if cmd == "status":
        return _schtasks("/Query", "/TN", TASK_NAME, "/V", "/FO", "LIST")
    if cmd == "remove":
        main([argv[0], "stop"])
        return _schtasks("/Delete", "/TN", TASK_NAME, "/F")
    print(__doc__)
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
