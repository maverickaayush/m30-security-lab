"""Offline Android/SCSC-to-Linux ownership model. Never operates on a device.

An actual implementation would need a privileged supervisor, a source-matched
kernel HCI driver, explicit service/SELinux policy, and verified recovery.
"""

from enum import Enum
from typing import Protocol


class State(str, Enum):
    ANDROID_OWNS = "android_owns"
    PREPARING = "preparing_handoff"
    RELEASED = "controller_released"
    RESEARCH_OWNS = "research_backend_owns"
    STOPPING = "stopping_research_backend"
    RESTORING = "restoring_android"
    RECOVERY_REQUIRED = "recovery_required"


class Operations(Protocol):
    def stop_android(self) -> None: ...
    def verify_released(self) -> bool: ...
    def start_research(self) -> None: ...
    def stop_research(self) -> None: ...
    def start_android(self) -> None: ...
    def verify_android(self) -> bool: ...


class Handoff:
    def __init__(self, ops: Operations):
        self.ops = ops
        self.state = State.ANDROID_OWNS
        self.history = [self.state]

    def _set(self, value: State) -> None:
        self.state = value
        self.history.append(value)

    def acquire(self) -> bool:
        if self.state != State.ANDROID_OWNS:
            raise RuntimeError("handoff can start only from verified Android ownership")
        self._set(State.PREPARING)
        try:
            self.ops.stop_android()
            if not self.ops.verify_released():
                raise RuntimeError("Android fd still open or release unverified")
            self._set(State.RELEASED)
            self.ops.start_research()
            self._set(State.RESEARCH_OWNS)
            return True
        except Exception:
            self.restore()
            return False

    def restore(self) -> bool:
        if self.state == State.ANDROID_OWNS:
            return True
        if self.state == State.RESEARCH_OWNS:
            self._set(State.STOPPING)
        try:
            # Idempotence is a required contract of the future supervisor.
            self.ops.stop_research()
            self._set(State.RESTORING)
            self.ops.start_android()
            if not self.ops.verify_android():
                raise RuntimeError("Android Bluetooth restoration unverified")
            self._set(State.ANDROID_OWNS)
            return True
        except Exception:
            self._set(State.RECOVERY_REQUIRED)
            return False
