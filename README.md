# Network-Analyzer

An Android app that turns a phone plus a USB‑C‑to‑Ethernet adapter (e.g. Anker,
Realtek RTL8153/8156 or ASIX AX88179 chipset) into a portable wired + wireless
network analyzer.

Plug the adapter into a live Ethernet drop, and the app inspects the link,
discovers what's on the network, measures speed and quality, and analyzes
nearby WiFi — all from the phone.

---

## Scope

### In scope

| Area | Capability |
|------|-----------|
| **Link info** | Interface speed (10/100/1000), duplex, MTU, MAC; IP/subnet/gateway/DNS; DHCP lease; public IP + ISP/ASN |
| **Discovery** | ARP/ping sweep of the subnet; per-host IP, MAC, vendor (OUI), hostname (rDNS/mDNS/NetBIOS) |
| **Port scan** | TCP-connect scan per host with service identification |
| **Statistics** | Live RX/TX throughput, packet counts, errors, drops |
| **Speed test** | Throughput (HTTP or iperf3), latency, jitter, packet loss, DNS resolution time |
| **WiFi analysis** | Nearby APs (SSID, BSSID, RSSI, channel, band, width, security), channel congestion, current link quality |

### Out of scope (physical-layer cable diagnostics)

Deliberately **tabled** — these require Time Domain Reflectometry (TDR) at the
PHY level, whose vendor registers aren't exposed to unrooted Android apps by the
USB-Ethernet drivers. They need dedicated cable-tester hardware.

- Cable length estimation (needs nanosecond timing; impossible over USB + Android
  userspace, where scheduling/USB jitter is milliseconds — ~10,000× too coarse)
- Per-pin wiremap: open / short / miswired / swapped-pair fault location
  (1000BASE-T PHYs auto-correct MDIX, pair-swap, polarity and skew, silently
  hiding exactly these faults; per-pair diagnostic registers aren't exposed)

The app should surface these as "requires dedicated hardware — out of scope."

**Partial exception — cable _qualification_ (see M7).** What two cooperating
endpoints _can_ do is a pass/fail quality check under real traffic (confirm a
clean 4-pair gigabit link, push bidirectional throughput, watch error/retrain
counters). That's a qualifier, not a wiremap tester or a certifier — no length,
no per-pin fault location.

---

## Key constraints (design around these)

- **Target unrooted Android first.** This maximizes install base but means:
  - TCP-connect scans only (no raw SYN scans)
  - No raw ARP frames — discovery leans on ping sweep + connect probes
  - Root can be an optional enhanced mode later (raw sockets, SYN, faster ARP).
- **Interface binding is critical.** The phone keeps WiFi/cellular up alongside
  the USB link. Sockets must be bound to the USB Ethernet interface
  (`bindProcessToNetwork()` / `Network.bindSocket()`) or tests silently run over
  the wrong interface.
- **Adapter recognition.** Realtek and ASIX chipsets enumerate reliably on modern
  Android; off-brand chips may not. Detect and warn on unsupported adapters.
- **WiFi scan throttling (Android 9+).** Background scans are rate-limited
  (~4 scans / 2 min). Acceptable for a foreground analyzer; don't design around
  rapid rescans.

---

## Architecture (proposed)

```
┌─────────────────────────────────────────────┐
│                    UI (Kotlin)               │
│  Dashboard · Hosts · Speed · WiFi · Details  │
├─────────────────────────────────────────────┤
│                 Domain / services            │
│  Discovery · PortScan · Stats · SpeedTest    │
│  WifiScan · InterfaceManager                 │
├─────────────────────────────────────────────┤
│   Platform layer                             │
│   ConnectivityManager · /sys/class/net       │
│   WifiManager · bound sockets                │
└─────────────────────────────────────────────┘
```

- **Language/stack:** Kotlin + Android SDK.
- **Networking:** pure-Kotlin/Java for MVP (sockets, coroutines). Optional NDK
  path later to bundle `iperf3` for higher-fidelity throughput.
- **Link stats source:** `/sys/class/net/<iface>/statistics/`.
- **Concurrency:** Kotlin coroutines; parallelized sweeps with a bounded pool.

---

## MVP milestones

**M1 — Interface & link info** ✅ _implemented_
Detect the USB Ethernet interface, show link speed/duplex/MAC and IP config,
bind app traffic to it. Warn on unsupported/absent adapter.
_Unrooted-only for v1 (decided): public ConnectivityManager APIs + world-readable
sysfs; no raw sockets or privileged calls._

**M2 — Statistics** ✅ _implemented_
Live RX/TX throughput meter + rolling sparkline + packet/error/dropped counters,
sampled once per second from `/sys/class/net/<iface>/statistics/`. Surfaced on a
second "Statistics" tab.

**M3 — Discovery & inventory** ✅ _implemented_
On-demand subnet sweep on a third "Hosts" tab: TCP-connect probes across the
wired subnet (connect success *or* refusal proves liveness), then read
`/proc/net/arp` for MACs — so hosts that drop probes are still found via the ARP
entry their SYN triggered. MAC → vendor via an OUI starter set; best-effort
reverse-DNS hostnames; open ports captured to seed M4. All sockets bound to the
Ethernet network. Subnets larger than /22 are refused as too big to sweep.

**M4 — Port scan** ✅ _implemented_
Per-host TCP-connect scan on a "Ports" tab: classifies open/closed/filtered from
connect outcomes, labels services from a well-known-port catalog, and passively
grabs any greeting banner (SSH/FTP/SMTP…) without sending a payload. Configurable
ports spec ("22,80,443" or "1-1024", capped at 4096) with Common/1-1024/Web
quick-fills. "Scan ports" on a discovered host hands the IP straight to this tab.

**M5 — Speed & quality** ✅ _implemented_
A "Speed" tab that runs three tests in sequence over the wired link, all bound to
the Ethernet `Network`: TCP-ping latency/jitter/loss (ICMP isn't reliable
unrooted, so RTT is timed from TCP handshakes — a refusal counts too), DNS
resolution timing via the Ethernet resolver, and HTTP download throughput
(Cloudflare endpoint by default, editable; time/size-capped). iperf3 upload
remains a stretch (needs a server → folds into M7).

**M6 — WiFi analysis** ✅ _implemented_
A "WiFi" tab using the phone's own radio (independent of the USB link): nearby
APs with SSID/BSSID/RSSI+bars/channel/band/width/security, a collapsible
channel-congestion view, and a collapsible current-association card. The
Connected card also hosts a **live signal meter** — a toggle that polls the
connected network's RSSI (via `connectionInfo`, which isn't scan-throttled) at a
configurable interval (0.5–5 s) and plots a sparkline. Gated behind the runtime
scan permission — `ACCESS_FINE_LOCATION` plus `NEARBY_WIFI_DEVICES` on API 33+
(without `neverForLocation`, which returned empty results on Android 16) — with
a broadcast receiver + result polling and version-aware empty-state messages.

