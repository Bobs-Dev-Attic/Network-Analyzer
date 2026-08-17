# Semantic status color and live-signal graph axes

Date: 2026-08-17
Status: approved

## Problem

The app reads as monotone. Two independent causes:

1. **No palette of its own.** `Theme.kt` calls `darkColorScheme()` / `lightColorScheme()`
   with no arguments, yielding Material 3's stock baseline purple. On Android 12+ that
   is overridden entirely by wallpaper-derived dynamic color.
2. **No semantic color on data that has meaning.** Of ~34 color references across the
   eight screens, 26 are `onSurfaceVariant` (grey secondary text), 4 are `primary`,
   3 are grey dividers, 1 is `error`. Roughly three quarters of the app's color usage
   is grey text — including state that is inherently good/bad: cable verdicts, signal
   strength, port states, packet loss.

The second cause is the one worth fixing for a diagnostic tool: colored state is
scannable, and this app has a lot of state.

Separately, the live signal graph draws a bare polyline with no scale, so a reading
cannot be quantified from the chart.

## Goals

- Encode existing semantic state as color, consistently across screens.
- Give the live RSSI graph a labeled grid with dBm and time axes.
- Keep every screen fully readable with color removed.

## Non-goals

- No new brand palette. Chrome (tabs, buttons, top bar) keeps dynamic color.
- No changes to `network/` logic or to any ViewModel, except the one timing fix below.
- No new data collection, no new screens.

## Architecture

### Semantic colors come from the theme, not from call sites

A `StatusColors` data class holds the semantic roles. Light and dark instances are
defined in a new `ui/theme/Color.kt`. `NetworkAnalyzerTheme` provides the correct one
through a `LocalStatusColors` CompositionLocal, and screens read it via a
`MaterialTheme.statusColors` extension accessor.

This is a correctness requirement, not a style preference. The app has an explicit
`ThemeMode` (SYSTEM/LIGHT/DARK) from the Settings tab, resolved to a `darkTheme`
boolean at `Theme.kt:22-26`. A semantic color that instead branched on
`isSystemInDarkTheme()` at the call site would return the *system* value, so a user
forcing Light while the OS is dark would get dark-variant status colors on a light
surface. The semantic layer must inherit the theme's already-resolved value.

Rejected alternative: reusing M3 `colorScheme` slots (`error`, `tertiary`). Only two
or three usable slots exist for the four tinted state roles plus three band hues, and
under dynamic color they are wallpaper-derived — a "green PASS" could render pink.

### Dynamic color stays; semantic colors are fixed

`dynamicLightColorScheme` / `dynamicDarkColorScheme` remain for chrome, so the app
still feels native. Semantic colors are fixed constants: PASS is green on every device
regardless of wallpaper.

Accepted trade-off: on an unusual wallpaper the dynamic primary may sit oddly beside
fixed green/amber/red. Accepted because the goal is semantic color, not a new identity.

## Palette

Values chosen for >= 4.5:1 text contrast against their own container.

| Role     | Light fg / container  | Dark fg / container   |
|----------|-----------------------|-----------------------|
| good     | `#1B6E3C` / `#DCF0E3` | `#7BD99F` / `#12341F` |
| warn     | `#8A5300` / `#FDEBD2` | `#F5C169` / `#3A2A0C` |
| bad      | `#B3261E` / `#F9DEDC` | `#F2B8B5` / `#4A1F1C` |
| info     | `#3B4FA8` / `#DEE1FF` | `#9FB0FF` / `#1B2559` |
| 2.4 GHz  | `#00696E` / `#B8ECEF` | `#4FD8E0` / `#063B3E` |
| 5 GHz    | `#3B4FA8` / `#DEE1FF` | `#9FB0FF` / `#1B2559` |
| 6 GHz    | `#6B3FA0` / `#EDDCFF` | `#CBA8F5` / `#2E1B45` |

`neutral` is not a new color; it maps to `MaterialTheme.colorScheme.onSurfaceVariant`.

`info` and `5 GHz` intentionally share the same values. They are distinct roles that
never co-occur — `info` appears only on the port-scan screen, `5 GHz` only on the WiFi
screen — so they stay separate named roles (either may be retuned independently) while
reusing one hue today.

## State to color mapping

| Source                          | Mapping                                                        |
|---------------------------------|----------------------------------------------------------------|
| `Verdict`                       | PASS→good, MARGINAL→warn, FAULT→bad, UNKNOWN→neutral           |
| `WifiAp.signalBars` (0..4)      | 0-1→bad, 2→warn, 3-4→good                                      |
| `WifiBand`                      | 2.4/5/6 GHz→band hues, UNKNOWN→neutral                         |
| `PortState`                     | OPEN→info, FILTERED→warn, CLOSED→neutral                       |
| `LatencyResult.lossPct`         | 0→good, <=2→warn, >2→bad                                       |
| `LatencyResult.avgMs`           | <30→good, <100→warn, >=100→bad                                 |
| `DnsResult.ok`                  | true→good, false→bad                                           |
| Statistics rx errors / drops    | 0→good, >0→warn                                                |

`PortState.OPEN` maps to **info, not good**, deliberately. On a port scan an open port
is noteworthy rather than a success; green would read as reassurance when it is often
the opposite.

