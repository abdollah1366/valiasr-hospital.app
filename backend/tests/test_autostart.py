import sys

from scripts import autostart


def test_task_runs_this_script_at_boot_as_system():
    cmd = autostart.schtasks_create_command()
    tr = cmd[cmd.index("/TR") + 1]
    assert sys.executable in tr and tr.endswith('autostart.py" run')
    assert cmd[cmd.index("/SC") + 1] == "ONSTART" and cmd[cmd.index("/RU") + 1] == "SYSTEM"


def test_uvicorn_listens_on_all_interfaces():
    assert autostart.uvicorn_command()[-4:] == ["--host", "0.0.0.0", "--port", "8000"]


def test_run_restarts_crashed_server_and_logs(tmp_path, monkeypatch):
    monkeypatch.setattr(autostart, "LOG_DIR", tmp_path)
    monkeypatch.setattr(autostart, "RESTART_DELAY", 0)
    monkeypatch.setattr(
        autostart, "uvicorn_command", lambda: [sys.executable, "-c", "print('سلام از سرور'); exit(3)"]
    )
    autostart.run(max_restarts=1)
    log = (tmp_path / "server.log").read_text(encoding="utf-8")
    assert log.splitlines().count("سلام از سرور") == 2
    assert log.count("exited with code 3") == 2


def test_server_commands_refuse_outside_windows(monkeypatch):
    monkeypatch.setattr(autostart.sys, "platform", "linux")
    assert autostart.main(["autostart.py", "install"]) == 1


def test_task_has_no_time_limit():
    assert "-ExecutionTimeLimit ([TimeSpan]::Zero)" in autostart.SETTINGS_PS
    assert "-TaskName RaziServer" in autostart.SETTINGS_PS