**M7 — Two-phone cable qualification** ✅ _implemented (stretch; builds on M2 + M5)_
A "Cable" tab with Server/Client roles. One phone listens; the other runs a timed
download then upload over the cable under test, measures throughput, and reads
error/drop counter deltas on *both* ends (the server returns its own in the
upload ack). Verdict = PASS / MARGINAL / FAULT from link rate + error counters
(throughput is shown but never fails a run — it's capped by USB generation, not
the cable). Needs IPv4 on both ends (e.g. via a switch/router); plain TCP bound
to the Ethernet interface, unrooted.

_Delivers a **qualifier**, not a wiremap or certifier:_
- ✅ Confirms all 4 pairs carry a clean gigabit link (gigabit needs all 4)
- ✅ Flags 2-pair-only faults (caps at 100 Mbps → a pair is open/broken)
- ✅ Measures real bidirectional throughput end-to-end (controlled far end)
- ✅ Flags marginal / damaged / over-length runs via errors + retrains under load
- ❌ No length in meters (nanosecond timing impossible over USB + userspace)
- ❌ No per-pin fault location (PHY auto-corrects MDIX/pair-swap/polarity/skew)

Verdict UI must state these limits plainly so it's never mistaken for a
wiremap tester. Requires a lightweight pairing/handshake between the two app
instances over the wired link.

---

## Building

Android Studio (Koala or newer) — open the project root and run the `app`
configuration on a device. Or from the CLI once the Gradle wrapper jar is
present (`gradle wrapper` or Android Studio generates it):

```
./gradlew :app:assembleDebug     # build the APK
./gradlew :app:installDebug      # install on a connected device
```

**Requirements:** JDK 17, Android SDK 34, a physical device with USB host
support (the emulator has no USB-C Ethernet path). Toolchain: AGP 8.5, Kotlin
1.9.24, Compose (BOM 2024.06), min SDK 26.

