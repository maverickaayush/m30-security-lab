#!/usr/bin/env python3
"""Bounded, explicit Bluetooth Classic responsiveness experiment.

This is a laptop-side research harness.  It is deliberately not a generic
disconnect tool: it sends individual L2CAP echo requests or RFCOMM connection
attempts only to one caller-selected, owned target.  It never uses a shell,
never starts a background flood, and reports local command results separately
from any observation of the target's existing link.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Callable, Sequence


MAC = re.compile(r"^[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}$")
HCI = re.compile(r"^hci[0-9]+$")
MAX_REQUESTS = 20
MAX_DURATION = 30.0
MAX_TIMEOUT = 5.0


class ExperimentError(ValueError):
    pass


def validate_mac(value: str) -> str:
    if not MAC.fullmatch(value):
        raise ExperimentError("target must be a Bluetooth address such as AA:BB:CC:DD:EE:FF")
    return value.upper()


def validate_controller(value: str) -> str:
    if not HCI.fullmatch(value):
        raise ExperimentError("controller must be an HCI name such as hci0")
    return value


def command_result(argv: Sequence[str], timeout: float, runner: Callable[..., Any] | None = None) -> dict[str, Any]:
    """Run one structured command in its own process group."""
    if not argv or any("\x00" in part for part in argv):
        raise ExperimentError("invalid empty command or NUL byte")
    started = time.monotonic()
    proc: Any = None
    try:
        if runner is not None:
            completed = runner(list(argv), timeout=timeout)
            return {
                "argv": list(argv),
                "returncode": getattr(completed, "returncode", None),
                "stdout": getattr(completed, "stdout", "") or "",
                "stderr": getattr(completed, "stderr", "") or "",
                "timed_out": False,
                "elapsed_ms": round((time.monotonic() - started) * 1000),
            }
        proc = subprocess.Popen(
            list(argv),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            start_new_session=True,
            close_fds=True,
        )
        try:
            out, err = proc.communicate(timeout=timeout)
            timed_out = False
        except subprocess.TimeoutExpired as exc:
            timed_out = True
            out = (exc.stdout or "") if isinstance(exc.stdout, str) else (exc.stdout or b"").decode(errors="replace")
            err = (exc.stderr or "") if isinstance(exc.stderr, str) else (exc.stderr or b"").decode(errors="replace")
            try:
                os.killpg(proc.pid, signal.SIGTERM)
                proc.communicate(timeout=0.5)
            except Exception:
                try:
                    os.killpg(proc.pid, signal.SIGKILL)
                except Exception:
                    pass
                proc.communicate()
        return {
            "argv": list(argv),
            "returncode": proc.returncode,
            "stdout": out,
            "stderr": err,
            "timed_out": timed_out,
            "elapsed_ms": round((time.monotonic() - started) * 1000),
        }
    except FileNotFoundError as exc:
        return {
            "argv": list(argv),
            "returncode": None,
            "stdout": "",
            "stderr": str(exc),
            "timed_out": False,
            "missing": True,
            "elapsed_ms": round((time.monotonic() - started) * 1000),
        }


def adapter_check(controller: str, timeout: float, runner: Callable[..., Any] | None = None) -> dict[str, Any]:
    """Read adapter state without changing power or ownership."""
    return command_result(["hciconfig", "-a", controller], timeout, runner)


def target_check(target: str, timeout: float, runner: Callable[..., Any] | None = None) -> dict[str, Any]:
    """Read the daemon's cached target identity; this is not a disconnect."""
    return command_result(["bluetoothctl", "info", target], timeout, runner)


def service_check(target: str, timeout: float, runner: Callable[..., Any] | None = None) -> dict[str, Any]:
    """Discover Classic services before an RFCOMM experiment."""
    return command_result(["sdptool", "browse", target], timeout, runner)


def build_request(args: argparse.Namespace) -> list[str]:
    if args.mechanism == "l2cap-echo":
        # One echo request.  -f from blue-deauth is intentionally not used.
        return [
            "l2ping", "-i", args.controller, "-c", "1", "-s",
            str(args.packet_size), "-t", str(max(1, int(args.request_timeout))), args.target,
        ]
    if not args.rfcomm_channel:
        raise ExperimentError("RFCOMM testing requires an observed service channel")
    # rfcomm's -i option is supported by the BlueZ util shipped on this host;
    # refusing the operation is safer than silently falling back to hci0.
    return [
        "rfcomm", "-i", args.controller, "connect", str(args.rfcomm_device),
        args.target, str(args.rfcomm_channel),
    ]


def rfcomm_release(args: argparse.Namespace, timeout: float,
                   runner: Callable[..., Any] | None = None) -> dict[str, Any]:
    """Release the explicit local RFCOMM device after each attempt."""
    return command_result(
        ["rfcomm", "-i", args.controller, "release", str(args.rfcomm_device)],
        timeout,
        runner,
    )