Thresholds live in one `statusFor(...)` helper file so they are not scattered across
screens.

## Components

Three shared composables in a new `ui/theme/StatusUi.kt`, so each screen drops to a
one-line call instead of repeating chip construction:

- `StatusChip(label, glyph, colors)` — tinted fill, matching border, glyph + text.
- `SignalBars(bars)` — existing 0-4 height ramp, drawn in the strength color.
- `BandChip(band)` — colored chip carrying the band label.

## Accessibility

**Color never carries meaning alone.** PASS-green versus FAULT-red is exactly the pair
that red-green color blindness collapses (~8% of men). Every status chip renders glyph
+ label + color: `✓ PASS`, `! MARGINAL`, `✕ FAULT`. Signal bars keep their height ramp
and the dBm number stays visible.

Acceptance test: with color stripped out, every screen remains fully readable.

## Live signal graph

Replaces `RssiSparkline` in `WifiScreen.kt:338-360`, which currently draws one baseline
and a polyline in an 80dp box.

**Layout.** Height 80dp → 140dp. Left gutter 36dp for dBm labels, bottom gutter 18dp
for time labels, plot area is the remainder. Labels drawn with `rememberTextMeasurer()`
+ `drawText` (available in Compose UI 1.6.8 via BOM 2024.06.00), styled `labelSmall` at
`onSurfaceVariant`.

**Y axis — fixed range.** Keeps the existing -100..-30 dBm range; it is not auto-scaled,
so the frame stays stable as the signal moves. Solid labeled gridlines every 20 dB at
-100 / -80 / -60 / -40.

Behind the gridlines, three semantic zone bands at ~8% alpha, with boundaries at -66 and
-77 — the same thresholds as `signalBars` and the mapping table above, so chart, bars,
and chip always agree:

| Zone | dBm         | Band       |
|------|-------------|------------|
| good | >= -66      | green, 8%  |
| warn | -77 .. -66  | amber, 8%  |
| bad  | < -77       | red, 8%    |

**X axis — time.** "Now" at the right edge, age increasing leftward. Span is
`(n-1) * intervalMs`, with `RSSI_HISTORY = 60` samples. Tick step is chosen from
{5s, 10s, 15s, 30s, 60s} as the value nearest `span/4`, which yields round labels for
all four interval settings:

| Interval | Max span | Step chosen | Labels                |
|----------|----------|-------------|-----------------------|
| 0.5s     | ~30s     | 10s         | 0s / 10s / 20s        |
| 1s       | 59s      | 15s         | 0s / 15s / 30s / 45s  |
| 2s       | ~2min    | 30s         | 0s / 30s / 60s / 90s  |
| 5s       | ~5min    | 60s         | 0s / 60s ... / 240s   |

Vertical gridlines at each tick.

**Line.** Single color at 2dp drawn over the zones, plus a filled dot on the latest
sample. The line is deliberately *not* colored by strength: a multi-hue line over
tinted zones turns to mud, and the zones already encode strength positionally.

## Timing fix

`WifiViewModel.kt:147` changes `intervalMs` without clearing `rssiHistory`:

```kotlin
_state.update { it.copy(intervalMs = ms) }
```

After switching 5s → 0.5s the buffer holds samples taken at two different rates, while
the x-axis maps position to time using only the current `intervalMs`. Today this is
invisible because there is no time axis; once one exists the labels misreport older
samples — a point drawn at "-30s" could be five minutes old.

Fix: clear `rssiHistory` on interval change, matching what `onToggleLive` already does
at `WifiViewModel.kt:140`. The trace is lost when changing rate, but every plotted point
is truthfully placed.

Rejected alternative: storing a timestamp per sample and plotting against real elapsed
time. More correct across rate changes, but changes `WifiState.rssiHistory` from
`List<Int>` to a list of pairs and touches the sampling loop. The graph is a live meter,
not a record to scroll back through, so the cheaper fix fits.

This is the only ViewModel change in scope.

## Files

New:
- `ui/theme/Color.kt` — `StatusColors`, light/dark instances, `LocalStatusColors`
- `ui/theme/StatusUi.kt` — `StatusChip`, `SignalBars`, `BandChip`
- `ui/theme/StatusMapping.kt` — `statusFor(...)` threshold helpers

Modified:
- `ui/theme/Theme.kt` — provide `LocalStatusColors` from resolved `darkTheme`
- `ui/WifiScreen.kt` — band chips, signal bars, new grid chart
- `ui/QualificationScreen.kt` — verdict chip
- `ui/PortScanScreen.kt` — port state chips
- `ui/SpeedQualityScreen.kt` — latency / loss / DNS coloring
- `ui/StatisticsScreen.kt` — error and drop counters
- `ui/InterfaceScreen.kt` — link up/down
- `ui/WifiViewModel.kt` — clear history on interval change

## Verification

- `./gradlew assembleDebug` compiles.
- Manual: toggle Settings theme to Light and Dark with the OS set to the opposite, and
  confirm status colors follow the app setting rather than the system.
- Manual: switch live-signal interval and confirm the trace clears and axis labels stay
  consistent.
- Manual: confirm every screen is readable with color disregarded (glyphs and labels
  present on all status chips).