To exercise M1: connect a USB-C Ethernet adapter to a live drop, launch the app,
and the **Wired Link** screen shows interface, speed/duplex/MTU/MAC, IP config,
and lets you bind app traffic to the interface.

> Note: some values (notably hardware MAC, and PHY speed/duplex on locked-down
> builds) are withheld from unrooted apps and will read "unavailable" — expected.

## Install on your phone

There's no prebuilt APK yet — you build it once, then it installs like any app.
Easiest path is Android Studio; a command-line path is included too. For a
condensed version of the steps below, see **[install.md](install.md)**.

### Option A — Android Studio (recommended)

1. Install **Android Studio** (Koala or newer) on your computer.
2. **File → Open** and select this project's root folder. Let Gradle sync (it
   downloads the SDK/toolchain and generates the Gradle wrapper automatically).
3. On the phone, enable **Developer options** (Settings → About phone → tap
   **Build number** 7 times) and turn on **USB debugging** (Settings → System →
   Developer options).
4. Plug the phone into the computer by USB; approve the "Allow USB debugging"
   prompt on the phone.
5. Pick your phone in the device dropdown and press **Run ▶**. Android Studio
   builds, installs, and launches the app.

### Option B — command line (adb)

Requires JDK 17 + Android SDK (with `adb` on your PATH), and USB debugging on
(steps 3–4 above).

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If `./gradlew` is missing, run `gradle wrapper` once (or open the project in
Android Studio, which creates it).

### Option C — sideload the APK directly

If someone hands you the built `app-debug.apk` (e.g. from Option B), copy it to
the phone and open it. You'll be asked to allow **"Install unknown apps"** for
your file manager/browser — approve it, then tap **Install**.

### Using it once installed

1. Plug the **USB-C Ethernet adapter** into the phone (a USB-C OTG-capable port).
2. Connect a **live Ethernet cable** to the adapter.
3. Open **Network Analyzer**. The **Link** tab should show the interface and its
   speed within a second or two — that also confirms your adapter's chipset is
   supported. If nothing appears, the adapter isn't being recognized.
4. Explore the tabs: **Statistics**, **Hosts**, **Ports**, **Speed**, **WiFi**,
   **Cable**. The **WiFi** tab will ask for a one-time permission.

**Notes**
- Needs a phone with **USB host / OTG** support (most modern Android phones).
- The app is **unrooted** — no root or special setup required.
- The **Speed** download test needs real internet through the drop; **Cable**
  (M7) needs a second phone + adapter and IPv4 on both ends (via a switch/router).

## Troubleshooting

### Build issues (first build in Android Studio)

