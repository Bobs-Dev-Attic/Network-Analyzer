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

- Cable length estimation
- Open / short / miswired pair detection

The app should surface these as "requires dedicated hardware — out of scope."

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

**M3 — Discovery & inventory**
Ping sweep + connect probes → host list with MAC, vendor (OUI), hostname.

**M4 — Port scan**
Per-host TCP-connect scan + service identification.

**M5 — Speed & quality**
Latency/jitter/loss, DNS timing, HTTP throughput; iperf3 as stretch.

**M6 — WiFi analysis**
AP scan, channel congestion view, current-link quality (phone radio).

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
- [ ] OUI database: bundle vs. fetch/update.
