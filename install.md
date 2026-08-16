# Install — Quick Start

Get **Network Analyzer** onto your Android phone in about 15–20 minutes. No root.
For the full guide and troubleshooting, see the [README](README.md).

---

## What you need

- An Android phone with **USB host / OTG** (most modern phones).
- A **USB-C to Ethernet adapter** (Realtek RTL8153-class recommended, e.g. Anker
  A83130A1) and a **live Ethernet cable**.
- A computer with **Android Studio** (it brings the SDK + JDK).

---

## Build & install (Android Studio — easiest)

1. **Get the code.**
   ```
   git clone https://github.com/Bobs-Dev-Attic/Network-Analyzer.git
   ```
2. **Open it.** Android Studio → **File → Open** → pick the `Network-Analyzer`
   folder. Let **Gradle sync** finish (first time downloads tools and creates the
   Gradle wrapper — a few minutes). Accept any prompt to install **API 34** or
   set **JDK 17**.
3. **Make the phone a developer device** (one-time):
   - Settings → **About phone** → tap **Build number** 7×.
   - Settings → System → **Developer options** → enable **USB debugging**.
4. **Plug the phone into the computer** and tap **Allow** on the USB-debugging
   prompt.
5. **Select your phone** in the device dropdown and press **Run ▶**. It builds,
   installs, and launches automatically.

> First build hiccup? That's expected for not-yet-validated code — copy the red
> error (and any Logcat output) and it's a quick fix. See README →
> **Troubleshooting**.

---

## Build & install (command line)

Needs JDK 17 + Android SDK with `adb` on PATH, and USB debugging on (step 3).

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If `./gradlew` is missing, run `gradle wrapper` once (or open in Android Studio).

---

## Sideload (no computer at run time)

Build the APK once (command line above), copy
`app/build/outputs/apk/debug/app-debug.apk` to the phone, tap it, and allow
**"Install unknown apps"** for your file manager, then **Install**.

---

## First run

1. Plug the **USB-C Ethernet adapter** into the phone; connect a **live cable**.
2. Open **Network Analyzer**.
3. The **Link** tab shows the interface + speed within a second or two — this
   also confirms your adapter's chipset is supported. Nothing there = the adapter
   isn't recognized (try a Realtek/ASIX one).
4. Explore: **Statistics · Hosts · Ports · Speed · WiFi · Cable**. The **WiFi**
   tab requests a one-time permission.

Notes: **Speed**'s download test needs real internet through the drop; **Cable**
needs a second phone + adapter and IPv4 on both ends (via a switch/router).
