<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="GT Monitor icon"/>
</p>

<h1 align="center">GT Monitor</h1>

<p align="center">
  Android foreground service that monitors cellular network changes in real-time,<br/>
  with per-device provider architecture for broad OEM compatibility.
</p>

---

## Features

- **Foreground service** — runs persistently with a rich status notification (signal, cell count, events)
- **Live cell info** — operator, network type (2G / 3G / 4G / 5G), signal dBm & level, cell ID, TAC, PCI, EARFCN, MCC / MNC, bandwidth
- **Visible-cell list** — all detected cells with registration status, type, ID and signal
- **Tower change detection** — event logged when the registered cell tower changes
- **Service state monitoring** — tracks in-service / out-of-service / emergency-only / power-off transitions
- **Boot autostart** — service resumes automatically after device reboot
- **Detailed dashboard** — tap the notification to view all connection details; pull-to-refresh
- **System log viewer** — built-in live-tail log page (file-backed, max 5 000 lines) for on-device diagnostics
- **Device-specific providers** — pluggable provider architecture selects the best telephony strategy per OEM:
  | Provider | Devices | Strategy |
  |----------|---------|----------|
  | `DefaultCellInfoProvider` | Pixel, AOSP, most OEMs | `requestCellInfoUpdate` / `allCellInfo` |
  | `SamsungCellInfoProvider` | Samsung (One UI) | `SignalStrength` fallback + cached `onCellInfoChanged` data when standard APIs return empty |

## Architecture

```
MainActivity ─────────┐
LogActivity           │  callbacks
                      ▼
GTService ──► CellInfoProvider (interface)
  │               ▲
  │      ┌────────┴────────┐
  │      │                 │
  │  DefaultCell…    SamsungCell…
  │
  ├── GTListener  (PhoneStateListener)
  ├── GTLog       (file + live-tail logger)
  └── BootReceiver
```

`DeviceProviderFactory` inspects `Build.MANUFACTURER` at service start and instantiates
the correct provider. Adding a new OEM module is two steps:

1. Create a class extending `DefaultCellInfoProvider` (or implementing `CellInfoProvider`)
2. Add a matching rule in `DeviceProviderFactory.create()`

## Build

All commands delegate to bash scripts in `scripts/`:

```bash
npm run build           # full Gradle build              (scripts/build.sh)
npm run assemble        # debug APK only                 (scripts/assemble.sh)
npm run assemble-prod   # release APK (signed)           (scripts/assemble-prod.sh)
npm run bundle          # debug AAB                      (scripts/bundle.sh)
npm run bundle-prod     # release AAB (signed)           (scripts/bundle-prod.sh)
npm run android         # install debug + launch         (scripts/android.sh)
npm run android-prod    # install release + launch       (scripts/android-prod.sh)
npm run dev             # clean → build → install debug  (scripts/dev.sh)
npm run release         # clean → build → all artifacts  (scripts/release.sh)
npm run publish         # tag, push, build & GitHub release (scripts/publish.sh)
npm run clean           # clean build artifacts          (scripts/clean.sh)
npm run lint            # run lint checks                (scripts/lint.sh)
npm run test            # run tests                      (scripts/test.sh)
```

Requires Android SDK with **minSdk 29** (Android 10+) · targetSdk 34 · compileSdk 34.

## Signing

Release builds are signed using credentials from `keystore.properties` (git-ignored).
The keystore file (`*.keystore`) is also git-ignored.

**First-time setup:**

1. Generate a keystore in the project root:

   ```bash
   keytool -genkeypair -v \
     -keystore release.keystore \
     -alias GTMonitor \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -storepass <password> -keypass <password> \
     -dname "CN=GTMonitor, OU=Dev, O=GTMonitor, L=Unknown, ST=Unknown, C=US"
   ```

2. Create `keystore.properties` in the project root:

   ```properties
   storeFile=release.keystore
   storePassword=<password>
   keyAlias=GTMonitor
   keyPassword=<password>
   ```

> **Note:** PKCS12 keystores require `storePassword` and `keyPassword` to be identical.

Both files are listed in `.gitignore` and must **never** be committed.

## Permissions

| Permission                            | Purpose                                        |
| ------------------------------------- | ---------------------------------------------- |
| `READ_PHONE_STATE`                    | Access cell / network info                     |
| `ACCESS_FINE_LOCATION`                | Cell tower location data                       |
| `ACCESS_COARSE_LOCATION`              | Fallback location for cell queries             |
| `FOREGROUND_SERVICE`                  | Run persistent background service              |
| `FOREGROUND_SERVICE_LOCATION`         | Location-type foreground service (Android 14+) |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Connected-device foreground service type       |
| `FOREGROUND_SERVICE_DATA_SYNC`        | Data-sync foreground service type              |
| `CHANGE_NETWORK_STATE`                | Network state change events                    |
| `POST_NOTIFICATIONS`                  | Show status notification (Android 13+)         |
| `RECEIVE_BOOT_COMPLETED`              | Auto-start on device boot                      |

## Project structure

```
app/src/main/java/com/example/gtmonitor/
├── MainActivity.kt          # Dashboard UI, permission handling, service lifecycle
├── GTService.kt             # Foreground service — delegates to CellInfoProvider
├── GTListener.kt            # PhoneStateListener (cell info & service state)
├── GTLog.kt                 # Singleton logger (Logcat + file + live listeners)
├── LogActivity.kt           # Live-tail log viewer
├── ConnectionInfo.kt        # Data class for all UI fields
├── BootReceiver.kt          # BOOT_COMPLETED → start service
└── provider/
    ├── CellInfoProvider.kt       # Interface + CellDataSnapshot data class
    ├── DefaultCellInfoProvider.kt # Standard Android API implementation
    ├── SamsungCellInfoProvider.kt  # Samsung SignalStrength fallback
    └── DeviceProviderFactory.kt   # OEM-based provider selection
```

## Author

RVÐ

## License

GGPL