- **Gradle sync fails / `gradlew: not found`.** The Gradle wrapper jar isn't
  committed (it's a binary). Open the project in Android Studio, which generates
  it on first sync, or run `gradle wrapper` once with a local Gradle install.
- **"Unsupported Java version" / Gradle needs JDK 17.** Set **Settings → Build,
  Execution, Deployment → Build Tools → Gradle → Gradle JDK** to a JDK 17. AGP
  8.5 requires it.
- **SDK 34 not installed.** Android Studio → **SDK Manager** → install **Android
  14 (API 34)** platform + build-tools, then re-sync.
- **A Compose call flags an experimental API** (e.g. `FlowRow`,
  `ExperimentalLayoutApi`). The affected file should carry the matching
  `@OptIn(...)`; if a new lint error appears after a library bump, add the opt-in
  it names to that composable.
- **Dependency/version resolution errors.** Versions are pinned in
  `app/build.gradle.kts` (AGP 8.5, Kotlin 1.9.24, Compose BOM 2024.06). If your
  installed AGP differs, let Android Studio's **AGP Upgrade Assistant** align
  them rather than editing by hand.

> This app hasn't been device-validated yet, so the first build may surface a
> small import or opt-in fix. That's expected — the structure is sound.

### The adapter doesn't show up (Link tab stays "No adapter")

- **Confirm USB host/OTG.** Not every port/phone supports it; try the phone's
  primary USB-C port and, if needed, an OTG-verified cable.
- **Chipset support.** Realtek (RTL8153/8156) and ASIX (AX88179) enumerate on
  Android; off-brand chips may not. If a known-good adapter works and another
  doesn't, the other's chipset is the problem.
- **Live cable + link LED.** Make sure the drop is live and the adapter's link
  light is on. No carrier = no Ethernet transport = "No adapter".
- **Re-seat / replug.** Android brings the interface up on attach; unplug and
  replug the adapter, then tap **Refresh**.

### Values read "unavailable"

- **MAC address** is withheld from unrooted apps on most Android 6+ builds — this
  is expected, not a bug.
- **Speed / duplex** come from `/sys` and are hidden on some locked-down builds;
  the interface still works, only the readout is missing.

### WiFi tab shows no networks

- **Turn on system Location** (Settings → Location). This is the most common
  cause: **most phones gate WiFi scan results behind the Location master switch
  even when the app permission is granted** — and even on Android 13+, despite the
  app declaring `NEARBY_WIFI_DEVICES` with `neverForLocation`. The app now detects
  this and says so in the empty-state message.
- **Grant the permission** when prompted. On **Android 13+ the prompt says
  "Nearby devices," not "Location"** — that's the modern WiFi-scan permission, so
  don't wait for a location prompt that won't come. Below 13 it asks for Location.
- **Never asked / no prompt appears?** Android suppresses a permission prompt once
  it's been dismissed or denied ("don't ask again"), so the button silently does
  nothing. Use the **Open app settings** button (or Settings → Apps → Network
  Analyzer → Permissions) and enable **Nearby devices** / **Location** by hand. The
  app re-checks on resume, so it updates the moment you come back.
- **Scan throttling** (Android 9+) rate-limits scans to a few per couple of
  minutes. The app requests a scan, shows a "Scanning…" spinner, and waits for the
  result before reporting empty; if a scan is throttled it tells you to retry in
  ~10 s.

### Speed tab: download test fails

- The download needs **real internet reachability** through the wired drop. On an
  isolated/lab LAN it will fail with a clear message — latency and DNS (to the
  gateway/local resolver) still work.
- If your network blocks the default endpoint, edit the **Download URL** field to
  any large file reachable on your network.

### Cable tab (M7): client can't connect

- **Both phones need an IPv4 on the wired link.** A direct phone-to-phone cable
  has no DHCP, so connect both through a **switch/router** that hands out
  addresses; the server shows its IP once it has one.
- **Enter the exact IP** the server tab displays, and keep both phones on the
  **same wired segment**.
- **Firewall/isolation.** Some managed switches isolate ports (AP/client
  isolation) — use a plain unmanaged switch for the test.

### Common runtime errors (crashes & exceptions)

Read the stack trace in **Logcat** (Android Studio → **View → Tool Windows →
Logcat**, filter by the app package). The usual ones:

| Symptom / exception | Cause | Fix |
|---|---|---|
| **`SecurityException` on the WiFi tab** (scan results / getScanResults) | Scan permission not granted, or running on a build that still requires location | Grant the permission when prompted; on Android ≤12 also turn on system **Location services**. |
| **Download fails with `CleartextNotPermittedException`** | You changed the Download URL to an `http://` link — Android blocks cleartext by default (API 28+) | Use an `https://` URL, or add a `network_security_config` allowing cleartext for your test host. |
| **`registerReceiver … RECEIVER_EXPORTED/NOT_EXPORTED required`** (Android 14) | A broadcast receiver registered without an export flag | Already handled via `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`; keep that flag if you touch `WifiViewModel`. |
| **`NetworkOnMainThreadException`** | Network I/O ran on the UI thread | All built-in I/O is on `Dispatchers.IO`; if you add calls, keep them off the main thread. |
| **`SocketTimeoutException` / "Connection refused"** on Speed or Cable | Target unreachable or no server listening | Expected and shown as a message, not a crash — check reachability and that the server is started. |
| **Counters/speed show nothing, no crash** | sysfs layout differs on some OEM builds | Reads fail soft to "unavailable"; the feature still works where the files exist. |
| **App keeps sampling after you leave it** | The Statistics poller runs while the app is in memory | Expected for now (lifecycle-gating is a later refinement); close the app to stop it. |
| **Link tab doesn't refresh after unplug/replug** | The network-change callback missed an event | Tap **Refresh** to force a re-read. |

If a crash isn't listed here, grab the Logcat stack trace — the top
`Caused by:` line and the first frame in this app's package point to the cause.

## Open decisions

- [x] Root optional-enhanced mode — **out for v1** (unrooted-only).
- [ ] Bundle native binaries (nmap/iperf3) vs. pure Kotlin — APK size vs. power.
- [ ] Minimum Android API level.
- [ ] OUI database: bundle vs. fetch/update. _(M3 ships a small high-confidence
      starter set in `OuiLookup`; replace with the full IEEE registry.)_
