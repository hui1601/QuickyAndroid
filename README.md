# QuickyAndroid

Unofficial Android app for QCY earbuds, built with Kotlin + Jetpack Compose (Material 3 Expressive). Communicates directly with the earbuds over BLE GATT using protocols reverse-engineered from the official QCY app.

## What it does

- **Scan & connect** — BLE scan with advertisement parsing (battery levels/charging state per bud and case, product identification), then GATT connection. A foreground service (`BleService`, `connectedDevice` type) keeps the link alive, with auto-reconnect and battery polling.
- **Vendor protocol routing** — earbuds are routed by vendor ID to the right GATT client, mirroring the original app's chip-family detection (`VendorRouter`):
  - `STANDARD` — QCY service `0xA001` (QCC, WQ, Airoha, RTK, BES families)
  - `JL` / `JL_NEW` — JieLi chips via RCSP tunnel
  - `ZR` — Zhurui family
  - `WUQI` — runtime fallback for devices exposing only service `0x7033`
- **Device dashboard** — feature availability is derived from a bundled per-product catalog (`app/src/main/assets/android_products.json`, ~199 products with images):
  - Noise Control (ANC modes)
  - Equalizer (presets + custom multi-band, e.g. 10-band)
  - Game Mode, Spatial Audio, LDAC, Adaptive EQ, Env Adaptation, Focus/Sleep Mode
  - Per-bud volume (L/R), LED indicator, LED effects
  - Wearing detection, Ear Tip Fit test
  - Remote camera shutter
  - Key Controls — remap touch gestures per event
  - Find My Earphone — make a lost bud ring
  - Device Settings — product-specific toggles
- **Home-screen widget** — persists and renders the last known connection + battery snapshot (`DeviceWidgetProvider`).
- **Theming** — dark mode, dynamic color (Material You), M3 motion, Lottie animations, shared-element transitions between scan and dashboard.

## Requirements

- Android 8.0+ (minSdk 26), BLE hardware
- Bluetooth and notification runtime permissions (requested on first launch)

## Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # protocol/parser/router unit tests
./gradlew connectedDebugAndroidTest  # Compose instrumentation tests
```

## Layout

| Path | Contents |
|---|---|
| `app/src/main/java/com/hui1601/quickyandroid/ble/` | Scanner, foreground service, vendor GATT clients, protocol codecs |
| `app/src/main/java/com/hui1601/quickyandroid/ui/` | Compose screens, components, theme, view models |
| `app/src/main/java/com/hui1601/quickyandroid/data/` | Advertisement parser, product repository/models |
| `app/src/main/assets/` | Product catalog JSON + product images + Lottie animations |

## Disclaimer

**This is AI slop code.** It was generated largely by AI coding agents with minimal human review, and it talks to consumer hardware over undocumented, reverse-engineered protocols.

The code quality is a total mess. I don't care — it's AI slop.

The author accepts **no liability** for anything this app does or fails to do — bricked earbuds, drained batteries, missed alarms, or anything else — and claims **no rights** regarding its functionality.

Active maintenance happens **only while the author is actively using their QCY HT18 earbuds**. Once those earbuds are gone, this project is effectively abandoned: issues and pull requests may sit unaddressed indefinitely.
