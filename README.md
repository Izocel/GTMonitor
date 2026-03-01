<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="GT Monitor icon"/>
</p>

<h1 align="center">GT Monitor</h1>

<p align="center">
  Android background service that monitors cellular network changes in real-time.
</p>

---

## Features

- **Foreground service** — runs persistently with a status notification
- **Live cell info** — operator, network type (2G/3G/4G/5G), signal strength, cell ID, TAC, PCI, EARFCN, MCC/MNC, bandwidth
- **Tower change alerts** — audible notification when the registered cell tower changes
- **Service state monitoring** — alerts on loss of service or emergency-only mode
- **Boot autostart** — service resumes automatically after device reboot
- **Detailed dashboard** — tap the notification to view all connection details

## Build

```bash
npm run build           # full build with lint
npm run assemble-debug  # debug APK only
npm run android         # install debug + launch on device
npm run android-prod    # install release + launch on device
npm run clean           # clean build artifacts
npm run lint            # run lint checks
```

Requires Android SDK with `minSdk 28` (Android 9+).

## Permissions

| Permission               | Purpose                                |
| ------------------------ | -------------------------------------- |
| `READ_PHONE_STATE`       | Access cell/network info               |
| `ACCESS_FINE_LOCATION`   | Cell tower location data               |
| `FOREGROUND_SERVICE`     | Run persistent background service      |
| `POST_NOTIFICATIONS`     | Show status notification (Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on device boot              |

## Author

RVÐ

## License

GGPL
