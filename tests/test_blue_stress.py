import argparse
import json
import unittest

from host.blue_stress import ExperimentError, build_request, run_experiment, validate_mac


class Completed:
    returncode = 0
    stdout = "ok"
    stderr = ""


def fake_runner(argv, timeout):
    return Completed()


class BlueStressTests(unittest.TestCase):
    def args(self, **overrides):
        values = dict(controller="hci0", target="AA:BB:CC:DD:EE:FF", mechanism="l2cap-echo",
                      rfcomm_channel=None, packet_size=16, requests=2, pace=0.1, duration=2.0,
                      request_timeout=1.0, execute=False, rfcomm_device=0)
        values.update(overrides)
        return argparse.Namespace(**values)

    def test_mac_normalizes(self):
        self.assertEqual(validate_mac("aa:bb:cc:dd:ee:ff"), "AA:BB:CC:DD:EE:FF")

    def test_request_is_structured_and_bounded(self):
        self.assertEqual(build_request(self.args()), ["l2ping", "-i", "hci0", "-c", "1", "-s", "16", "-t", "1", "AA:BB:CC:DD:EE:FF"])

    def test_dry_run_does_not_send_target_requests(self):
        calls = []
        result = run_experiment(self.args(), lambda argv, timeout: calls.append(argv) or Completed())
        self.assertEqual(result["status"], "ready_dry_run")
        self.assertEqual(calls, [["hciconfig", "-a", "hci0"]])

    def test_execute_has_one_baseline_then_bounded_operations(self):
        calls = []
        result = run_experiment(self.args(execute=True), lambda argv, timeout: calls.append(argv) or Completed())
        self.assertEqual(result["status"], "completed")
        self.assertEqual(len(result["operations"]), 2)
        self.assertEqual(len(calls), 5)  # adapter, target check, baseline, two requests
        self.assertTrue(all("-f" not in call for call in calls))

    def test_rfcomm_requires_explicit_channel(self):
        with self.assertRaises(ExperimentError):
            build_request(self.args(mechanism="rfcomm"))

    def test_rfcomm_selects_and_releases_explicit_local_device(self):
        args = self.args(mechanism="rfcomm", rfcomm_channel=7)
        self.assertEqual(build_request(args), ["rfcomm", "-i", "hci0", "connect", "0", "AA:BB:CC:DD:EE:FF", "7"])


if __name__ == "__main__":
    unittest.main()
