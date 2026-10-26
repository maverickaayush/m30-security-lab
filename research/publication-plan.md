# Three ranked research projects and publication gates

Ranked 2026-10-26 by feasible measurement, original artifact, usefulness and
uncertainty. All work remains in the owned lab. No paper, PR, disclosure or
external contact has been submitted.

## 1. Callback-to-air behavior of a Samsung SCSC BLE advertiser

**Question/hypothesis.** How do Android advertising callbacks, vendor HCI
events and independently received advertisements relate as payloads rotate?
Hypothesis: controller scheduling and cache behavior create measurable gaps;
callback success alone overstates RF evidence. Android APIs and BlueZ receivers
are established prior art; the device-specific three-layer correlation is the
unanswered part.

**Artifacts/equipment.** Existing app, laptop BlueZ receiver, optional
timestamped BLE sniffer, and Android HCI snoop if enabled. No flash.
**Minimal experiment.** 30 s one-set LAB rotation at 1 s, low power, repeated
with a static-advertisement control and quiet-channel baseline. The prior run
already showed callbacks 0–27 but BlueZ cache only `[0,2,4,17]`; that is a
measurement-design warning, not loss. Independently timestamp true scan events
and preserve missing-event semantics.
**Metrics/repeatability.** Per-trial callback count, receiver event count,
payload IDs, interval distribution and confidence intervals across at least
20 randomized trials, clock offset, channel and receiver settings. Stop on
thermal/battery concern, scan errors, fixture interference or deadline.
**Deliverable/route.** Open-source harness and reproducible technical article;
a workshop paper only if a nontrivial mechanism and rigorous controls emerge.
**Biggest uncertainty.** Whether available receiver/firmware provides genuine
per-advertisement timestamps. **First action:** obtain a timestamped scan-event
capture from the existing laptop or borrow a suitable sniffer, then correlate
one run with phone HCI snoop. An all-cache dataset would falsify packet-loss
claims and make the result a tooling note only.

## 2. SCSC UDI/FAPI visibility and limits on the M30

**Question/hypothesis.** Does `/dev/s5n2560udi0` expose useful own-traffic
802.11 management data, and how incomplete are its frames/metadata compared
with an external monitor receiver? Family `udi.c` already documents FAPI
logging and drop counters; novelty requires a validated decoder and measured
completeness, not rediscovery of the node.

**Artifacts/equipment.** Exact source if obtainable, live node under enforcing
SELinux, a privacy-safe isolated Wi-Fi AP/traffic fixture, and external monitor
sniffer for controls. **Minimal experiment:** first map the family `udi_msg_t`
and ioctl contract offline; then, only for self-generated traffic, run a
bounded read and decode direction, signal ID, descriptor, timestamp, truncation
and drop events. Compare with a second receiver and same known frames.
**Metrics/repeatability.** Decode success and coverage by frame class,
metadata fields present/missing, per-run drops, observer agreement, at least
10 trials with idle and own-traffic controls. Stop if logs contain unrelated
traffic, UDI queues approach caps, or driver/service instability occurs.
**Deliverable/route.** Public parser for synthetic/redacted records and a
technical article; a tool/driver contribution if a verified bug and maintainer
path are established. **Biggest uncertainty:** family source may differ from
installed driver and UDI may expose only own data. **First action:** derive
the exact `udi_msg_t` layout and read-only mode/filter plan without opening a
long-lived capture. A result with no 802.11 metadata would still be a useful
negative measurement, but too weak alone for a conference paper.

## 3. Reproducible SCSC BlueZ bridge and ownership audit

**Question/hypothesis.** Can the optional family bridge be built for this
exact stock kernel and handed exclusively between Android and Linux without
state corruption? The family bridge exists already; original work would be a
matched build, verified handoff, tests and an upstreamable defect fix.

**Artifacts/equipment.** Exact Samsung release, build toolchain, DT/boot and
module ABI map, separate storage/compute, verified stock restore procedure.
**Minimal experiment:** reproduce the *unchanged* running kernel offline,
compare config/ABI/boot artifacts, build bridge enabled in a non-flashed
image, run static/compile checks including the post-free SKB patch. Only a
later phase with a tested recovery strategy could flash and test `hci0`.
**Controls/metrics.** Unmodified build control, compile warnings, module/API
differences, controller discovery, Android-to-BlueZ handoff and restore time.
Repeat on at least two clean boots before claiming portability. Stop if exact
source cannot be matched, recovery cannot be proven, or the HAL retains H4.
**Deliverable/route.** Reproducible port notes and patch; upstream contribution
only to an active maintained tree. **Biggest uncertainty:** exact source and
device recovery. **First action:** obtain Samsung's exact source package and
record its hashes. This is **not** a ready vulnerability or a path to generic
third-party disconnect.

## Route and novelty threshold

- **Technical article:** can report this phone's negative/positive boundary
  map, source-backed `smxf` parser and repeatable fixture results now, clearly
  labeled as single-device observations. More trials make it stronger.
- **Upstream contribution:** needs a maintained target, a minimal patch,
  compile/runtime tests and a clear base revision. The
  [Linux patch guide](https://docs.kernel.org/process/submitting-patches.html)
  and [checklist](https://docs.kernel.org/process/submit-checklist.html) require
  more than `git apply --check`. This family mirror's disabled bridge patch is
  not yet ready for upstream submission.
- **Workshop/conference:** original mechanism, controls, comparative data and
  reproducible artifact are required. The closed
  [WOOT '26 call](https://www.usenix.org/conference/woot26/call-for-papers)
  accepted wireless/mobile offensive research; its 2026 deadlines have passed.
  [USENIX Security '27 Cycle 2](https://www.usenix.org/conference/usenixsecurity27/call-for-papers)
  currently lists mandatory registration 2027-01-19 and submission 2027-01-26,
  13 body pages and an Open Science appendix. That venue would require far
  stronger new mechanism/evaluation than this audit; dates must be rechecked
  before any submission. We claim no acceptance odds.
- **Vulnerability disclosure/CVE:** requires an exact affected product/version,
  reproducible violation of a security property, impact and coordinated vendor
  route. [CVE CNA rules](https://www.cve.org/ResourcesSupport/AllResources/CNARules)
  define vulnerabilities by exploitable confidentiality/integrity/availability
  impact; a disabled option or failed feature is not one. For a flaw in a
  Bluetooth **specification**, the [SIG reporting page](https://www.bluetooth.com/learn-about-bluetooth/key-attributes/bluetooth-security/reporting-security/)
  gives its process. Product flaws go to the product vendor/appropriate CNA.
  No candidate has been validated here.
- **Bounty:** requires a relevant, currently in-scope program and eligibility
  for the specific target. None was identified for the unnamed earbuds, and
  no production Noise service was tested. We claim no payout or bounty path.

**Verdict.** The strongest near-term public contribution is a reproducible,
multi-layer SCSC advertising measurement with an honest distinction between
framework callbacks, controller events and independent RF reception. The
firmware parser and UDI map are useful supporting artifacts. An exact-source
BlueZ port is a longer, higher-risk project and would not itself enable a
universal Bluetooth “deauth” operation.
