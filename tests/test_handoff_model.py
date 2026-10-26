import unittest

from scripts.handoff_model import Handoff, State


class FakeOperations:
    def __init__(self, fail=""):
        self.fail = fail
        self.events = []

    def _call(self, name):
        self.events.append(name)
        if self.fail == name:
            raise RuntimeError(name)

    def stop_android(self): self._call("stop_android")
    def verify_released(self):
        self.events.append("verify_released")
        return self.fail != "verify_released"
    def start_research(self): self._call("start_research")
    def stop_research(self): self._call("stop_research")
    def start_android(self): self._call("start_android")
    def verify_android(self):
        self.events.append("verify_android")
        return self.fail != "verify_android"


class HandoffTest(unittest.TestCase):
    def test_successful_sequence_restores_android(self):
        ops = FakeOperations()
        handoff = Handoff(ops)
        self.assertTrue(handoff.acquire())
        self.assertEqual(handoff.state, State.RESEARCH_OWNS)
        self.assertTrue(handoff.restore())
        self.assertEqual(handoff.state, State.ANDROID_OWNS)
        self.assertEqual(ops.events, ["stop_android", "verify_released", "start_research",
                                      "stop_research", "start_android", "verify_android"])

    def test_unverified_release_never_starts_research(self):
        ops = FakeOperations("verify_released")
        handoff = Handoff(ops)
        self.assertFalse(handoff.acquire())
        self.assertNotIn("start_research", ops.events)
        self.assertEqual(handoff.state, State.ANDROID_OWNS)

    def test_failed_restore_requires_recovery(self):
        ops = FakeOperations("verify_android")
        handoff = Handoff(ops)
        self.assertTrue(handoff.acquire())
        self.assertFalse(handoff.restore())
        self.assertEqual(handoff.state, State.RECOVERY_REQUIRED)

    def test_research_start_failure_still_restores(self):
        ops = FakeOperations("start_research")
        handoff = Handoff(ops)
        self.assertFalse(handoff.acquire())
        self.assertEqual(handoff.state, State.ANDROID_OWNS)


if __name__ == "__main__":
    unittest.main()
