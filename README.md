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

**M1 — Interface & link info** ✅ _in progress_
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

**M5 — Speed & quality**
Latency/jitter/loss, DNS timing, HTTP throughput; iperf3 as stretch.

**M6 — WiFi analysis**
AP scan, channel congestion view, current-link quality (phone radio).

**M7 — Two-phone cable qualification** _(stretch; builds on M2 + M5)_
A cooperative pass/fail quality test for a cable run using two phones + two
adapters — one end runs an iperf-style server, the other the client, both
watching link rate and error/drop/retrain counters.

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

## Open decisions

- [x] Root optional-enhanced mode — **out for v1** (unrooted-only).
- [ ] Bundle native binaries (nmap/iperf3) vs. pure Kotlin — APK size vs. power.
- [ ] Minimum Android API level.
- [ ] OUI database: bundle vs. fetch/update. _(M3 ships a small high-confidence
      starter set in `OuiLookup`; replace with the full IEEE registry.)_
