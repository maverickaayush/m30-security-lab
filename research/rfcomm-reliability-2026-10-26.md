# RFCOMM reliability follow-on

Checked 2026-10-26. This is a cooperating laptop fixture experiment, not a
disruption test. The phone remains the only owner of its Bluetooth controller.

## What changed

The phone backend now records a per-run UUID, the `SystemClock.elapsedRealtime`
time base, phase transitions (`socket_create`, `connect`, `exchange`,
`completed`/`exception`, and close), relative timestamps, timeout state and a
redacted exception message. The Classic discovery and SDP UI logs now record
start, finish, target selection, SDP request and SDP completion. The fixture
adds a correlation ID, monotonic timestamps, and a `fixture_ready` event after
the explicit BlueZ profile and SDP record are installed.

The protocol is unchanged: the fixture accepts exactly `M30LAB/1 PING\n` and
returns `M30LAB/1 PONG\n`. The phone still uses one worker, one baseline,
at most 20 requests, a 30-second run deadline and a 12-second per-operation
deadline.

## Prior observations and confounders

Two earlier baseline attempts failed before BlueZ received `NewConnection`:
one timed out at about 5.1 seconds and one returned `IOException` at about 6.4
seconds with Android logging socket channel `-1`. The successful retry changed
the SDP record, refreshed SDP, changed the timeout, and changed laptop trust
state before it passed. Those conditions were changed together, so no one of
them is an isolated fix. The later bounded exchange (baseline plus three) and
cancellation run (baseline plus nine) passed with matching independent fixture
logs. This is an intermittent setup result, not a resolved root cause.

## Controlled matrix

| Run | Fixture registration before discovery | SDP refresh | Bond/trust | Discovery active at connect | Deadline | Result |
| --- | --- | --- | --- | --- | --- | --- |
| A1/A2 (historical) | not independently timestamped | uncertain | changed later | unknown | 5–6 s | baseline failed before fixture connection |
| A3 (historical success) | yes, but no ready event | yes | changed | stopped before test | 12 s | exchange passed |
| B (current) | explicit `fixture_ready` | fresh request | phone bond retained; laptop pairing may be required | app stops discovery before SDP/connect | 12 s / 30 s | awaiting phone run |

The current run uses fixture correlation ID `rfcomm-reliability-20261026-a`;
its raw JSONL is ignored under `evidence/rfcomm/`. It will stop after 600 s and
restore the laptop's initial discoverability and pairability. A complete B run
will vary one factor per repetition: (1) fresh SDP versus cached UUID, (2)
trusted bond versus newly paired bond, (3) discovery stopped versus discovery
still active, and (4) 12-second versus shorter bounded deadline. No unlimited
retry is allowed.

## Interpretation rules

* `fixture_ready` proves registration and SDP setup, not a phone connection.
* A phone `socket_created`/`connect` phase without a laptop `connected` event is
  a setup or transport failure; it is not target disconnection.
* A laptop `received` plus `response` event and matching phone `exchange_ok`
  establish a cooperating RFCOMM exchange.
* Cancellation is successful only when the phone reports stop/close and the
  fixture reports a matching disconnect with no socket left in its set.

## Reproduction

```sh
cd ~/m30-security-lab
python3 host/rfcomm_fixture.py --self-test
timeout 600s python3 host/rfcomm_fixture.py \
  --run-id rfcomm-reliability-20261026-a \
  --log evidence/rfcomm/reliability-matrix-a.jsonl --max-seconds 600
```

On the phone, pair with the named laptop, run Classic discovery, select the
laptop, refresh its Classic services, select the returned LAB UUID, and start
the bounded test. Save the JSON and correlate `run_id`, phase timestamps and
fixture events. The fixture must be stopped with SIGINT after the runs so its
`finally` block restores adapter settings.

## Current conclusion

The Android RFCOMM implementation is live and reliable for the previously
successful cooperating fixture runs, but the two early baseline failures are
not isolated. The next evidence needed is one completed matrix run with the
new phase logs. No HCI handoff, raw HCI operation, flooding, or third-party
link test belongs in this experiment.
