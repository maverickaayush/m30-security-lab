# Bounded L2CAP echo observation against an owned earbud

Date: 2026-10-29 IST (phone log: 2026-10-28 UTC). The target was the user's
Noise Air Buds Pro 4, already playing music from a Nothing Phone (2a). The
earbud address is retained only in private run records. No kernel or APK was
changed for this experiment.

## Result

The M30's internal SCSC Linux `hci0` sent **100 of 100** individually bounded
Classic L2CAP echo requests in 59.856 seconds and validated 100 matching echo
responses. There were zero request failures. This is a successful phone-side
protocol exchange with the earbud. **An interruption of the existing
Nothing-to-earbud audio link was not established.** The user could not tell
whether music stuttered or the Bluetooth connection state changed during the
active run. The user reported a popup about connecting to the Samsung M30;
the screen and exact cause were not independently recorded. Treat it as a
reported UI event, not proof of a disconnect or pairing outcome.

## Method and evidence

The user confirmed music stayed uninterrupted during the preceding 30-second
no-test baseline. A phone-local root supervisor then stopped the Android
Bluetooth HAL, verified that the Samsung H4 descriptor was released, acquired
the SCSC Linux controller, and ran `echo-seq` with a single explicit target,
100 requests, a 60-second deadline and one worker. Requests were scheduled at
least 600 ms apart. Each request had a deadline no longer than five seconds;
three consecutive failures would have stopped the sequence. The supervisor
then brought Linux HCI down and restored the Android HAL. It never opened the
Samsung H4 transport concurrently with the HAL.

| Evidence | Observation |
|---|---|
| `noise4-l2cap-20261029-baseline.txt` | 30-second no-test interval; user subsequently reported stable music |
| `noise4-l2cap-20261029-100-phone.txt` | Run ID `noise4-l2cap-20261029-100`; 100 request indices, 100 20-byte sends, 100 validated 20-byte responses, 100 closed sockets, zero failures |
| Same phone log | First request 18:35:17.788 UTC; last response 18:36:17.644 UTC; Linux released and `android_owner_restored` logged |
| Post-run targeted phone check | SM-M305F; kernel `4.4.177-g09520657-dirty`; SELinux Enforcing; vendor HAL running and holding `/dev/scsc_h4_0`; Bluetooth manager ON |

The private files are under
`/home/aayush-yadav/m30-rollback-20261027/METADATA/` and are excluded from
Git. Their local SHA-256 values, respectively, are:

```text
3f822525f0bdbe7eaa179a508c5cfcf3886bf6a7b30a1efcc28658fddf24a840  baseline
6bbf7c6edd6cc9cef13cb170d7d29263728628fae3bc2405f98eca1b7011faca  phone run
```

The phone helper binary was hash checked both on the laptop and phone:
`03b4105c6b8515fe3958214d6ba2d87b2f9898259e36a1475b32be4e072796a0`.
The phone supervisor hash was
`e3a95cf6e9ee7962fe1453edc249c0741c43f05c699b10b1a7ddfcfc289b58f4`.
The private log gives a median request socket lifetime of 146.5 ms, 95th
ordered sample of 377 ms, and maximum of 1023 ms. These are local socket
lifetimes, not over-the-air packet round-trip or audio latency measurements.

The laptop's `dumpcap` Bluetooth-monitor interface was verified usable in a
separate permissions check. It monitors the **laptop's** controller, not the
Nothing phone's link to the earbuds, so it supplies no independent link-state
evidence for this run. There was no Nothing-side HCI snoop log, screen recording
or timestamped A2DP/connection trace. A subjective report that a popup
appeared cannot fill that gap. The active outcome is therefore
`target_responded; existing_link_effect=inconclusive`, not “deauth,” “no
effect,” or a measured disconnection.

## Reproduction and limits

`host/phone_linux_lab.c` implements `echo-seq`; `scripts/phone_handoff.sh`
validates the target and bounds before controller handoff. The private run
used the phone's hash-matched v5 helper and supervisor, with 100 requests over
60 seconds. `tests/test_phone_handoff.sh` checks invalid counts, pacing and
addresses before root/radio access. The host parser test passed with
AddressSanitizer and UndefinedBehaviorSanitizer. The Linux helper returned
exit 0, and the host ADB command returned exit 0. A later targeted read
confirmed Android Bluetooth ON and the HAL's H4 descriptor. This does not
replace a fresh Android BLE scan after this particular run.

The next useful experiment is an **instrumented** baseline and one bounded
repeat with Nothing-side HCI snoop or another timestamped connection-state
record. Record the A2DP link state before, during and after the M30 sequence;
stop immediately if the phone cannot restore Android Bluetooth. A higher
request count without target-side evidence would not answer the disruption
question. No additional RF run was performed after this result.

**Follow-up:** That instrumented repeat was completed later the same day.
The Nothing-side HCI capture showed no disconnection during the 100-request
run. See
[`noise4-l2cap-instrumented-2026-10-29.md`](noise4-l2cap-instrumented-2026-10-29.md).