def run_experiment(args: argparse.Namespace, runner: Callable[..., Any] | None = None) -> dict[str, Any]:
    controller = validate_controller(args.controller)
    target = validate_mac(args.target)
    if args.requests < 1 or args.requests > MAX_REQUESTS:
        raise ExperimentError(f"requests must be between 1 and {MAX_REQUESTS}")
    if args.duration <= 0 or args.duration > MAX_DURATION:
        raise ExperimentError(f"duration must be between 0 and {MAX_DURATION} seconds")
    if args.request_timeout <= 0 or args.request_timeout > MAX_TIMEOUT:
        raise ExperimentError(f"request timeout must be between 0 and {MAX_TIMEOUT} seconds")
    if args.pace < 0.1 or args.pace > 10:
        raise ExperimentError("pace must be between 0.1 and 10 seconds")
    if args.mechanism == "l2cap-echo" and not 8 <= args.packet_size <= 64:
        raise ExperimentError("packet size must be between 8 and 64 bytes")
    if args.mechanism == "rfcomm" and (args.rfcomm_channel is None or not 1 <= args.rfcomm_channel <= 30):
        raise ExperimentError("RFCOMM channel must be between 1 and 30")
    if args.mechanism == "rfcomm" and not 0 <= args.rfcomm_device <= 255:
        raise ExperimentError("RFCOMM device must be between 0 and 255")

    adapter = adapter_check(controller, min(args.request_timeout, 2.0), runner)
    result: dict[str, Any] = {
        "schema": "m30-security-lab/blue-stress/v1",
        "status": "not_started",
        "execution_location": "laptop",
        "controller": controller,
        "target": target,
        "mechanism": args.mechanism,
        "limits": {"requests": args.requests, "duration_s": args.duration, "pace_s": args.pace,
                    "request_timeout_s": args.request_timeout},
        "adapter_check": adapter,
        "baseline": None,
        "operations": [],
        "target_effect": "not_measured",
        "notes": [
            "This tests protocol responsiveness on one selected Bluetooth Classic target.",
            "A command return value does not prove that an existing target-to-target link changed.",
            "BLE-only peripherals do not satisfy the L2CAP echo/RFCOMM prerequisites.",
        ],
    }
    if adapter.get("missing") or adapter.get("returncode") not in (0, None):
        result["status"] = "controller_unavailable"
        return result
    if not args.execute:
        result["status"] = "ready_dry_run"
        result["planned_request"] = build_request(args)
        return result

    stop = False
    previous_handlers: dict[int, Any] = {}

    def request_stop(signum: int, _frame: Any) -> None:
        nonlocal stop
        stop = True

    for sig in (signal.SIGINT, signal.SIGTERM):
        previous_handlers[sig] = signal.getsignal(sig)
        signal.signal(sig, request_stop)
    try:
        request = build_request(args)
        result["planned_request"] = request
        result["target_check"] = target_check(target, min(args.request_timeout, 2.0), runner)
        if args.mechanism == "rfcomm":
            result["service_check"] = service_check(target, min(args.request_timeout, 2.0), runner)
            service_text = (result["service_check"].get("stdout", "") or "").lower()
            if result["service_check"].get("missing") or result["service_check"].get("returncode") != 0 or "rfcomm" not in service_text:
                result["status"] = "target_service_unavailable"
                return result
        result["started_at_monotonic"] = time.monotonic()
        baseline = command_result(request, args.request_timeout, runner)
        if args.mechanism == "rfcomm":
            baseline["cleanup"] = rfcomm_release(args, min(args.request_timeout, 2.0), runner)
        result["baseline"] = baseline
        if baseline.get("timed_out") or baseline.get("returncode") != 0:
            result["status"] = "baseline_failed"
            return result
        deadline = time.monotonic() + args.duration
        for index in range(args.requests):
            if stop:
                result["status"] = "stopped"
                break
            if time.monotonic() >= deadline:
                result["status"] = "deadline"
                break
            operation = command_result(request, min(args.request_timeout, max(0.1, deadline - time.monotonic())), runner)
            if args.mechanism == "rfcomm":
                operation["cleanup"] = rfcomm_release(args, min(args.request_timeout, 2.0, max(0.1, deadline - time.monotonic())), runner)
            operation["index"] = index + 1
            result["operations"].append(operation)
            if time.monotonic() + args.pace >= deadline:
                break
            if stop:
                result["status"] = "stopped"
                break
            time.sleep(min(args.pace, max(0.0, deadline - time.monotonic())))
        else:
            result["status"] = "completed"
        if result["status"] == "not_started":
            result["status"] = "completed"
        result["target_effect"] = "not_measured"
        return result
    finally:
        for sig, handler in previous_handlers.items():
            signal.signal(sig, handler)


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Bounded owned-target Bluetooth Classic responsiveness test")
    p.add_argument("--controller", default="hci0")
    p.add_argument("--target", required=True)
    p.add_argument("--mechanism", choices=("l2cap-echo", "rfcomm"), default="l2cap-echo")
    p.add_argument("--rfcomm-channel", type=int)
    p.add_argument("--rfcomm-device", type=int, default=0,
                   help="local RFCOMM device number; it is released after each attempt")
    p.add_argument("--packet-size", type=int, default=16)
    p.add_argument("--requests", type=int, default=3)
    p.add_argument("--pace", type=float, default=1.0)
    p.add_argument("--duration", type=float, default=10.0)
    p.add_argument("--request-timeout", type=float, default=2.0)
    p.add_argument("--execute", action="store_true", help="send the bounded test after baseline; omit for dry-run")
    return p


def main(argv: Sequence[str] | None = None) -> int:
    try:
        result = run_experiment(parser().parse_args(argv))
    except (ExperimentError, OSError) as exc:
        print(json.dumps({"schema": "m30-security-lab/blue-stress/v1", "status": "invalid", "error": str(exc)}))
        return 2
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["status"] in {"ready_dry_run", "completed", "stopped", "deadline"} else 1


if __name__ == "__main__":
    sys.exit(main())
