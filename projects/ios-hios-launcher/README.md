# Liquid Hybrid Launcher

An independent Android launcher for Android 11+, combining iOS-inspired visuals with Android/HiOS conventions.

## Build

From this directory run:

```bash
gradle --no-daemon :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Included in the first foundation

- HOME / DEFAULT launcher registration
- App discovery and launch
- Searchable app library
- Adaptive 3/4/5-column grid
- Initial dark appearance toggle
- Separate GitHub Actions build and APK artifact

This is an early foundation. Persistent workspace editing, actual app icon loading, drag-and-drop, widget hosting, wallpaper-aware glass and physical-device performance validation are not complete yet.
