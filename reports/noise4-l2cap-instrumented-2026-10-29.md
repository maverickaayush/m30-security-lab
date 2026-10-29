# Instrumented Noise Air Buds Pro 4 link test

Date: 2026-10-29 IST. This follow-up used the user's owned Nothing Phone (2a)
as an independent HCI recorder while the already-connected Noise Air Buds
Pro 4 played music. The M30 was the test controller. All addresses, serials,
raw captures, bug reports and pairing material remain in the private rollback
directory outside Git.

## Answer

The M30's internal Linux SCSC `hci0` completed **100/100** Classic L2CAP
echo requests in about 60 seconds. The Nothing phone's Bluetooth HCI capture
shows its existing Classic link to the earbuds connected before the test and
**no Disconnection Complete event during the test or through two minutes of
post-test capture**. The user noticed no audio interruption. Thus this
workload did **not disconnect the existing Nothing-to-earbud link** in the
measured run. This is a negative result for this exact target and bounded
workload, not a proof about every Bluetooth device or higher workloads.

## Sequence and controls

1. On the Nothing Phone (2a), enable Bluetooth HCI snoop in Developer options,
   restart Bluetooth and reconnect the earbuds. A private preflight bug report
   contained a BTSnoop/H4 log with the target's Classic Connection Complete
   event at 18:45:54.114 UTC and subsequent ACL records. This confirmed that
   target-side link events were observable before the RF test.
2. With one USB-C cable, leave Nothing-side logging and music running while
   moving the cable to the M30. Record a **no-test baseline** from
   18:49:53.110 to 18:50:23.110 UTC.
3. Run the phone-local `echo-seq` helper against only the specified owned
   earbud: one worker, 100 scheduled requests, at least 600 ms between starts,
   60-second deadline, at most five seconds per request, and an abort after
   three consecutive failures. The supervisor separately bounded the helper,
   released Android ownership before Linux acquisition and restored Android
   afterward.
4. Reconnect the Nothing phone by USB without restarting Bluetooth. A private
   post-test bug report yielded an HCI capture covering 18:45:49.600 to
   18:53:43.353 UTC, with zero BTSnoop dropped or truncated records. The host
   and Nothing clocks agreed within the one-second `date` resolution at
   collection time.

| Observation | Baseline | Active interval |
|---|---|---|
| UTC window | 18:49:53.110–18:50:23.110 | 18:50:37.696–18:51:37.267 |
| Nothing-side target HCI state at both ends | Connected | Connected |
| Target HCI Disconnection Complete events | 0 | 0 |
| M30 echo requests and validated responses | 0 | 100 and 100 |
| M30 request failures | 0 | 0 |
| User audio observation | Music playing | No interruption noticed |

The target's HCI connection event occurred before both windows. No target
disconnection event appears anywhere in the capture through 18:53:43 UTC.
Within the active window, the Nothing HCI log also contains no controller
Reset command, Hardware Error event, or Connection Complete/Disconnection
Complete event for any link. These absence checks depend on the capture's
zero dropped/truncated record counters.
The BTSnoop parser also counted three target-handle ACL packets in the
baseline and zero in the active interval. That count is **not** an audio
continuity measure: the Nothing diagnostics reported A2DP offload enabled,
so host-visible ACL payloads need not represent the audio stream. The
connection/disconnection HCI events and the user's listening report support
the stated link result; they do not prove that every audio sample arrived.

After the M30 run, the helper logged `linux_released`, the supervisor logged
`android_owner_restored`, and a targeted read found the Android Bluetooth HAL
running, holding `/dev/scsc_h4_0`, manager state ON and SELinux Enforcing.
The APK, kernel and firmware were not changed. An earlier uninstrumented
100-request run also returned 100/100 echoes, but its link effect remains
inconclusive because the Nothing-side capture was unavailable for that run.
The earlier user-reported popup about connecting to Samsung M30 was not
reproduced or attributed to a particular protocol in this instrumented run.

## Private evidence and reproducibility

Private files under
`/home/aayush-yadav/m30-rollback-20261027/METADATA/`:

| File | Local SHA-256 |
|---|---|
| `noise4-instrumented-baseline-20261029.txt` | `1702bacc750cd4c2487e2dfc2f891179d3a60a2ee90a8e8a3f7ed7aaee20aa55` |
| `noise4-l2cap-instrumented-20261029-phone.txt` | `dc5f098a2c4502e15a61cfdc0bf5eedae55dda5b35acc40e72a8a4bc9333d1eb` |
| `nothing-hci-preflight-20261029.zip` | `ef4611aaecc4edf443dc1de33de6822d77089a4263cb96790b67dd7f88288d30` |
| `nothing-hci-preflight-20261029.btsnoop` | `16283ea4e7fb5d357254bc9ec9a83cfa31baac1f589e69cfff4ff350eda3f01c` |
| `nothing-hci-posttest-20261029.zip` | `4efccf23e473a6fd542d6aa759b7dce2ec93c367c902c7dc0a84c8c47bccd064` |
| `nothing-hci-posttest-20261029.btsnoop` | `61cdd09e79808e2d40546dddb03c3950dc77ef2f6a33c956c79fd8d655b9dd09` |
| `noise4-l2cap-instrumented-20261029-summary.json` | `6f92dbb11f4114409381bab72e7ca1494eb1aec07edcb6e17047a089b40b38a3` |

`scripts/summarize_bt_link.py` reads only HCI UART/H4 BTSnoop v1 records,
maps the selected target's Classic Connection Complete event to a controller
handle and checks Disconnection Complete events over a specified UTC window.
It emits no raw addresses or payloads. It rejects missing target mappings,
uncovered windows, dropped records and truncated records as inconclusive.
Four synthetic parser tests pass, including a known disconnect. The parser
does not decode A2DP or independently hear the music. AOSP describes the
Developer-options HCI snoop workflow and its privacy implications:
https://source.android.com/docs/core/connect/bluetooth/verifying_debugging

Reanalysis uses the private capture and the selected owned accessory address:

```sh
python3 scripts/summarize_bt_link.py PRIVATE_BTSNOOP \
  --target OWNED_CLASSIC_ADDRESS \
  --from-utc 2026-10-28T18:50:37.696Z \
  --to-utc 2026-10-28T18:51:37.267Z
python3 -m unittest tests.test_summarize_bt_link -v
```

This is a target-specific robustness observation, not a Bluetooth
deauthentication primitive or a vulnerability finding. More requests by
themselves would not establish a general third-party disconnect mechanism.
Noise's own product page states that Air Buds Pro 4 support **dual-device
pairing**. This makes coexistence of the Nothing link and an M30 connection
plausible, but the capture does not measure the earbuds' maximum simultaneous
connection count or prove how they arbitrate a third connection:
https://www.gonoise.com/products/noise-air-buds-pro-4-earbuds

The 2024 BlueDoS study found that some tested OnePlus devices disconnected on
an L2CAP socket connection **before any echo payload was sent**, while tested
BoAt devices behaved differently. It does not provide a universal request
threshold or evidence for this Noise firmware:
https://www.scitepress.org/PublishedPapers/2024/128457/

The next research step is to select a source-supported target-specific
mechanism or different controller capability, define the expected link-level
effect, and test it with this same independent capture method.
